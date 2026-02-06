package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@SuppressWarnings("null")
public class SharedwallMethodCache {
    private static final Logger log = LoggerFactory.getLogger(SharedwallMethodCache.class);

    private final ApplicationContext context;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, List<MethodCandidate>>> cacheRef = new AtomicReference<>(Map.of());
    private final AtomicLong lastRefreshMs = new AtomicLong(0);

    private final boolean cacheEnabled;
    private final boolean warmupEnabled;

    private final MeterRegistry meterRegistry;
    private final io.micrometer.core.instrument.Counter cacheHitCounter;
    private final io.micrometer.core.instrument.Counter cacheMissCounter;
    public SharedwallMethodCache(ApplicationContext context,
                                 ObjectMapper objectMapper,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.enabled:true}") boolean cacheEnabled,
                                 MeterRegistry meterRegistry,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.warmup-enabled:true}") boolean warmupEnabled) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.cacheEnabled = cacheEnabled;
        this.meterRegistry = meterRegistry;
        this.warmupEnabled = warmupEnabled;
        this.cacheHitCounter = meterRegistry.counter("honeycomb.shared.cache.hits");
        this.cacheMissCounter = meterRegistry.counter("honeycomb.shared.cache.misses");
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
        long started = System.currentTimeMillis();
        Map<String, List<MethodCandidate>> next = new HashMap<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Object bean = context.getBean(beanName);
                Class<?> cls = bean.getClass();
                if (!cls.isAnnotationPresent(Cell.class)) continue;
                for (Method m : cls.getDeclaredMethods()) {
                    Sharedwall s = m.getAnnotation(Sharedwall.class);
                    if (s == null) continue;
                    String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                    m.setAccessible(true);
                    next.computeIfAbsent(alias, k -> new ArrayList<>()).add(new MethodCandidate(bean, m, s, objectMapper));
                }
            } catch (Throwable ignored) {
            }
        }
        cacheRef.set(Collections.unmodifiableMap(next));
        lastRefreshMs.set(System.currentTimeMillis() - started);
        log.debug("Sharedwall cache refreshed. methods={}, ms={}", next.size(), lastRefreshMs.get());
    }

    public List<MethodCandidate> getCandidates(String methodName) {
        if (!cacheEnabled) {
            return discoverOnDemand(methodName);
        }
        Map<String, List<MethodCandidate>> cache = cacheRef.get();
        List<MethodCandidate> list = cache.get(methodName);
        if (list == null) {
            cacheMissCounter.increment();
            return List.of();
        }
        cacheHitCounter.increment();
        return list;
    }

    public long getLastRefreshMs() {
        return lastRefreshMs.get();
    }

    public long getBuildDurationMs() {
        return lastRefreshMs.get();
    }

    public int getMethodCount() {
        return cacheRef.get().size();
    }

    public long rebuild() {
        refresh();
        return lastRefreshMs.get();
    }

    private List<MethodCandidate> discoverOnDemand(String methodName) {
        List<MethodCandidate> candidates = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Object bean = context.getBean(beanName);
                Class<?> cls = bean.getClass();
                if (!cls.isAnnotationPresent(Cell.class)) continue;
                for (Method m : cls.getDeclaredMethods()) {
                    Sharedwall s = m.getAnnotation(Sharedwall.class);
                    if (s == null) continue;
                    String alias = (s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                    if (!alias.equals(methodName)) continue;
                    m.setAccessible(true);
                    candidates.add(new MethodCandidate(bean, m, s, objectMapper));
                }
            } catch (Throwable ignored) {
            }
        }
        return candidates;
    }

    public static class MethodCandidate {
        private final Object bean;
        private final Method method;
        private final Sharedwall sharedwall;

        private final JavaType[] paramJavaTypes;
        private final Class<?>[] paramClasses;
        private final Invoker invoker;

        public MethodCandidate(Object bean, Method method, Sharedwall sharedwall, ObjectMapper objectMapper) {
            this.bean = bean;
            this.method = method;
            this.sharedwall = sharedwall;
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
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.unreflect(method);
                MethodHandle target = mh;
                if (pcount > 0) {
                    target = mh.asSpreader(Object[].class, pcount)
                            .asType(MethodType.methodType(Object.class, Object.class, Object[].class));
                } else {
                    // adapt no-arg method to (Object, Object[]) -> Object
                    target = mh.asType(MethodType.methodType(Object.class, Object.class))
                            .asType(MethodType.methodType(Object.class, Object.class, Object[].class));
                }
                CallSite site = LambdaMetafactory.metafactory(
                        lookup,
                        "invoke",
                        MethodType.methodType(Invoker.class),
                        MethodType.methodType(Object.class, Object.class, Object[].class),
                        target,
                        target.type()
                );
                tmp = (Invoker) site.getTarget().invokeExact();
            } catch (Throwable t) {
                // fallback: use MethodHandle or reflection via an Invoker implementation
                try {
                    final MethodHandles.Lookup lookupFallback = MethodHandles.lookup();
                    final MethodHandle mhFallback = lookupFallback.unreflect(method);
                    tmp = new Invoker() {
                        @Override
                        public Object invoke(Object targetBean, Object[] args) throws Throwable {
                            if (Modifier.isStatic(method.getModifiers())) {
                                if (pcount == 0) {
                                    return mhFallback.invoke();
                                }
                                return mhFallback.asSpreader(Object[].class, pcount).invokeWithArguments((Object[]) args);
                            } else {
                                Object[] all = new Object[args.length + 1];
                                all[0] = targetBean;
                                System.arraycopy(args, 0, all, 1, args.length);
                                return mhFallback.invokeWithArguments(all);
                            }
                        }
                    };
                } catch (Throwable t2) {
                    tmp = new Invoker() {
                        @Override
                        public Object invoke(Object targetBean, Object[] args) throws Throwable {
                            return method.invoke(targetBean, args);
                        }
                    };
                }
            }
            this.invoker = tmp;
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public Sharedwall getSharedwall() { return sharedwall; }
        public JavaType[] getParamJavaTypes() { return paramJavaTypes; }
        public Class<?>[] getParamClasses() { return paramClasses; }
        public Invoker getInvoker() { return invoker; }

        public interface Invoker {
            Object invoke(Object targetBean, Object[] args) throws Throwable;
        }
    }
}
