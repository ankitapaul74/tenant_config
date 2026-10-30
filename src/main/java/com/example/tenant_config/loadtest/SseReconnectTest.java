
package com.example.tenant_config.loadtest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SseReconnectTest {

    private static final String SSE_URL =
            "http://localhost:8080/api/configs/events";

    private static final String UPDATE_URL =
            "http://localhost:8080/api/configs/overrides";

    private static final int MAX_RETRIES = 5;

    private static final long INITIAL_BACKOFF = 1000;
    private static final long MAX_BACKOFF = 10000;

    // Last known event ID from our previous replay test.
    private static final AtomicInteger lastEventId =
            new AtomicInteger(23);

    // Prevent processing the same event ID more than once.
    private static final Set<Integer> processedEventIds =
            ConcurrentHashMap.newKeySet();

    private static final AtomicInteger retryCount =
            new AtomicInteger(0);

    private static final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    public static void main(String[] args) throws Exception {

        System.out.println("======================================");
        System.out.println(" AUTOMATIC SSE RECONNECTION TEST");
        System.out.println("======================================");

        // STEP 1: Establish initial connection.

        System.out.println("\nConnecting to SSE server...");

        HttpResponse<InputStream> initialResponse =
                connectToServer();

        if (initialResponse.statusCode() != 200) {
            System.out.println("Initial connection failed.");
            return;
        }

        System.out.println("Initial connection successful.");

        // STEP 2: Simulate unexpected disconnection.

        System.out.println("\nSimulating unexpected disconnection...");

        initialResponse.body().close();

        System.out.println("Connection lost.");

        // STEP 3: Create an update while disconnected.

        Thread.sleep(1000);

        String tenantId =
                "autoReconnect-" + System.currentTimeMillis();

        String json = """
                {
                    "configKey": "companyRecoveryTest",
                    "level": "CLIENT",
                    "tenantId": "%s",
                    "value": 170,
                    "author": "Automatic Reconnect Test",
                    "reason": "Testing automatic SSE reconnection"
                }
                """.formatted(tenantId);

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        System.out.println("\nCreating update while disconnected...");

        HttpResponse<String> updateResponse = client.send(
                updateRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("Update HTTP status: "
                + updateResponse.statusCode());

        if (updateResponse.statusCode() < 200 ||
                updateResponse.statusCode() >= 300) {

            System.out.println("Update failed: "
                    + updateResponse.body());
            return;
        }

        // Allow scheduled polling to detect the update.

        Thread.sleep(2000);

        // STEP 4: Automatically reconnect and recover events.

        System.out.println("\nStarting automatic reconnection...");

        boolean recovered = reconnectWithRetry();

        // STEP 5: Print results.

        System.out.println("\n========== TEST RESULTS ==========");

        System.out.println("Initial connection: SUCCESS");
        System.out.println("Disconnection: SUCCESS");
        System.out.println("Update while disconnected: SUCCESS");

        System.out.println("Automatic reconnection: "
                + (retryCount.get() > 0 ? "RETRY EXECUTED" : "CONNECTED"));

        System.out.println("Missed event recovered: "
                + (recovered ? "YES" : "NO"));

        System.out.println("Retry attempts: " + retryCount.get());

        System.out.println("Last processed event ID: "
                + lastEventId.get());

        System.out.println("Overall result: "
                + (recovered ? "PASSED" : "FAILED"));

        System.out.println("==================================");
    }

    // Establish an SSE connection using the last processed event ID.

    private static HttpResponse<InputStream> connectToServer()
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SSE_URL))
                .header("Accept", "text/event-stream")
                .header("Last-Event-ID",
                        String.valueOf(lastEventId.get()))
                .GET()
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
    }

    // Automatically retry when connection fails or stream closes.

    private static boolean reconnectWithRetry() {

        long backoff = INITIAL_BACKOFF;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            retryCount.set(attempt);

            System.out.println("\nReconnect attempt: " + attempt);

            HttpResponse<InputStream> response = null;

            try {

                response = connectToServer();

                if (response.statusCode() != 200) {
                    throw new RuntimeException(
                            "HTTP error: " + response.statusCode()
                    );
                }

                System.out.println("Reconnected successfully.");

                boolean eventRecovered =
                        readEvents(response.body());

                response.body().close();

                if (eventRecovered) {
                    return true;
                }

            } catch (Exception e) {

                System.out.println(
                        "Connection failed: " + e.getMessage()
                );

            } finally {

                if (response != null) {
                    try {
                        response.body().close();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (attempt < MAX_RETRIES) {

                // Add random jitter to the exponential backoff.

                long jitter = ThreadLocalRandom.current()
                        .nextLong(0, Math.max(1, backoff / 2));

                long waitTime = backoff + jitter;

                System.out.println(
                        "Retrying after " + waitTime + " ms..."
                );

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                backoff = Math.min(backoff * 2, MAX_BACKOFF);
            }
        }

        return false;
    }

    // Read replayed events and ignore duplicate event IDs.

    private static boolean readEvents(InputStream stream)
            throws Exception {

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(stream))) {

            String line;
            int currentEventId = -1;
            boolean matchingEventReceived = false;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("id:")) {

                    currentEventId = Integer.parseInt(
                            line.substring(3).trim()
                    );
                }

                if (line.startsWith("data:")) {

                    String data = line.substring(5).trim();

                    if (currentEventId > 0) {

                        if (currentEventId <= lastEventId.get()
                                || !processedEventIds.add(currentEventId)) {

                            System.out.println(
                                    "Duplicate event ignored: "
                                            + currentEventId
                            );

                            continue;
                        }

                        lastEventId.set(currentEventId);

                        System.out.println(
                                "Processed event ID: " + currentEventId
                        );
                    }

                    System.out.println("Event data: " + data);

                    if (data.contains("companyRecoveryTest")) {
                        matchingEventReceived = true;
                    }

                    currentEventId = -1;

                    if (matchingEventReceived) {
                        return true;
                    }
                }
            }
        }

        // EOF means the stream closed unexpectedly.
        System.out.println("SSE stream closed. Will retry.");

        return false;
    }
}
