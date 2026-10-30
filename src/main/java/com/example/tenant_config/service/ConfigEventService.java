
package com.example.tenant_config.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.tenant_config.entity.ConfigChangeEntity;
import com.example.tenant_config.repository.ConfigChangeRepository;

@Service
public class ConfigEventService {

    private static class ClientConnection {
        private final SseEmitter emitter;
        private final String tenantId;
        private final boolean admin;

        ClientConnection(
                SseEmitter emitter,
                String tenantId,
                boolean admin) {
            this.emitter = emitter;
            this.tenantId = tenantId;
            this.admin = admin;
        }
    }

    private final Set<ClientConnection> connections =
            ConcurrentHashMap.newKeySet();

    private final ConfigChangeRepository changeRepository;

    public ConfigEventService(
            ConfigChangeRepository changeRepository) {
        this.changeRepository = changeRepository;
    }

    public SseEmitter subscribe(
            Long lastEventId,
            String tenantId,
            boolean admin) {

        SseEmitter emitter = new SseEmitter(0L);

        ClientConnection connection =
                new ClientConnection(emitter, tenantId, admin);

        connections.add(connection);

        Runnable removeConnection =
                () -> connections.remove(connection);

        emitter.onCompletion(removeConnection);
        emitter.onTimeout(removeConnection);
        emitter.onError(error -> removeConnection.run());

        long reconnectDelay =
                ThreadLocalRandom.current().nextLong(1000, 5001);

        try {
            emitter.send(
                SseEmitter.event()
                    .reconnectTime(reconnectDelay)
                    .comment("Connection established")
            );
        } catch (IOException e) {
            removeConnection.run();
            return emitter;
        }

        // Recover only events visible to this client
        if (lastEventId != null && lastEventId >= 0) {

            List<ConfigChangeEntity> missedChanges =
                    changeRepository.findByIdGreaterThanOrderByIdAsc(
                            lastEventId
                    );

            for (ConfigChangeEntity change : missedChanges) {

                boolean visible =
                        admin
                        || change.getTenantId() == null
                        || change.getTenantId().equals(tenantId);

                if (!visible) {
                    continue;
                }

                try {
                    emitter.send(
                        SseEmitter.event()
                            .id(String.valueOf(change.getId()))
                            .name("config-update")
                            .data(
                                "Missed configuration change: "
                                + change.getConfigKey()
                                + ", Change ID: " + change.getId()
                            )
                    );
                } catch (IOException e) {
                    removeConnection.run();
                    break;
                }
            }
        }

        return emitter;
    }

    public void publish(
            String eventId,
            String message,
            String changedTenantId,
            boolean globalChange) {

        for (ClientConnection connection : connections) {

            boolean visible =
                    connection.admin
                    || globalChange
                    || (changedTenantId != null
                        && changedTenantId.equals(connection.tenantId));

            if (!visible) {
                continue;
            }

            try {
                connection.emitter.send(
                    SseEmitter.event()
                        .id(eventId)
                        .name("config-update")
                        .data(message)
                );
            } catch (IOException e) {
                connections.remove(connection);
            }
        }
    }
}
