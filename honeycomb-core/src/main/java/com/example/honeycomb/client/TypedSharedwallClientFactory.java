package com.example.honeycomb.client;

import com.example.honeycomb.dto.SharedwallInvokeInfo;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates typed interface proxies backed by {@link SharedwallClient}.
 *
 * Example:
 * <pre>
 * interface PricingApi {
 *   Mono<Map<String, Object>> discount(Map<String, Object> req);
 * }
 *
 * PricingApi api = new TypedSharedwallClientFactory(client).create(PricingApi.class);
 * api.discount(Map.of("listPrice", 49.99, "discountPct", 0.15));
 * </pre>
 */
public final class TypedSharedwallClientFactory {
    private final SharedwallClient client;

    public TypedSharedwallClientFactory(SharedwallClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public <T> T create(Class<T> apiType) {
        return create(apiType, false);
    }

    public <T> T create(Class<T> apiType, boolean validateAtStartup) {
        return create(apiType, validateAtStartup, SharedwallValidationOptions.defaults());
    }

    public <T> T create(Class<T> apiType,
                        boolean validateAtStartup,
                        SharedwallValidationOptions validationOptions) {
        if (apiType == null || !apiType.isInterface()) {
            throw new IllegalArgumentException("apiType must be a non-null interface");
        }

        if (validateAtStartup) {
            validateMappings(apiType, validationOptions == null ? SharedwallValidationOptions.defaults() : validationOptions);
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> apiType.getName() + "$SharedwallProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> method.invoke(this, args);
                };
            }

            if (!Mono.class.isAssignableFrom(method.getReturnType())) {
                throw new IllegalStateException("Typed sharedwall methods must return Mono<T>: " + method);
            }

            String sharedName = resolveSharedMethodName(method);
            String sharedVersion = resolveSharedMethodVersion(method);
            Object body = buildRequestBody(method, args);
            Type monoPayloadType = resolveMonoPayloadType(method);

            if (monoPayloadType instanceof Class<?> payloadClass) {
                SharedwallResult resultAnn = method.getAnnotation(SharedwallResult.class);
                SharedwallEnvelopeMode mode = resolveEnvelopeMode(payloadClass, resultAnn);
                String targetCell = resultAnn != null && resultAnn.cell() != null && !resultAnn.cell().isBlank()
                        ? resultAnn.cell()
                        : null;
                return client.invokeTyped(sharedName, body, payloadClass, mode, targetCell, sharedVersion);
            }

            ParameterizedTypeReference<?> responseType = ParameterizedTypeReference.forType(monoPayloadType);
            return client.invokeVersioned(sharedName, body, null, responseType, sharedVersion);
        };

        Object proxy = Proxy.newProxyInstance(apiType.getClassLoader(), new Class<?>[]{apiType}, handler);
        return apiType.cast(proxy);
    }

    private <T> void validateMappings(Class<T> apiType, SharedwallValidationOptions options) {
        Map<String, List<SharedwallInvokeInfo>> remoteMethods = loadRemoteMethodsByName();
        List<String> errors = Arrays.stream(apiType.getMethods())
                .filter(m -> m.getDeclaringClass() != Object.class)
                .filter(m -> !m.isDefault())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .map(method -> validateMethod(apiType, method, remoteMethods, options))
                .filter(Objects::nonNull)
                .toList();

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Typed sharedwall client validation failed for "
                    + apiType.getName()
                    + ": "
                    + String.join("; ", errors));
        }
    }

    private String validateMethod(Class<?> apiType,
                                  Method method,
                                  Map<String, List<SharedwallInvokeInfo>> remoteMethods,
                                  SharedwallValidationOptions options) {
        String sharedName = resolveSharedMethodName(method);
        String sharedVersion = resolveSharedMethodVersion(method);
        List<SharedwallInvokeInfo> candidates = remoteMethods.get(sharedName + "@" + sharedVersion);
        if (candidates == null || candidates.isEmpty()) {
            return "missing method '" + sharedName + "' version '" + sharedVersion + "' for " + apiType.getSimpleName() + "." + method.getName();
        }

        String fromCell = client.fromCell();
        boolean signatureMatch = candidates.stream().anyMatch(candidate ->
                signatureMatches(method, candidate)
                        && allowedForCaller(candidate, fromCell, options.enforceAllowedFrom())
                        && !deprecatedBlocked(candidate, options.failOnDeprecated())
        );
        if (!signatureMatch) {
            return "signature/authorization/deprecation mismatch for '" + sharedName + "' in "
                    + apiType.getSimpleName() + "." + method.getName();
        }

        return null;
    }

    private boolean signatureMatches(Method method, SharedwallInvokeInfo candidate) {
        if (method.getParameterCount() != candidate.parameters().size()) {
            return false;
        }
        for (int i = 0; i < method.getParameterCount(); i++) {
            String expected = method.getGenericParameterTypes()[i].getTypeName();
            String actual = candidate.parameters().get(i).type();
            if (!typeCompatible(expected, actual)) {
                return false;
            }
        }

        String expectedReturn = resolveMonoPayloadType(method).getTypeName();
        String actualReturn = candidate.returnType();
        return typeCompatible(expectedReturn, actualReturn)
                || isReactiveWrapperCompatible(expectedReturn, actualReturn);
    }

    private boolean typeCompatible(String expected, String actual) {
        if (Objects.equals(expected, actual)) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        String normalizedExpected = normalizeType(expected);
        String normalizedActual = normalizeType(actual);
        if (Objects.equals(normalizedExpected, normalizedActual)) {
            return true;
        }
        return normalizedActual.endsWith("." + normalizedExpected)
                || normalizedExpected.endsWith("." + normalizedActual);
    }

    private boolean isReactiveWrapperCompatible(String expectedPayloadType, String actualReturnType) {
        if (actualReturnType == null || expectedPayloadType == null) {
            return false;
        }
        String normalized = normalizeType(actualReturnType);
        return normalized.contains("reactor.core.publisher.Mono<" + normalizeType(expectedPayloadType) + ">")
                || normalized.contains("reactor.core.publisher.Flux<" + normalizeType(expectedPayloadType) + ">");
    }

    private boolean allowedForCaller(SharedwallInvokeInfo candidate,
                                     String fromCell,
                                     boolean enforceAllowedFrom) {
        if (!enforceAllowedFrom) {
            return true;
        }
        if (candidate.allowedFrom() == null || candidate.allowedFrom().isEmpty()) {
            return true;
        }
        if (fromCell == null || fromCell.isBlank()) {
            return false;
        }
        return candidate.allowedFrom().stream()
                .anyMatch(a -> "*".equals(a) || fromCell.equalsIgnoreCase(a));
    }

    private boolean deprecatedBlocked(SharedwallInvokeInfo candidate, boolean failOnDeprecated) {
        return failOnDeprecated && candidate.deprecated();
    }

    private SharedwallEnvelopeMode resolveEnvelopeMode(Class<?> payloadClass, SharedwallResult resultAnn) {
        if (resultAnn != null && resultAnn.mode() != null) {
            return resultAnn.mode();
        }
        if (Map.class.isAssignableFrom(payloadClass) || Object.class.equals(payloadClass)) {
            return SharedwallEnvelopeMode.RAW_ENVELOPE;
        }
        return SharedwallEnvelopeMode.FIRST_RESULT;
    }

    private String normalizeType(String type) {
        return type.replace(" ", "")
                .replace("java.lang.", "")
                .replace("java.util.", "");
    }

    private Map<String, List<SharedwallInvokeInfo>> loadRemoteMethodsByName() {
        List<SharedwallInvokeInfo> methods = client.listInvokableMethods()
                .block(Duration.ofSeconds(5));
        if (methods == null) {
            return Map.of();
        }
        Map<String, List<SharedwallInvokeInfo>> byName = new HashMap<>();
        for (SharedwallInvokeInfo method : methods) {
            byName.computeIfAbsent(method.methodName() + "@" + method.version(), ignore -> new java.util.ArrayList<>()).add(method);
        }
        return byName;
    }

    private String resolveSharedMethodName(Method method) {
        SharedwallCall ann = method.getAnnotation(SharedwallCall.class);
        if (ann != null && ann.value() != null && !ann.value().isBlank()) {
            return ann.value();
        }
        return method.getName();
    }

    private String resolveSharedMethodVersion(Method method) {
        SharedwallCall ann = method.getAnnotation(SharedwallCall.class);
        if (ann != null && ann.version() != null && !ann.version().isBlank()) {
            return ann.version();
        }
        return "v1";
    }

    private Object buildRequestBody(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        if (args.length == 1) {
            return args[0];
        }

        java.lang.reflect.Parameter[] params = method.getParameters();
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i++) {
            String key = params[i].isNamePresent() ? params[i].getName() : "arg" + i;
            body.put(key, args[i]);
        }
        return body;
    }

    private Type resolveMonoPayloadType(Method method) {
        Type generic = method.getGenericReturnType();
        if (!(generic instanceof ParameterizedType parameterizedType)) {
            return Object.class;
        }

        Type[] args = parameterizedType.getActualTypeArguments();
        if (args.length == 0 || args[0] == null) {
            return Object.class;
        }
        return args[0];
    }
}
