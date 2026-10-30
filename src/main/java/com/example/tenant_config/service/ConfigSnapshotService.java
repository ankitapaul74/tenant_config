
package com.example.tenant_config.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.tenant_config.model.ConfigSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class ConfigSnapshotService {

    private final ObjectMapper objectMapper;

    private final Path snapshotPath =
            Path.of("snapshots", "config-snapshot.json");

    private final Path temporaryPath =
            Path.of("snapshots", "config-snapshot.tmp");

    public ConfigSnapshotService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // Save the current configuration snapshot safely
    public synchronized void saveSnapshot(ConfigSnapshot snapshot) {

        try {
            // Create the snapshots directory if it does not exist
            Files.createDirectories(snapshotPath.getParent());

            // First write the complete snapshot to a temporary file
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryPath.toFile(), snapshot);

            // Replace the old snapshot only after the temporary
            // file has been written successfully
            try {
                Files.move(
                        temporaryPath,
                        snapshotPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (java.nio.file.AtomicMoveNotSupportedException e) {

                // Fallback when the file system does not support
                // atomic move
                Files.move(
                        temporaryPath,
                        snapshotPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            System.out.println(
                    "Configuration snapshot saved successfully."
            );

        } catch (IOException e) {

            System.err.println(
                    "Failed to save configuration snapshot: "
                            + e.getMessage()
            );
        }
    }

    // Load the previously saved snapshot from disk
    public Optional<ConfigSnapshot> loadSnapshot() {

        if (!Files.exists(snapshotPath)) {
            System.out.println("No previous snapshot found.");
            return Optional.empty();
        }

        try {
            ConfigSnapshot snapshot =
                    objectMapper.readValue(
                            snapshotPath.toFile(),
                            ConfigSnapshot.class
                    );

            System.out.println(
                    "Configuration snapshot loaded successfully."
            );

            return Optional.of(snapshot);

        } catch (IOException e) {

            System.err.println(
                    "Failed to load configuration snapshot: "
                            + e.getMessage()
            );

            return Optional.empty();
        }
    }
}
