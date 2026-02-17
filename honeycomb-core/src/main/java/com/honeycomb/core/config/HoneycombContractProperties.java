package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Honeycomb contract testing, bound to
 * {@code honeycomb.contracts.*}.
 *
 * <p>When enabled, the framework auto-generates consumer-driven contract
 * stubs from {@code @Sharedwall}-annotated methods at build time and
 * exposes a runtime contract verification endpoint.</p>
 *
 * @since 1.4.3
 */
@ConfigurationProperties(prefix = "honeycomb.contracts")
public class HoneycombContractProperties {

    /** Master switch for contract testing support. */
    private boolean enabled = false;

    /** Output directory (relative to project root) for generated contract files. */
    private String outputDir = "target/honeycomb-contracts";

    /** Format for generated contracts: {@code spring-cloud-contract} or {@code pact}. */
    private String format = "spring-cloud-contract";

    /** Base package filter — only generate contracts for cells in these packages. Empty means all. */
    private List<String> includePackages = new ArrayList<>();

    /** Whether to auto-verify contracts on application startup. */
    private boolean verifyOnStartup = false;

    /** Whether to publish generated stubs to a stub runner repository. */
    private boolean publishStubs = false;

    // -- getters / setters --------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public List<String> getIncludePackages() { return includePackages; }
    public void setIncludePackages(List<String> includePackages) { this.includePackages = includePackages; }

    public boolean isVerifyOnStartup() { return verifyOnStartup; }
    public void setVerifyOnStartup(boolean verifyOnStartup) { this.verifyOnStartup = verifyOnStartup; }

    public boolean isPublishStubs() { return publishStubs; }
    public void setPublishStubs(boolean publishStubs) { this.publishStubs = publishStubs; }
}
