package com.honeycomb.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Honeycomb gRPC transport module.
 *
 * <p>Bound to the {@code honeycomb.grpc} prefix. Controls server port, TLS,
 * client target resolution, and transport mode selection.</p>
 *
 * <h3>Transport Modes</h3>
 * <ul>
 *   <li>{@code http} — default, uses Spring WebFlux HTTP only</li>
 *   <li>{@code grpc} — uses gRPC for shared-method invocations and cell CRUD</li>
 *   <li>{@code both} — enables both HTTP and gRPC transports simultaneously</li>
 * </ul>
 *
 * <h3>Example YAML</h3>
 * <pre>{@code
 * honeycomb:
 *   grpc:
 *     enabled: true
 *     transport: both             # http | grpc | both
 *     server:
 *       port: 9090
 *       tls:
 *         enabled: false
 *     client:
 *       default-target: "localhost:9090"
 *       per-cell-targets:
 *         OrderCell: "order-service:9090"
 * }</pre>
 *
 * @since 1.4.0
 */
@Data
@ConfigurationProperties(prefix = "honeycomb.grpc")
public class HoneycombGrpcProperties {

    /** Master switch: enable the gRPC transport module. */
    private boolean enabled = false;

    /**
     * Transport mode: {@code http}, {@code grpc}, or {@code both}.
     * <ul>
     *   <li>{@code http}  — gRPC module is loaded but only HTTP endpoints are active</li>
     *   <li>{@code grpc}  — only gRPC endpoints are started; HTTP shared endpoints are disabled</li>
     *   <li>{@code both}  — both HTTP and gRPC endpoints are active concurrently</li>
     * </ul>
     */
    private TransportMode transport = TransportMode.BOTH;

    /** gRPC server configuration. */
    private Server server = new Server();

    /** gRPC client configuration for outgoing inter-cell calls. */
    private Client client = new Client();

    /** Reflection service — useful for grpcurl/grpcui debugging. */
    private boolean reflectionEnabled = true;

    /** Health service — register gRPC health check. */
    private boolean healthEnabled = true;

    /**
     * Transport mode enumeration.
     */
    public enum TransportMode {
        /** HTTP-only: gRPC module provides no active endpoints. */
        HTTP,
        /** gRPC-only: starts gRPC server, disables HTTP shared endpoints. */
        GRPC,
        /** Both: HTTP and gRPC servers run concurrently on their respective ports. */
        BOTH
    }

    /**
     * gRPC server settings.
     */
    @Data
    public static class Server {
        /** gRPC server listen port (0 = auto-pick). Default 9090. */
        private int port = 9090;

        /** Maximum inbound message size in bytes. Default 4MB. */
        private int maxInboundMessageSize = 4 * 1024 * 1024;

        /** Server-side keepalive time. */
        private Duration keepAliveTime = Duration.ofMinutes(5);

        /** Server-side keepalive timeout. */
        private Duration keepAliveTimeout = Duration.ofSeconds(20);

        /** Enable server-side TLS. */
        private Tls tls = new Tls();
    }

    /**
     * gRPC client settings for outgoing calls.
     */
    @Data
    public static class Client {
        /**
         * Default gRPC target for cells without per-cell config.
         * Format: {@code host:port} or {@code dns:///service-name:port}.
         */
        private String defaultTarget = "localhost:9090";

        /** Per-cell gRPC target overrides. Key = cell name, value = host:port. */
        private Map<String, String> perCellTargets = new HashMap<>();

        /** Client-side deadline/timeout for unary RPCs. */
        private Duration deadline = Duration.ofSeconds(10);

        /** Enable client-side TLS. */
        private Tls tls = new Tls();

        /** Maximum retry attempts for failed RPCs before circuit-breaker engagement. */
        private int maxRetries = 1;

        /** Enable client-side load balancing (round_robin, pick_first). */
        private String loadBalancingPolicy = "round_robin";

        /** Negotiation type: plaintext or tls. */
        private String negotiationType = "plaintext";
    }

    /**
     * TLS/SSL configuration shared between server and client.
     */
    @Data
    public static class Tls {
        /** Enable TLS. */
        private boolean enabled = false;

        /** Path to the certificate chain file (PEM). */
        private String certChainPath;

        /** Path to the private key file (PEM). */
        private String privateKeyPath;

        /** Path to the trusted CA certificates (PEM) for mutual TLS. */
        private String trustCertPath;
    }
}
