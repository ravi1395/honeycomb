package com.honeycomb.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable model representing a network address for a cell instance.
 * Used for static service discovery and inter-cell routing.
 *
 * <p><b>v2.0:</b> Added optional {@link #contextPath} field to support
 * Tomcat-based deployments where each cell WAR is served at a distinct
 * context path (e.g. {@code /SampleModel}). In the classic multi-JVM
 * model the context path is empty and routing uses {@code host:port} only.</p>
 *
 * @see com.honeycomb.core.service.CellAddressService
 */
@Data
@NoArgsConstructor
public class CellAddress {
    private Long id;
    private String cellName;
    private String host;
    private Integer port;

    /**
     * Optional servlet context path (e.g. {@code "/SampleModel"}).
     * Must start with {@code "/"} or be empty/null for root deployments.
     * <p>Used by {@link #getBaseUrl()} to construct the full URL prefix.</p>
     */
    private String contextPath;

    /** Legacy 4-arg constructor — context path defaults to empty. */
    public CellAddress(Long id, String cellName, String host, Integer port) {
        this(id, cellName, host, port, null);
    }

    /** Full constructor including context path. */
    public CellAddress(Long id, String cellName, String host, Integer port, String contextPath) {
        this.id = id;
        this.cellName = cellName;
        this.host = host;
        this.port = port;
        this.contextPath = normalizeContextPath(contextPath);
    }

    /**
     * Returns the full HTTP base URL for this cell address, including the
     * context path when present. Examples:
     * <ul>
     *   <li>{@code http://host:8080} (classic multi-JVM, no context path)</li>
     *   <li>{@code http://host:8080/SampleModel} (Tomcat WAR deployment)</li>
     * </ul>
     */
    public String getBaseUrl() {
        StringBuilder sb = new StringBuilder("http://");
        sb.append(host != null ? host : "localhost");
        if (port != null && port > 0) {
            sb.append(':').append(port);
        }
        if (contextPath != null && !contextPath.isEmpty()) {
            sb.append(contextPath);
        }
        return sb.toString();
    }

    /**
     * Display key for logging and metrics: {@code host:port/ctx} or {@code host:port}.
     */
    public String displayKey() {
        String base = (host != null ? host : "localhost") + ":" + (port != null ? port : 0);
        if (contextPath != null && !contextPath.isEmpty()) {
            return base + contextPath;
        }
        return base;
    }

    private static String normalizeContextPath(String ctx) {
        if (ctx == null || ctx.isBlank()) return "";
        String trimmed = ctx.trim();
        if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
        // Strip trailing slash
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
