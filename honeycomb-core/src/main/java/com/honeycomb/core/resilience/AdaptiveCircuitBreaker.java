package com.honeycomb.core.resilience;

import com.honeycomb.core.config.HoneycombCircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Adaptive circuit breaker that auto-tunes failure rate thresholds
 * based on historical error rate trends.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Periodically samples each circuit breaker's failure rate.</li>
 *   <li>Maintains a sliding window of recent failure rates.</li>
 *   <li>Calculates trend: if error rates are consistently low, tighten the threshold
 *       (make it more sensitive); if volatile, loosen it (more tolerant).</li>
 *   <li>Updates circuit breaker configurations within configured min/max bounds.</li>
 * </ol>
 *
 * @since 1.5.0
 * @see HoneycombCircuitBreakerProperties
 */
@Component
@ConditionalOnProperty(name = "honeycomb.circuit-breaker.adaptive-enabled", havingValue = "true")
public class AdaptiveCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveCircuitBreaker.class);

    private final CircuitBreakerRegistry registry;
    private final HoneycombCircuitBreakerProperties properties;

    /** Sliding window of recent failure rates per circuit breaker name. */
    private final Map<String, ConcurrentLinkedDeque<Float>> failureRateHistory = new ConcurrentHashMap<>();

    /** Current adjusted thresholds per circuit breaker name. */
    private final Map<String, Float> currentThresholds = new ConcurrentHashMap<>();

    public AdaptiveCircuitBreaker(CircuitBreakerRegistry registry,
                                   HoneycombCircuitBreakerProperties properties) {
        this.registry = registry;
        this.properties = properties;
        log.info("Adaptive circuit breaker enabled: initial={}%, min={}%, max={}%, interval={}s",
                properties.getInitialFailureRateThreshold(),
                properties.getMinFailureRateThreshold(),
                properties.getMaxFailureRateThreshold(),
                properties.getAdjustmentIntervalSeconds());
    }

    /**
     * Periodic evaluation of circuit breaker metrics and threshold adjustment.
     */
    @Scheduled(fixedDelayString = "${honeycomb.circuit-breaker.adjustment-interval-seconds:60}000")
    public void evaluateAndAdjust() {
        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            String name = cb.getName();
            float currentFailureRate = cb.getMetrics().getFailureRate();

            // Skip if no calls have been recorded yet (returns -1)
            if (currentFailureRate < 0) continue;

            // Track history
            ConcurrentLinkedDeque<Float> history = failureRateHistory.computeIfAbsent(
                    name, k -> new ConcurrentLinkedDeque<>());
            history.addLast(currentFailureRate);
            while (history.size() > properties.getEvaluationWindowSize()) {
                history.pollFirst();
            }

            // Need enough data points to make a decision
            if (history.size() < 3) continue;

            // Calculate statistics
            float avgRate = 0;
            float maxRate = -Float.MAX_VALUE;
            float minRate = Float.MAX_VALUE;
            for (float rate : history) {
                avgRate += rate;
                maxRate = Math.max(maxRate, rate);
                minRate = Math.min(minRate, rate);
            }
            avgRate /= history.size();
            float volatility = maxRate - minRate;

            // Determine new threshold
            float currentThreshold = currentThresholds.getOrDefault(name,
                    properties.getInitialFailureRateThreshold());
            float newThreshold;

            if (avgRate < 5.0f && volatility < 10.0f) {
                // Very stable and low error rate — tighten threshold
                newThreshold = Math.max(currentThreshold - 5.0f,
                        properties.getMinFailureRateThreshold());
            } else if (avgRate > 30.0f || volatility > 40.0f) {
                // High error rate or volatile — loosen threshold
                newThreshold = Math.min(currentThreshold + 5.0f,
                        properties.getMaxFailureRateThreshold());
            } else {
                // Moderate — nudge toward initial
                float diff = properties.getInitialFailureRateThreshold() - currentThreshold;
                newThreshold = currentThreshold + (diff * 0.1f);
            }

            // Clamp to bounds
            newThreshold = Math.max(properties.getMinFailureRateThreshold(),
                    Math.min(newThreshold, properties.getMaxFailureRateThreshold()));

            if (Math.abs(newThreshold - currentThreshold) > 0.5f) {
                currentThresholds.put(name, newThreshold);
                updateCircuitBreakerConfig(name, newThreshold);
                log.info("Adaptive CB '{}': threshold adjusted {}% → {}% (avgRate={}%, volatility={}%)",
                        name,
                        String.format("%.1f", currentThreshold),
                        String.format("%.1f", newThreshold),
                        String.format("%.1f", avgRate),
                        String.format("%.1f", volatility));
            }
        }
    }

    private void updateCircuitBreakerConfig(String name, float newThreshold) {
        try {
            CircuitBreakerConfig newConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(newThreshold)
                    .slowCallRateThreshold(properties.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(Duration.ofMillis(properties.getSlowCallDurationThresholdMs()))
                    .waitDurationInOpenState(Duration.ofSeconds(properties.getWaitDurationInOpenStateSeconds()))
                    .permittedNumberOfCallsInHalfOpenState(properties.getPermittedCallsInHalfOpen())
                    .slidingWindowSize(properties.getSlidingWindowSize())
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .build();

            // Remove existing CB so new config actually takes effect
            registry.remove(name);
            CircuitBreaker newCb = registry.circuitBreaker(name, newConfig);
        } catch (Exception ex) {
            log.warn("Failed to update circuit breaker '{}' config: {}", name, ex.getMessage());
        }
    }

    /**
     * Returns the current adaptive thresholds for all circuit breakers.
     */
    public Map<String, Float> getCurrentThresholds() {
        return Map.copyOf(currentThresholds);
    }

    /**
     * Returns the failure rate history for a specific circuit breaker.
     */
    public ConcurrentLinkedDeque<Float> getFailureRateHistory(String name) {
        return failureRateHistory.getOrDefault(name, new ConcurrentLinkedDeque<>());
    }
}
