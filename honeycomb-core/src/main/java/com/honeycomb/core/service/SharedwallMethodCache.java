package com.honeycomb.core.service;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.Sharedwall;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches and resolves {@link com.honeycomb.core.annotations.Sharedwall @Sharedwall}-annotated
 * methods from {@link com.honeycomb.core.annotations.Cell @Cell} beans.
 *
 * <p>Builds an in-memory index of method name → {@link java.lang.reflect.Method}
 * at startup, supporting fast reflective dispatch from the
 * {@link com.honeycomb.core.web.SharedwallDispatcherController}.
 * Provides hit/miss statistics for observability.</p>
 *
 * @see com.honeycomb.core.annotations.Sharedwall
 */
@Component
@SuppressWarnings("null")
public class SharedwallMethodCache {
    private static final Logger log = LoggerFactory.getLogger(SharedwallMethodCache.class);

    private final ApplicationContext context;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, List<MethodCandidate>>> cacheRef = new AtomicReference<>(Map.of());
    private final AtomicLong lastRefreshDurationMs = new AtomicLong(0);
    private final AtomicLong lastRefreshAtMs = new AtomicLong(0);
    private final AtomicLong nextAllowedRefreshAtMs = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private final boolean cacheEnabled;
    private final boolean warmupEnabled;

    private final MeterRegistry meterRegistry;
    private final Timer cacheRefreshTimer;
    private final long backoffBaseMs;
    private final long backoffMaxMs;
    private final long backoffJitterMs;

    public SharedwallMethodCache(ApplicationContext context,
                                 ObjectMapper objectMapper,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.enabled:true}") boolean cacheEnabled,
                                 MeterRegistry meterRegistry,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.warmup-enabled:true}") boolean warmupEnabled,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.refresh.backoff-base-ms:1000}") long backoffBaseMs,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.refresh.backoff-max-ms:30000}") long backoffMaxMs,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.refresh.jitter-ms:250}") long backoffJitterMs) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.cacheEnabled = cacheEnabled;
        this.meterRegistry = meterRegistry;
        this.warmupEnabled = warmupEnabled;
        this.backoffBaseMs = Math.max(100, backoffBaseMs);
        this.backoffMaxMs = Math.max(this.backoffBaseMs, backoffMaxMs);
        this.backoffJitterMs = Math.max(0, backoffJitterMs);
        this.cacheRefreshTimer = Timer.builder("honeycomb.shared.cache.refresh.duration")
                .description("Duration to refresh sharedwall cache")
                .publishPercentiles(0.5, 0.95)
                .publishPercentileHistogram()
                .register(meterRegistry);
        meterRegistry.gauge("honeycomb.shared.cache.methods", this, SharedwallMethodCache::getMethodCount);
        meterRegistry.gauge("honeycomb.shared.cache.last_refresh_duration_ms", this, SharedwallMethodCache::getLastRefreshDurationMs);
        meterRegistry.gauge("honeycomb.shared.cache.last_refresh_age_ms", this, SharedwallMethodCache::getLastRefreshAgeMs);
        meterRegistry.gauge("honeycomb.shared.cache.consecutive_failures", consecutiveFailures);
    }

    @PostConstruct
    public void warmup() {
        if (!cacheEnabled) return;
        if (warmupEnabled) {
            refresh();
        }
    }

    @Scheduled(fixedDelayString = "${honeycomb.shared.cache.cache-refresh-ms:60000}")
    public void refresh() {
        if (!cacheEnabled) return;
        if (!refreshInProgress.compareAndSet(false, true)) {
            meterRegistry.counter("honeycomb.shared.cache.refresh.skips", "reason", "in_progress").increment();
            return;
        }
        long now = System.currentTimeMillis();
        long nextAllowed = nextAllowedRefreshAtMs.get();
        if (nextAllowed > now) {
            meterRegistry.counter("honeycomb.shared.cache.refresh.skips", "reason", "backoff").increment();
            log.debug("Sharedwall cache refresh skipped due to backoff. nextAllowedMs={}", nextAllowed);
            refreshInProgress.set(false);
            return;
        }
        long started = now;
        try {
            Map<String, List<MethodCandidate>> next = new HashMap<>();
            for (String beanName : context.getBeanDefinitionNames()) {
                try {
                    Object bean = context.getBean(beanName);
                    Class<?> cls = AopUtils.getTargetClass(bean);
                    if (!cls.isAnnotationPresent(Cell.class)) continue;
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.isSynthetic() || m.isBridge()) continue;
                        Sharedwall s = m.getAnnotation(Sharedwall.class);
                        if (s == null) continue;
                        String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                        m.setAccessible(true);
                        next.computeIfAbsent(alias, k -> new ArrayList<>()).add(new MethodCandidate(bean, m, s, objectMapper, meterRegistry));
                    }
                } catch (Throwable ignored) {
                }
            }
            cacheRef.set(Collections.unmodifiableMap(next));
            long dur = System.currentTimeMillis() - started;
            lastRefreshDurationMs.set(dur);
            lastRefreshAtMs.set(System.currentTimeMillis());
            consecutiveFailures.set(0);
            nextAllowedRefreshAtMs.set(0);
            cacheRefreshTimer.record(java.time.Duration.ofMillis(dur));
            meterRegistry.counter("honeycomb.shared.cache.refreshes", "result", "success").increment();
            log.debug("Sharedwall cache refreshed. methods={}, ms={}", next.size(), dur);
        } catch (Throwable ex) {
            long dur = System.currentTimeMillis() - started;
            lastRefreshDurationMs.set(dur);
            try { cacheRefreshTimer.record(java.time.Duration.ofMillis(dur)); } catch (Throwable ignored) {}
            try { meterRegistry.counter("honeycomb.shared.cache.refreshes", "result", "failure").increment(); } catch (Throwable ignored) {}
            long failures = consecutiveFailures.incrementAndGet();
            long backoff = computeBackoffMs(failures);
            long jitter = backoffJitterMs == 0 ? 0 : ThreadLocalRandom.current().nextLong(0, backoffJitterMs + 1);
            nextAllowedRefreshAtMs.set(System.currentTimeMillis() + backoff + jitter);
            log.warn("Sharedwall cache refresh failed (failures={}, backoffMs={}, jitterMs={})", failures, backoff, jitter, ex);
        } finally {
            refreshInProgress.set(false);
        }
    }

    public List<MethodCandidate> getCandidates(String methodName) {
        if (!cacheEnabled) {
            return discoverOnDemand(methodName);
        }
        Map<String, List<MethodCandidate>> cache = cacheRef.get();
        List<MethodCandidate> list = cache.get(methodName);
        if (list == null) {
            meterRegistry.counter("honeycomb.shared.cache.requests", "method", methodName, "outcome", "miss").increment();
            return List.of();
        }
        meterRegistry.counter("honeycomb.shared.cache.requests", "method", methodName, "outcome", "hit").increment();
        return list;
    }

    public List<MethodCandidate> getCandidates(String methodName, String version) {
        String resolvedVersion = normalizeVersion(version);
        return getCandidates(methodName).stream()
                .filter(candidate -> Objects.equals(resolveVersion(candidate), resolvedVersion))
                .toList();
    }

    public Map<String, List<MethodCandidate>> getAllCandidates() {
        if (!cacheEnabled) {
            return discoverAllOnDemand();
        }
        return cacheRef.get();
    }

    public Map<String, List<MethodCandidate>> getAllCandidates(String version) {
        String resolvedVersion = normalizeVersion(version);
        Map<String, List<MethodCandidate>> base = getAllCandidates();
        Map<String, List<MethodCandidate>> filtered = new HashMap<>();
        for (Map.Entry<String, List<MethodCandidate>> entry : base.entrySet()) {
            List<MethodCandidate> list = entry.getValue().stream()
                    .filter(candidate -> Objects.equals(resolveVersion(candidate), resolvedVersion))
                    .toList();
            if (!list.isEmpty()) {
                filtered.put(entry.getKey(), list);
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    private String resolveVersion(MethodCandidate candidate) {
        Sharedwall sharedwall = candidate.getSharedwall();
        if (sharedwall == null || sharedwall.version() == null || sharedwall.version().isBlank()) {
            return "v1";
        }
        return sharedwall.version().trim();
    }

    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "v1";
        }
        return version.trim();
    }

    public long getLastRefreshMs() {
        return lastRefreshDurationMs.get();
    }

    public long getBuildDurationMs() {
        return lastRefreshDurationMs.get();
    }

    public long getLastRefreshDurationMs() {
        return lastRefreshDurationMs.get();
    }

    public long getLastRefreshAtMs() {
        return lastRefreshAtMs.get();
    }

    public long getLastRefreshAgeMs() {
        long at = lastRefreshAtMs.get();
        return at == 0 ? 0 : Math.max(0, System.currentTimeMillis() - at);
    }

    public long getNextAllowedRefreshAtMs() {
        return nextAllowedRefreshAtMs.get();
    }

    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public int getMethodCount() {
        return cacheRef.get().size();
    }

    public long rebuild() {
        refresh();
        return lastRefreshDurationMs.get();
    }

    public void invalidateAll() {
        cacheRef.set(Map.of());
        log.info("Sharedwall cache invalidated (all)");
    }

    public boolean invalidateMethod(String methodName) {
        Map<String, List<MethodCandidate>> current = cacheRef.get();
        if (!current.containsKey(methodName)) return false;
        Map<String, List<MethodCandidate>> next = new HashMap<>(current);
        next.remove(methodName);
        cacheRef.set(Collections.unmodifiableMap(next));
        log.info("Sharedwall cache invalidated for method={}", methodName);
        return true;
    }

    private long computeBackoffMs(long failures) {
        long exp = Math.min(10, Math.max(0, failures - 1));
        long backoff = backoffBaseMs * (1L << exp);
        return Math.min(backoffMaxMs, backoff);
    }

    private List<MethodCandidate> discoverOnDemand(String methodName) {
        List<MethodCandidate> candidates = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Object bean = context.getBean(beanName);
                Class<?> cls = AopUtils.getTargetClass(bean);
                if (!cls.isAnnotationPresent(Cell.class)) continue;
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.isSynthetic() || m.isBridge()) continue;
                    Sharedwall s = m.getAnnotation(Sharedwall.class);
                    if (s == null) continue;
                    String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                    if (!alias.equals(methodName)) continue;
                    m.setAccessible(true);
                    candidates.add(new MethodCandidate(bean, m, s, objectMapper, meterRegistry));
                }
            } catch (Throwable ignored) {
            }
        }
        return candidates;
    }

    private Map<String, List<MethodCandidate>> discoverAllOnDemand() {
        Map<String, List<MethodCandidate>> next = new HashMap<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Object bean = context.getBean(beanName);
                Class<?> cls = AopUtils.getTargetClass(bean);
                if (!cls.isAnnotationPresent(Cell.class)) continue;
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.isSynthetic() || m.isBridge()) continue;
                    Sharedwall s = m.getAnnotation(Sharedwall.class);
                    if (s == null) continue;
                    String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                    m.setAccessible(true);
                    next.computeIfAbsent(alias, k -> new ArrayList<>())
                            .add(new MethodCandidate(bean, m, s, objectMapper, meterRegistry));
                }
            } catch (Throwable ignored) {
            }
        }
        return Collections.unmodifiableMap(next);
    }

    public static class MethodCandidate {
        private final Object bean;
        private final Method method;
        private final Sharedwall sharedwall;

        private final JavaType[] paramJavaTypes;
        private final Class<?>[] paramClasses;
        private final Invoker invoker;
        private final MeterRegistry meterRegistry;

        public MethodCandidate(Object bean, Method method, Sharedwall sharedwall, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
            this.bean = bean;
            this.method = method;
            this.sharedwall = sharedwall;
            this.meterRegistry = meterRegistry;
            int pcount = method.getParameterCount();
            this.paramJavaTypes = new JavaType[pcount];
            this.paramClasses = new Class<?>[pcount];
            for (int i = 0; i < pcount; i++) {
                java.lang.reflect.Parameter param = method.getParameters()[i];
                this.paramClasses[i] = param.getType();
                try {
                    this.paramJavaTypes[i] = objectMapper.getTypeFactory().constructType(param.getParameterizedType());
                } catch (Exception ex) {
                    this.paramJavaTypes[i] = objectMapper.getTypeFactory().constructType(param.getType());
                }
            }
            Invoker tmp = null;
            try {
                if (method.isVarArgs()) {
                    throw new IllegalStateException("varargs not supported for fast invoker");
                }
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandles.Lookup declaringLookup = lookup;
                try {
                    declaringLookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup);
                } catch (Throwable ignored) {
                }
                MethodHandle mh = declaringLookup.unreflect(method);
                MethodHandle target = buildTargetHandle(mh, method, pcount);
                CallSite site = LambdaMetafactory.metafactory(
                        declaringLookup,
                        "invoke",
                        MethodType.methodType(Invoker.class),
                        MethodType.methodType(Object.class, Object.class, Object[].class),
                        target,
                        target.type()
                );
                tmp = (Invoker) site.getTarget().invokeExact();
            } catch (Throwable t) {
                // count that lambda metafactory failed for this method
                try { if (this.meterRegistry != null) this.meterRegistry.counter("honeycomb.shared.invoker.fallbacks", "method", method.getName(), "cell", bean.getClass().getName(), "stage", "lambda_failed").increment(); } catch (Throwable ignored) {}
                // fallback: use MethodHandle or reflection via an Invoker implementation
                try {
                    final MethodHandles.Lookup lookupFallback = MethodHandles.lookup();
                    MethodHandles.Lookup declaringLookupFallback = lookupFallback;
                    try {
                        declaringLookupFallback = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookupFallback);
                    } catch (Throwable ignored) {
                    }
                    final MethodHandle mhFallback = declaringLookupFallback.unreflect(method);
                    tmp = new Invoker() {
                        @Override
                        public Object invoke(Object targetBean, Object[] args) throws Throwable {
                            return invokeWithMethodHandle(mhFallback, method, targetBean, args, pcount);
                        }
                    };
                } catch (Throwable t2) {
                    try { if (this.meterRegistry != null) this.meterRegistry.counter("honeycomb.shared.invoker.fallbacks", "method", method.getName(), "cell", bean.getClass().getName(), "stage", "mh_failed").increment(); } catch (Throwable ignored) {}
                    tmp = new Invoker() {
                        @Override
                        public Object invoke(Object targetBean, Object[] args) throws Throwable {
                            try { if (MethodCandidate.this.meterRegistry != null) MethodCandidate.this.meterRegistry.counter("honeycomb.shared.invoker.fallbacks", "method", method.getName(), "cell", bean.getClass().getName(), "stage", "reflection").increment(); } catch (Throwable ignored) {}
                            if (Modifier.isStatic(method.getModifiers())) {
                                return method.invoke(null, args);
                            }
                            return method.invoke(targetBean, args);
                        }
                    };
                }
            }
            this.invoker = tmp;
        }

        private static MethodHandle buildTargetHandle(MethodHandle mh, Method method, int pcount) {
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (isStatic) {
                if (pcount == 0) {
                    MethodHandle withArgs = MethodHandles.dropArguments(mh, 0, Object.class, Object[].class);
                    return withArgs.asType(MethodType.methodType(Object.class, Object.class, Object[].class));
                }
                MethodHandle spreader = mh.asSpreader(Object[].class, pcount);
                MethodHandle withReceiver = MethodHandles.dropArguments(spreader, 0, Object.class);
                return withReceiver.asType(MethodType.methodType(Object.class, Object.class, Object[].class));
            }

            if (pcount == 0) {
                MethodHandle withArgs = MethodHandles.dropArguments(mh, 1, Object[].class);
                return withArgs.asType(MethodType.methodType(Object.class, Object.class, Object[].class));
            }
            MethodHandle spreader = mh.asSpreader(Object[].class, pcount);
            return spreader.asType(MethodType.methodType(Object.class, Object.class, Object[].class));
        }

        private static Object invokeWithMethodHandle(MethodHandle mh, Method method, Object targetBean, Object[] args, int pcount) throws Throwable {
            if (Modifier.isStatic(method.getModifiers())) {
                if (pcount == 0) {
                    return mh.invoke();
                }
                return mh.asSpreader(Object[].class, pcount).invokeWithArguments((Object[]) args);
            }
            Object[] all = new Object[(args == null ? 0 : args.length) + 1];
            all[0] = targetBean;
            if (args != null && args.length > 0) {
                System.arraycopy(args, 0, all, 1, args.length);
            }
            return mh.invokeWithArguments(all);
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public Sharedwall getSharedwall() { return sharedwall; }
        public JavaType[] getParamJavaTypes() { return paramJavaTypes; }
        public Class<?>[] getParamClasses() { return paramClasses; }
        public Invoker getInvoker() { return invoker; }

        @FunctionalInterface
        public interface Invoker {
            Object invoke(Object targetBean, Object[] args) throws Throwable;
        }
    }
}
