package io.github.mcclauneck.common.http;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Holds the single, process-wide {@link HttpClient} shared by every outbound
 * HTTP call MCEconomy makes.
 * <p>
 * The core economy backend ({@code MCEconomyAPI}) and any extension that talks
 * to the central server reuse this one client instead of each opening their
 * own. Sharing a single {@link HttpClient} means a single connection pool and
 * executor are used for the whole plugin, which the platform entry points
 * expose to extensions through their {@code getHttpClient()} accessor.
 * </p>
 */
public final class MCEconomyHttpClient {

    /**
     * The lazily-initialized shared client. Marked {@code volatile} for safe
     * publication under the double-checked locking in {@link #shared()}.
     */
    private static volatile HttpClient sharedClient;

    private MCEconomyHttpClient() {
    }

    /**
     * Returns the process-wide shared {@link HttpClient}, creating it lazily on
     * first use.
     *
     * @return the shared HTTP client.
     */
    public static HttpClient shared() {
        HttpClient client = sharedClient;
        if (client == null) {
            synchronized (MCEconomyHttpClient.class) {
                client = sharedClient;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                    sharedClient = client;
                }
            }
        }
        return client;
    }
}
