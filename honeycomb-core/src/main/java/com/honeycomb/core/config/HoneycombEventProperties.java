package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for cell event bus.
 *
 * <p><b>Added in v1.3</b> — configurable via {@code honeycomb.events.*} properties.</p>
 *
 * <p>Controls whether the event bus is enabled, which transport to use
 * (in-memory for dev, Redis for multi-instance), the default topic name,
 * and the in-memory sink buffer size.</p>
 */
@ConfigurationProperties(prefix = "honeycomb.events")
public class HoneycombEventProperties {

    /** Enable the cell event bus. Default: true */
    private boolean enabled = true;

    /** Transport type: memory | redis. Default: memory */
    private String transport = "memory";

    /** Default topic for events that don't specify one */
    private String defaultTopic = "honeycomb.events";

    /** Buffer size for in-memory sinks */
    private int bufferSize = 256;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }

    public String getDefaultTopic() { return defaultTopic; }
    public void setDefaultTopic(String defaultTopic) { this.defaultTopic = defaultTopic; }

    public int getBufferSize() { return bufferSize; }
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
}
