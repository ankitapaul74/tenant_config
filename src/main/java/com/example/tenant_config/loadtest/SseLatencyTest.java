
package com.example.tenant_config.loadtest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SseLatencyTest {

    private static final int CLIENT_COUNT = 500;

    private static final String SSE_URL =
            "http://localhost:8080/api/configs/events";

    private static final String UPDATE_URL =
            "http://localhost:8080/api/configs/overrides";

    private static final String CONFIG_KEY = "companyRecoveryTest";

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        ExecutorService readerExecutor = Executors.newCachedThreadPool();

        AtomicInteger successfulConnections = new AtomicInteger();
        AtomicInteger failedConnections = new AtomicInteger();
        AtomicInteger receivedEvents = new AtomicInteger();

        AtomicLong triggerTime = new AtomicLong(0);

        ConcurrentLinkedQueue<Long> latencies =
                new ConcurrentLinkedQueue<>();

        List<InputStream> activeStreams =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        System.out.println("Starting SSE Latency Test...");
        System.out.println("Target clients: " + CLIENT_COUNT);

        // STEP 1: Connect all clients

        for (int i = 0; i < CLIENT_COUNT; i++) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SSE_URL))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            CompletableFuture<Void> future = client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            ).thenAccept(response -> {

                if (response.statusCode() == 200) {

                    successfulConnections.incrementAndGet();

                    InputStream stream = response.body();
                    activeStreams.add(stream);

                    readerExecutor.submit(() -> {

                        try (BufferedReader reader =
                                     new BufferedReader(
                                             new InputStreamReader(stream))) {

                            String line;

                            while ((line = reader.readLine()) != null) {

                                if (line.startsWith("data:")) {

                                    String eventData = line.substring(5).trim();

                                    if (eventData.contains(CONFIG_KEY)) {

                                        long start = triggerTime.get();

                                        if (start > 0) {

                                            long latency =
                                                    System.nanoTime() - start;

                                            latencies.add(latency / 1_000_000);

                                            receivedEvents.incrementAndGet();
                                        }
                                    }
                                }
                            }

                        } catch (Exception ignored) {
                        }
                    });

                } else {
                    failedConnections.incrementAndGet();

                    try {
                        response.body().close();
                    } catch (Exception ignored) {
                    }
                }

            }).exceptionally(error -> {
                failedConnections.incrementAndGet();
                return null;
            });

            futures.add(future);
        }

        // STEP 2: Wait for connection attempts

        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).get(60, TimeUnit.SECONDS);

        System.out.println("\nConnections established.");
        System.out.println("Successful: " + successfulConnections.get());
        System.out.println("Failed: " + failedConnections.get());

        // Allow the server and readers to settle

        Thread.sleep(3000);

        // STEP 3: Trigger a configuration update

        String tenantId = "latencyTest-" + System.currentTimeMillis();

        String json = """
                {
                    "configKey": "companyRecoveryTest",
                    "level": "CLIENT",
                    "tenantId": "%s",
                    "value": "175",
                    "author": "SSE Latency Test",
                    "reason": "Measuring SSE event delivery latency"
                }
                """.formatted(tenantId);

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        System.out.println("\nTriggering configuration update...");

        triggerTime.set(System.nanoTime());

        HttpResponse<String> updateResponse = client.send(
                updateRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Update HTTP status: " +
                updateResponse.statusCode());

        if (updateResponse.statusCode() < 200 ||
                updateResponse.statusCode() >= 300) {

            System.out.println("Update failed: " + updateResponse.body());

        } else {

            // STEP 4: Wait for event delivery

            System.out.println("Waiting for SSE event delivery...");
            Thread.sleep(10000);
        }

        // STEP 5: Calculate latency

        List<Long> results = new ArrayList<>(latencies);

        Collections.sort(results);

        System.out.println("\n========== LATENCY TEST RESULTS ==========");

        System.out.println("Connected clients: " +
                successfulConnections.get());

        System.out.println("Failed connections: " +
                failedConnections.get());

        System.out.println("Clients receiving event: " +
                receivedEvents.get());

        if (!results.isEmpty()) {

            long total = 0;

            for (long value : results) {
                total += value;
            }

            double average = (double) total / results.size();

            int p50Index = (int) Math.ceil(0.50 * results.size()) - 1;
            int p99Index = (int) Math.ceil(0.99 * results.size()) - 1;

            System.out.println("Average latency: " +
                    String.format("%.2f ms", average));

            System.out.println("p50 latency: " +
                    results.get(p50Index) + " ms");

            System.out.println("p99 latency: " +
                    results.get(p99Index) + " ms");

        } else {

            System.out.println("No matching SSE events received.");
        }

        // STEP 6: Close connections

        for (InputStream stream : activeStreams) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }

        readerExecutor.shutdownNow();

        System.out.println("\nSSE Latency Test Completed.");
        System.out.println("==========================================");
    }
}
