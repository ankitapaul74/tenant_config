
package com.example.tenant_config.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TenantConfigClient implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(TenantConfigClient.class.getName());

    private static final long POLL_INTERVAL_SECONDS = 5;

    private final String baseUrl;
    private final String tenantId;
    private final String officeId;
    private final String shiftId;
    private final String authorization;
    private final Path snapshotPath;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Set<String> configKeys =
            ConcurrentHashMap.newKeySet();

    private final AtomicReference<Map<String, Object>> localSnapshot =
            new AtomicReference<>(Collections.emptyMap());

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final java.util.concurrent.ExecutorService sseExecutor =
            Executors.newSingleThreadExecutor();

    private final Object refreshLock = new Object();

    private volatile long currentVersion = 0L;
    private volatile String lastEventId;
    private volatile boolean running;
    private volatile InputStream activeSseStream;

    public TenantConfigClient(
            String baseUrl,
            String tenantId,
            String officeId,
            String shiftId,
            String username,
            String password,
            Path snapshotPath) {

        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
        this.snapshotPath = snapshotPath;

        String credentials = username + ":" + password;

        this.authorization = "Basic " +
                Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));

        this.objectMapper = new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        loadDiskSnapshot();
    }

    // Register configuration keys needed by the consuming service.
    public void registerConfigKey(String configKey) {
        configKeys.add(configKey);

        if (running) {
            refreshSnapshot(true);
        }
    }

    // Start periodic polling and SSE listening.
    public void start() {

        if (running) {
            return;
        }

        running = true;

        // Initial refresh, while retaining the disk snapshot on failure.
        refreshSnapshot(true);

        // Recover missed SSE notifications.
        scheduler.scheduleWithFixedDelay(
                () -> refreshSnapshot(false),
                POLL_INTERVAL_SECONDS,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        // Listen for push notifications.
        sseExecutor.submit(this::listenForEvents);

        LOGGER.info("Tenant configuration client started.");
    }

    // Microsecond-scale local read: no network request.
    public Object get(String configKey) {

        Map<String, Object> snapshot = localSnapshot.get();

        if (!snapshot.containsKey(configKey)) {
            throw new IllegalArgumentException(
                    "Configuration not registered or unavailable: "
                            + configKey);
        }

        return snapshot.get(configKey);
    }

    // Typed local read.
    public <T> T get(String configKey, Class<T> type) {

        return objectMapper.convertValue(get(configKey), type);
    }

    public long getCurrentVersion() {
        return currentVersion;
    }

    public Map<String, Object> getLocalSnapshot() {
        return localSnapshot.get();
    }

    // Poll version endpoint and atomically refresh all registered keys.
    private void refreshSnapshot(boolean force) {

        synchronized (refreshLock) {

            if (!running && !force) {
                return;
            }

            try {

                long serverVersion = fetchServerVersion();

                if (serverVersion < currentVersion) {
                    LOGGER.warning(
                            "Ignoring older server version: "
                                    + serverVersion);
                    return;
                }

                if (!force && serverVersion == currentVersion) {
                    return;
                }

                Map<String, Object> updatedValues = new HashMap<>();

                // Fetch every registered key before publishing anything.
                for (String key : configKeys) {
                    updatedValues.put(key, fetchConfiguration(key));
                }

                // Ensure the server did not change during the refresh.
                long confirmedVersion = fetchServerVersion();

                if (confirmedVersion != serverVersion) {
                    LOGGER.warning(
                            "Configuration changed during refresh. "
                                    + "Retrying during the next cycle.");
                    return;
                }

                if (confirmedVersion < currentVersion) {
                    return;
                }

                ClientSnapshot snapshot = new ClientSnapshot();
                snapshot.version = confirmedVersion;
                snapshot.lastEventId = lastEventId;
                snapshot.values = updatedValues;

                // Persist before publishing the new in-memory snapshot.
                saveDiskSnapshot(snapshot);

                localSnapshot.set(
                        Collections.unmodifiableMap(
                                new HashMap<>(updatedValues)));

                currentVersion = confirmedVersion;

                LOGGER.info(
                        "Configuration snapshot updated to version "
                                + currentVersion);

            } catch (Exception e) {

                LOGGER.log(
                        Level.WARNING,
                        "Configuration refresh failed. "
                                + "Continuing with last good snapshot.",
                        e);
            }
        }
    }

    private long fetchServerVersion()
            throws IOException, InterruptedException {

        String url = baseUrl + "/api/configs/version?since=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Version API returned HTTP "
                            + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());

        return json.get("currentVersion").asLong();
    }

    private Object fetchConfiguration(String configKey)
            throws IOException, InterruptedException {

        StringBuilder url = new StringBuilder(
                baseUrl + "/api/configs/resolve?configKey="
                        + encode(configKey)
                        + "&tenantId=" + encode(tenantId));

        if (officeId != null && !officeId.isBlank()) {
            url.append("&officeId=").append(encode(officeId));
        }

        if (shiftId != null && !shiftId.isBlank()) {
            url.append("&shiftId=").append(encode(shiftId));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Resolve API returned HTTP "
                            + response.statusCode()
                            + " for " + configKey);
        }

        return objectMapper.readValue(
                response.body(), Object.class);
    }

    // SSE listener with reconnect jitter and Last-Event-ID recovery.
    private void listenForEvents() {

        while (running) {

            try {

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(
                                baseUrl + "/api/configs/events"))
                        .header("Authorization", authorization)
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(30))
                        .GET();

                if (lastEventId != null) {
                    builder.header("Last-Event-ID", lastEventId);
                }

                HttpResponse<InputStream> response = httpClient.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException(
                            "SSE returned HTTP "
                                    + response.statusCode());
                }

                activeSseStream = response.body();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                activeSseStream,
                                StandardCharsets.UTF_8))) {

                    String line;
                    boolean eventReceived = false;

                    while (running && (line = reader.readLine()) != null) {

                        if (line.startsWith("id:")) {
                            lastEventId = line.substring(3).trim();
                        }

                        if (line.startsWith("data:")) {
                            eventReceived = true;
                        }

                        if (line.isEmpty() && eventReceived) {
                            refreshSnapshot(true);
                            eventReceived = false;
                        }
                    }
                } finally {
                    activeSseStream = null;
                }

            } catch (Exception e) {

                if (running) {
                    LOGGER.log(
                            Level.WARNING,
                            "SSE disconnected. Reconnecting.",
                            e);
                }
            }

            if (running) {
                try {
                    long delay = ThreadLocalRandom.current()
                            .nextLong(1000, 5001);

                    Thread.sleep(delay);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // Restore the last good client snapshot from disk.
    private void loadDiskSnapshot() {

        if (!Files.exists(snapshotPath)) {
            return;
        }

        try {

            ClientSnapshot snapshot = objectMapper.readValue(
                    snapshotPath.toFile(),
                    ClientSnapshot.class);

            if (snapshot.values != null) {

                localSnapshot.set(
                        Collections.unmodifiableMap(
                                new HashMap<>(snapshot.values)));

                currentVersion = snapshot.version;
                lastEventId = snapshot.lastEventId;

                LOGGER.info(
                        "Restored local client snapshot, version "
                                + currentVersion);
            }

        } catch (Exception e) {

            LOGGER.log(
                    Level.WARNING,
                    "Unable to restore client snapshot.",
                    e);
        }
    }

    // Save using a temporary file followed by atomic replacement.
    private void saveDiskSnapshot(ClientSnapshot snapshot)
            throws IOException {

        Path parent = snapshotPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryFile = snapshotPath.resolveSibling(
                snapshotPath.getFileName() + ".tmp");

        objectMapper.writeValue(
                temporaryFile.toFile(),
                snapshot);

        try {
            Files.move(
                    temporaryFile,
                    snapshotPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (AtomicMoveNotSupportedException e) {

            Files.move(
                    temporaryFile,
                    snapshotPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {

        running = false;

        scheduler.shutdownNow();
        sseExecutor.shutdownNow();

        InputStream stream = activeSseStream;

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Closing the stream during shutdown is best effort.
            }
        }

        LOGGER.info("Tenant configuration client stopped.");
    }

    // JSON structure persisted by the client library.
    public static class ClientSnapshot {

        public long version;
        public String lastEventId;
        public Map<String, Object> values;
    }
}
