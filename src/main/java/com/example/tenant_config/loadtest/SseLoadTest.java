
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SseLoadTest {

    private static final int CLIENT_COUNT = 500;

    private static final String SSE_URL =
            "http://localhost:8080/api/configs/events";

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        ExecutorService readerExecutor =
                Executors.newCachedThreadPool();

        AtomicInteger successfulConnections = new AtomicInteger();
        AtomicInteger failedConnections = new AtomicInteger();
        AtomicInteger receivedEvents = new AtomicInteger();

        ConcurrentMap<String, AtomicInteger> errors =
                new ConcurrentHashMap<>();

        List<InputStream> activeStreams =
                new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures =
                new ArrayList<>();

        System.out.println("Starting SSE Load Test...");
        System.out.println("Target clients: " + CLIENT_COUNT);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CLIENT_COUNT; i++) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SSE_URL))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            CompletableFuture<Void> future =
                    client.sendAsync(
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
                                            receivedEvents.incrementAndGet();
                                        }
                                    }

                                } catch (Exception e) {
                                    // Connection closed.
                                }
                            });

                        } else {

                            failedConnections.incrementAndGet();

                            errors.computeIfAbsent(
                                "HTTP " + response.statusCode(),
                                key -> new AtomicInteger()
                            ).incrementAndGet();

                            try {
                                response.body().close();
                            } catch (Exception ignored) {
                            }
                        }

                    }).exceptionally(error -> {

                        failedConnections.incrementAndGet();

                        Throwable cause = error;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                        }

                        String errorName =
                                cause.getClass().getSimpleName()
                                + ": " + cause.getMessage();

                        errors.computeIfAbsent(
                            errorName,
                            key -> new AtomicInteger()
                        ).incrementAndGet();

                        return null;
                    });

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.out.println("Some requests did not finish in time.");
        }

        long connectionTime =
                System.currentTimeMillis() - startTime;

        System.out.println("\n========== LOAD TEST RESULTS ==========");
        System.out.println("Target clients: " + CLIENT_COUNT);
        System.out.println("Successful: " + successfulConnections.get());
        System.out.println("Failed: " + failedConnections.get());
        System.out.println("Connection time: " + connectionTime + " ms");

        System.out.println("\nFailure details:");

        errors.forEach((error, count) ->
                System.out.println(error + " = " + count.get())
        );

        System.out.println("\nKeeping connections open for 30 seconds...");

        Thread.sleep(30000);

        System.out.println("Events received: " + receivedEvents.get());

        for (InputStream stream : activeStreams) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }

        readerExecutor.shutdownNow();

        System.out.println("\nLoad test completed.");
        System.out.println("=======================================");
    }
}
