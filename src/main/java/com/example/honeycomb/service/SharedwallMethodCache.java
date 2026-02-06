package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import jakarta.annotation.PostConstruct;
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
    private final AtomicReference<Map<String, List<MethodCandidate>>> cacheRef = new AtomicReference<>(Map.of());
    private final AtomicLong lastRefreshMs = new AtomicLong(0);

    private final boolean cacheEnabled;
    private final boolean warmupEnabled;

    public SharedwallMethodCache(ApplicationContext context,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.enabled:true}") boolean cacheEnabled,
                                 @org.springframework.beans.factory.annotation.Value("${honeycomb.shared.cache.warmup-enabled:true}") boolean warmupEnabled) {
        this.context = context;
        this.cacheEnabled = cacheEnabled;
        this.warmupEnabled = warmupEnabled;
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
                    next.computeIfAbsent(alias, k -> new ArrayList<>()).add(new MethodCandidate(bean, m, s));
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
        return list == null ? List.of() : list;
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
                    candidates.add(new MethodCandidate(bean, m, s));
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

        public MethodCandidate(Object bean, Method method, Sharedwall sharedwall) {
            this.bean = bean;
            this.method = method;
            this.sharedwall = sharedwall;
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public Sharedwall getSharedwall() { return sharedwall; }
    }
}
