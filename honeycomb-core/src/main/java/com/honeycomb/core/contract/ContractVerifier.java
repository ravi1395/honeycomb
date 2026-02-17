package com.honeycomb.core.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.core.service.SharedwallMethodCache.MethodCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Verifies that a list of {@link SharedMethodContract} definitions
 * still match the live {@code @Sharedwall} method signatures in the
 * running application.
 *
 * <p>Useful for detecting breaking changes (removed methods, changed
 * parameter types, altered access constraints) between a consumer's
 * saved contract set and the provider's current implementation.</p>
 *
 * @since 1.4.3
 */
@Component
@ConditionalOnProperty(name = "honeycomb.contracts.enabled", havingValue = "true")
public class ContractVerifier {
    private static final Logger log = LoggerFactory.getLogger(ContractVerifier.class);

    private final SharedwallMethodCache methodCache;
    private final ObjectMapper objectMapper;

    public ContractVerifier(SharedwallMethodCache methodCache, ObjectMapper objectMapper) {
        this.methodCache = methodCache;
        this.objectMapper = objectMapper;
    }

    /**
     * Verify a set of contracts against the live method cache.
     *
     * @return list of {@link Violation}s (empty = all contracts satisfied)
     */
    public List<Violation> verify(List<SharedMethodContract> contracts) {
        List<Violation> violations = new ArrayList<>();
        for (SharedMethodContract contract : contracts) {
            violations.addAll(verifyOne(contract));
        }
        if (violations.isEmpty()) {
            log.info("All {} contracts verified successfully", contracts.size());
        } else {
            log.warn("{} contract violations found across {} contracts", violations.size(), contracts.size());
        }
        return Collections.unmodifiableList(violations);
    }

    private List<Violation> verifyOne(SharedMethodContract contract) {
        List<Violation> v = new ArrayList<>();
        String name = contract.methodName();

        // 1) method must exist
        List<MethodCandidate> candidates = methodCache.getCandidates(name, contract.version());
        if (candidates.isEmpty()) {
            v.add(new Violation(name, contract.version(), "METHOD_MISSING",
                    "Shared method '" + name + "' version '" + contract.version() + "' not found"));
            return v;
        }

        MethodCandidate candidate = candidates.getFirst();
        java.lang.reflect.Method method = candidate.getMethod();

        // 2) parameter count match
        if (contract.parameters().size() != method.getParameterCount()) {
            v.add(new Violation(name, contract.version(), "PARAM_COUNT_MISMATCH",
                    "Expected " + contract.parameters().size() + " parameters but found " + method.getParameterCount()));
        } else {
            // 3) parameter type compatibility
            java.lang.reflect.Parameter[] actual = method.getParameters();
            for (int i = 0; i < contract.parameters().size(); i++) {
                SharedMethodContract.ParamDescriptor expected = contract.parameters().get(i);
                String actualType = actual[i].getParameterizedType().getTypeName();
                if (!actualType.equals(expected.type()) && !actual[i].getType().getName().equals(expected.type())) {
                    v.add(new Violation(name, contract.version(), "PARAM_TYPE_MISMATCH",
                            "Parameter " + i + ": expected type '" + expected.type()
                                    + "' but found '" + actualType + "'"));
                }
            }
        }

        // 4) allowedFrom unchanged
        var sw = candidate.getSharedwall();
        List<String> actualAllowed = sw != null ? List.of(sw.allowedFrom()) : List.of();
        if (!new HashSet<>(actualAllowed).equals(new HashSet<>(contract.allowedFrom()))) {
            v.add(new Violation(name, contract.version(), "ACCESS_CONSTRAINT_CHANGED",
                    "allowedFrom changed: contract=" + contract.allowedFrom()
                            + " actual=" + actualAllowed));
        }

        return v;
    }

    /**
     * A single contract violation.
     */
    public record Violation(
            String methodName,
            String version,
            String code,
            String message
    ) {}
}
