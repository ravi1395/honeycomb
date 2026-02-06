package com.example.honeycomb.web;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class SharedwallMethodCache {
    private static final Logger log = LoggerFactory.getLogger(SharedwallMethodCache.class);

    public static class MethodCandidate {
        private final Object bean;
        private final Method method;
        private final Sharedwall sharedwall;
        private final boolean allowAlias;

        public MethodCandidate(Object bean, Method method, Sharedwall sharedwall, boolean allowAlias) {
            this.bean = bean;
            this.method = method;
            this.sharedwall = sharedwall;
            this.allowAlias = allowAlias;
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public Sharedwall getSharedwall() { return sharedwall; }
        public boolean isAllowAlias() { return allowAlias; }
    }

    private final ApplicationContext ctx;
    private final Map<String, List<MethodCandidate>> cache = new ConcurrentHashMap<>();
    private volatile long buildDurationMs = 0L;

    public SharedwallMethodCache(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @PostConstruct
    public void init() {
        rebuild();
    }

    public long getBuildDurationMs() {
        return buildDurationMs;
    }

    public int getMethodCount() {
        return cache.size();
    }

    public synchronized long rebuild() {
        long start = System.nanoTime();
        Map<String, List<MethodCandidate>> next = new HashMap<>();

        Map<String, Object> cellBeans = ctx.getBeansWithAnnotation(Cell.class);
        for (Object bean : cellBeans.values()) {
            Class<?> cls = AopUtils.getTargetClass(bean);
            List<MethodCandidate> candidates = findSharedCandidates(bean, cls);
            for (MethodCandidate c : candidates) {
                String alias = resolveAlias(c.getMethod(), c.getSharedwall(), c.isAllowAlias());
                next.computeIfAbsent(alias, k -> new ArrayList<>()).add(c);
            }
        }

        cache.clear();
        cache.putAll(next);
        buildDurationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Sharedwall cache built in {} ms with {} methods", buildDurationMs, cache.size());
        return buildDurationMs;
    }

    public List<MethodCandidate> getCandidates(String methodName) {
        List<MethodCandidate> list = cache.get(methodName);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    private List<MethodCandidate> findSharedCandidates(Object bean, Class<?> cls) {
        List<MethodCandidate> candidates = new ArrayList<>();
        Set<Method> seen = new HashSet<>();

        // method-level @Sharedwall on class
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getDeclaringClass().equals(Object.class)) continue;
            Sharedwall s = m.getAnnotation(Sharedwall.class);
            if (s == null) continue;
            addCandidate(candidates, seen, bean, m, s, true);
        }

        // class-level @Sharedwall applies to all methods
        Sharedwall classShared = cls.getAnnotation(Sharedwall.class);
        if (classShared != null) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getDeclaringClass().equals(Object.class)) continue;
                if (m.getAnnotation(Sharedwall.class) != null) continue;
                addCandidate(candidates, seen, bean, m, classShared, false);
            }
        }

        // interface-level @Sharedwall (type or method)
        for (Class<?> iface : cls.getInterfaces()) {
            Sharedwall ifaceShared = iface.getAnnotation(Sharedwall.class);
            for (Method im : iface.getMethods()) {
                Sharedwall imShared = im.getAnnotation(Sharedwall.class);
                Sharedwall use = imShared != null ? imShared : ifaceShared;
                if (use == null) continue;
                try {
                    Method impl = cls.getMethod(im.getName(), im.getParameterTypes());
                    if (impl.getAnnotation(Sharedwall.class) != null) continue;
                    addCandidate(candidates, seen, bean, impl, use, imShared != null);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }

        return candidates;
    }

    private void addCandidate(List<MethodCandidate> candidates,
                              Set<Method> seen,
                              Object bean,
                              Method method,
                              Sharedwall sharedwall,
                              boolean allowAlias) {
        if (seen.contains(method)) return;
        method.setAccessible(true);
        candidates.add(new MethodCandidate(bean, method, sharedwall, allowAlias));
        seen.add(method);
    }

    private String resolveAlias(Method method, Sharedwall sharedwall, boolean allowAlias) {
        if (allowAlias && sharedwall != null && sharedwall.value() != null && !sharedwall.value().isBlank()) {
            return sharedwall.value();
        }
        return method.getName();
    }
}
