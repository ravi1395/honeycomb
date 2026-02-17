package com.honeycomb.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.config.HoneycombContractProperties;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.core.service.SharedwallMethodCache.MethodCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates {@link SharedMethodContract} descriptors from the live
 * {@link SharedwallMethodCache}.
 *
 * <p>Iterates over every discovered {@code @Sharedwall} method, extracts
 * parameter names/types, return type, access constraints, and version
 * to build a portable contract definition that can be serialised to
 * Spring Cloud Contract YAML or Pact JSON.</p>
 *
 * @since 1.4.3
 */
@Component
@ConditionalOnProperty(name = "honeycomb.contracts.enabled", havingValue = "true")
public class ContractGenerator {
    private static final Logger log = LoggerFactory.getLogger(ContractGenerator.class);

    private final SharedwallMethodCache methodCache;
    private final HoneycombContractProperties properties;
    private final ObjectMapper objectMapper;

    public ContractGenerator(SharedwallMethodCache methodCache,
                             HoneycombContractProperties properties,
                             ObjectMapper objectMapper) {
        this.methodCache = methodCache;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate contracts for <em>all</em> cached shared methods.
     */
    public List<SharedMethodContract> generateAll() {
        Map<String, List<MethodCandidate>> all = methodCache.getAllCandidates();
        List<SharedMethodContract> contracts = new ArrayList<>();
        for (Map.Entry<String, List<MethodCandidate>> entry : all.entrySet()) {
            String methodName = entry.getKey();
            for (MethodCandidate candidate : entry.getValue()) {
                if (!matchesPackageFilter(candidate)) continue;
                contracts.add(toContract(methodName, candidate));
            }
        }
        log.info("Generated {} shared-method contracts", contracts.size());
        return Collections.unmodifiableList(contracts);
    }

    /**
     * Generate a contract for a single method name (first candidate only).
     */
    public Optional<SharedMethodContract> generate(String methodName) {
        List<MethodCandidate> candidates = methodCache.getCandidates(methodName);
        if (candidates.isEmpty()) return Optional.empty();
        MethodCandidate candidate = candidates.getFirst();
        return Optional.of(toContract(methodName, candidate));
    }

    // -- internals ----------------------------------------------------------

    private SharedMethodContract toContract(String methodName, MethodCandidate candidate) {
        Method method = candidate.getMethod();
        Sharedwall sw = candidate.getSharedwall();

        SharedMethodContract.Builder builder = SharedMethodContract.builder()
                .methodName(methodName)
                .version(sw != null && sw.version() != null && !sw.version().isBlank() ? sw.version() : "v1")
                .declaringClass(method.getDeclaringClass().getName())
                .returnType(prettyType(method.getGenericReturnType()));

        // parameters
        Parameter[] params = method.getParameters();
        for (Parameter p : params) {
            builder.addParameter(
                    p.isNamePresent() ? p.getName() : p.getType().getSimpleName(),
                    prettyType(p.getParameterizedType()),
                    !p.getType().isPrimitive() // primitives are always required
            );
        }

        // access constraints
        if (sw != null && sw.allowedFrom().length > 0) {
            for (String cell : sw.allowedFrom()) {
                builder.addAllowedFrom(cell);
            }
        }

        // generate sample request
        if (params.length > 0) {
            Map<String, Object> example = new LinkedHashMap<>();
            for (Parameter p : params) {
                example.put(p.isNamePresent() ? p.getName() : p.getType().getSimpleName(),
                        defaultValueFor(p.getType()));
            }
            builder.addExampleRequest(example);
        }

        // generate sample response
        Map<String, Object> exResp = new LinkedHashMap<>();
        exResp.put("result", defaultValueFor(unwrapReactive(method.getGenericReturnType())));
        builder.addExampleResponse(exResp);

        return builder.build();
    }

    private boolean matchesPackageFilter(MethodCandidate candidate) {
        List<String> pkgs = properties.getIncludePackages();
        if (pkgs == null || pkgs.isEmpty()) return true;
        String className = candidate.getMethod().getDeclaringClass().getName();
        return pkgs.stream().anyMatch(className::startsWith);
    }

    private static String prettyType(Type type) {
        if (type instanceof Class<?> cls) {
            return cls.getName();
        }
        return type.getTypeName();
    }

    private static Class<?> unwrapReactive(Type type) {
        if (type instanceof ParameterizedType pt) {
            String raw = pt.getRawType().getTypeName();
            if (raw.startsWith("reactor.core.publisher.Mono") || raw.startsWith("reactor.core.publisher.Flux")) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> inner) {
                    return inner;
                }
            }
        }
        if (type instanceof Class<?> cls) return cls;
        return Object.class;
    }

    private static Object defaultValueFor(Class<?> type) {
        if (type == null || type == Void.class || type == void.class) return null;
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return 0;
        if (type == Long.class || type == long.class) return 0L;
        if (type == Double.class || type == double.class) return 0.0;
        if (type == Float.class || type == float.class) return 0.0f;
        if (type == Boolean.class || type == boolean.class) return false;
        if (type == List.class) return List.of();
        if (type == Map.class) return Map.of();
        return "object";
    }
}
