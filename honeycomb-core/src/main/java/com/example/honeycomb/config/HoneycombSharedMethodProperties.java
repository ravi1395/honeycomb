package com.example.honeycomb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "honeycomb.shared.methods")
public class HoneycombSharedMethodProperties {

    private Map<String, MethodPolicy> policies = new HashMap<>();

    public Map<String, MethodPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, MethodPolicy> policies) {
        this.policies = policies == null ? new HashMap<>() : policies;
    }

    public MethodPolicy resolve(String methodName, String version) {
        String resolvedVersion = (version == null || version.isBlank()) ? "v1" : version.trim();
        String key = methodName + ":" + resolvedVersion;
        MethodPolicy specific = policies.get(key);
        if (specific != null) {
            return specific.withPolicyName(key);
        }
        MethodPolicy byMethod = policies.get(methodName);
        if (byMethod != null) {
            return byMethod.withPolicyName(methodName);
        }
        MethodPolicy defaults = policies.get("default");
        if (defaults != null) {
            return defaults.withPolicyName("default");
        }
        return MethodPolicy.defaults().withPolicyName("default");
    }

    public static class MethodPolicy {
        private Duration timeout = Duration.ofSeconds(5);
        private int retryCount = 1;
        private Duration retryBackoff = Duration.ofMillis(200);
        private boolean circuitBreakerEnabled = true;
        private String policyName = "default";

        public static MethodPolicy defaults() {
            return new MethodPolicy();
        }

        public MethodPolicy withPolicyName(String policyName) {
            MethodPolicy copy = new MethodPolicy();
            copy.timeout = timeout;
            copy.retryCount = retryCount;
            copy.retryBackoff = retryBackoff;
            copy.circuitBreakerEnabled = circuitBreakerEnabled;
            copy.policyName = policyName == null || policyName.isBlank() ? "default" : policyName;
            return copy;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = Math.max(0, retryCount);
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff == null ? Duration.ofMillis(200) : retryBackoff;
        }

        public boolean isCircuitBreakerEnabled() {
            return circuitBreakerEnabled;
        }

        public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
            this.circuitBreakerEnabled = circuitBreakerEnabled;
        }

        public String getPolicyName() {
            return policyName;
        }
    }
}
