package com.honeycomb.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the adaptive circuit breaker,
 * bound to {@code honeycomb.circuit-breaker.*}.
 *
 * <p>The adaptive circuit breaker automatically adjusts its failure rate
 * threshold based on historical error rate trends. When error rates are
 * consistently low, the threshold tightens (more sensitive). When error
 * rates are volatile, the threshold loosens (more tolerant).</p>
 *
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "honeycomb.circuit-breaker")
public class HoneycombCircuitBreakerProperties {

    /** Whether adaptive thresholds are enabled. */
    private boolean adaptiveEnabled = false;

    /** Initial failure rate threshold (percentage, 0-100). */
    private float initialFailureRateThreshold = 50.0f;

    /** Minimum failure rate threshold the algorithm can lower to. */
    private float minFailureRateThreshold = 20.0f;

    /** Maximum failure rate threshold the algorithm can raise to. */
    private float maxFailureRateThreshold = 80.0f;

    /** Evaluation window size for trend analysis (number of sliding windows). */
    private int evaluationWindowSize = 10;

    /** How often to re-evaluate and adjust thresholds (seconds). */
    private int adjustmentIntervalSeconds = 60;

    /** Wait duration in open state (seconds). */
    private int waitDurationInOpenStateSeconds = 30;

    /** Sliding window size for failure rate calculation. */
    private int slidingWindowSize = 10;

    /** Permitted calls in half-open state. */
    private int permittedCallsInHalfOpen = 5;

    /** Slow call duration threshold (milliseconds). */
    private long slowCallDurationThresholdMs = 5000;

    /** Slow call rate threshold (percentage). */
    private float slowCallRateThreshold = 80.0f;

    // -- getters / setters --------------------------------------------------

    public boolean isAdaptiveEnabled() { return adaptiveEnabled; }
    public void setAdaptiveEnabled(boolean adaptiveEnabled) { this.adaptiveEnabled = adaptiveEnabled; }

    public float getInitialFailureRateThreshold() { return initialFailureRateThreshold; }
    public void setInitialFailureRateThreshold(float v) { this.initialFailureRateThreshold = v; }

    public float getMinFailureRateThreshold() { return minFailureRateThreshold; }
    public void setMinFailureRateThreshold(float v) { this.minFailureRateThreshold = v; }

    public float getMaxFailureRateThreshold() { return maxFailureRateThreshold; }
    public void setMaxFailureRateThreshold(float v) { this.maxFailureRateThreshold = v; }

    public int getEvaluationWindowSize() { return evaluationWindowSize; }
    public void setEvaluationWindowSize(int v) { this.evaluationWindowSize = v; }

    public int getAdjustmentIntervalSeconds() { return adjustmentIntervalSeconds; }
    public void setAdjustmentIntervalSeconds(int v) { this.adjustmentIntervalSeconds = v; }

    public int getWaitDurationInOpenStateSeconds() { return waitDurationInOpenStateSeconds; }
    public void setWaitDurationInOpenStateSeconds(int v) { this.waitDurationInOpenStateSeconds = v; }

    public int getSlidingWindowSize() { return slidingWindowSize; }
    public void setSlidingWindowSize(int v) { this.slidingWindowSize = v; }

    public int getPermittedCallsInHalfOpen() { return permittedCallsInHalfOpen; }
    public void setPermittedCallsInHalfOpen(int v) { this.permittedCallsInHalfOpen = v; }

    public long getSlowCallDurationThresholdMs() { return slowCallDurationThresholdMs; }
    public void setSlowCallDurationThresholdMs(long v) { this.slowCallDurationThresholdMs = v; }

    public float getSlowCallRateThreshold() { return slowCallRateThreshold; }
    public void setSlowCallRateThreshold(float v) { this.slowCallRateThreshold = v; }
}
