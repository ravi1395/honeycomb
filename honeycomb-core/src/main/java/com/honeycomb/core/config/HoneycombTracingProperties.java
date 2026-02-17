package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Honeycomb OpenTelemetry tracing,
 * bound to {@code honeycomb.tracing.*}.
 *
 * <p>Controls tracing export, sampling, and span enrichment. When enabled,
 * Honeycomb automatically bridges Micrometer observations to OpenTelemetry
 * spans and exports them via OTLP.</p>
 *
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "honeycomb.tracing")
public class HoneycombTracingProperties {

    /** Master switch for distributed tracing. */
    private boolean enabled = true;

    /** OTLP endpoint for trace export (e.g. http://localhost:4318/v1/traces). */
    private String otlpEndpoint = "http://localhost:4318/v1/traces";

    /** Sampling probability (0.0 to 1.0). 1.0 = sample everything. */
    private double samplingProbability = 1.0;

    /** Whether to propagate W3C traceparent headers (default: true). */
    private boolean propagateW3c = true;

    /** Whether to add cell-name and tenant-id as span attributes. */
    private boolean enrichSpans = true;

    /** Service name for OTLP export. */
    private String serviceName = "honeycomb";

    // -- getters / setters --------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOtlpEndpoint() { return otlpEndpoint; }
    public void setOtlpEndpoint(String otlpEndpoint) { this.otlpEndpoint = otlpEndpoint; }

    public double getSamplingProbability() { return samplingProbability; }
    public void setSamplingProbability(double samplingProbability) { this.samplingProbability = samplingProbability; }

    public boolean isPropagateW3c() { return propagateW3c; }
    public void setPropagateW3c(boolean propagateW3c) { this.propagateW3c = propagateW3c; }

    public boolean isEnrichSpans() { return enrichSpans; }
    public void setEnrichSpans(boolean enrichSpans) { this.enrichSpans = enrichSpans; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}
