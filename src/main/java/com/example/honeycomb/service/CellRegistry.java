package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CellRegistry implements ApplicationContextAware {
    private ApplicationContext context;

    // map: exposedName -> Class
    private final Map<String, Class<?>> cells = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @PostConstruct
    public void init() throws Exception {
        // 1) discover beans annotated with @Cell
        Map<String, Object> beans = context.getBeansWithAnnotation(Cell.class);
        for (Object bean : beans.values()) {
            Class<?> cls = bean.getClass();
            Cell ann = cls.getAnnotation(Cell.class);
            String name = cellName(cls, ann);
            cells.putIfAbsent(name, cls);
        }


        // 2) classpath scan for classes annotated with @Cell under base package
        // default to application main package
        String base = ClassUtils.getPackageName(context.getApplicationName() == null
            ? HoneycombConstants.Defaults.BASE_PACKAGE
            : context.getApplicationName());
        if (base == null || base.isBlank()) base = HoneycombConstants.Defaults.BASE_PACKAGE;

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        String pattern = PathMatchingResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + ClassUtils.convertClassNameToResourcePath(base)
            + HoneycombConstants.Patterns.CLASS_RESOURCE_SUFFIX;
        org.springframework.core.io.Resource[] resources = resolver.getResources(pattern);
        for (org.springframework.core.io.Resource r : resources) {
            if (!r.isReadable()) continue;
            MetadataReader mr = readerFactory.getMetadataReader(r);
            ClassMetadata cm = mr.getClassMetadata();
            String className = cm.getClassName();
            try {
                Class<?> cls = Class.forName(className);
                Cell ann = cls.getAnnotation(Cell.class);
                if (ann != null) {
                    String name = cellName(cls, ann);
                    cells.putIfAbsent(name, cls);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private String cellName(Class<?> cls, Cell ann) {
        if (ann != null && ann.value() != null && !ann.value().isBlank()) return ann.value();
        return cls.getSimpleName();
    }


    public Set<String> getCellNames() { return Set.copyOf(cells.keySet()); }

    public Optional<Class<?>> getCellClass(String name) { return Optional.ofNullable(cells.get(name)); }

    public Map<String, Object> describeCell(String name) {
        Class<?> cls = cells.get(name);
        if (cls == null) return Collections.emptyMap();
        Map<String,Object> mapping = new LinkedHashMap<>();
        mapping.put(HoneycombConstants.JsonKeys.CLASS_NAME, cls.getName());
        List<Map<String,String>> fields = new ArrayList<>();
        for (Field fieldMap : cls.getDeclaredFields()) {
            Map<String,String> fm = new HashMap<>();
            fm.put(HoneycombConstants.JsonKeys.NAME, fieldMap.getName());
            fm.put(HoneycombConstants.JsonKeys.TYPE, fieldMap.getType().getName());
            fields.add(fm);
        }
        mapping.put(HoneycombConstants.JsonKeys.FIELDS, fields);
        // list shared methods annotated with @Sharedwall (method or interface/type-level)
        List<String> shared = listSharedMethods(cls);
        if (!shared.isEmpty()) mapping.put(HoneycombConstants.JsonKeys.SHARED_METHODS, shared);
        // include optional cell metadata such as configured port
        Cell ann = cls.getAnnotation(Cell.class);
        if (ann != null && ann.port() > 0) {
            mapping.put(HoneycombConstants.JsonKeys.PORT, ann.port());
        }
        return mapping;
    }

    public Flux<String> getCellNamesFlux() {
        return Flux.fromIterable(getCellNames());
    }

    public Mono<Map<String,Object>> describeCellMono(String name) {
        Map<String,Object> d = describeCell(name);
        return d.isEmpty() ? Mono.empty() : Mono.just(d);
    }

    private List<String> listSharedMethods(Class<?> cls) {
        List<String> shared = new ArrayList<>();

        // method-level on class
        for (Method m : cls.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Sharedwall.class)) {
                Sharedwall s = m.getAnnotation(Sharedwall.class);
                String methodName = (s != null && s.value() != null && !s.value().isBlank()) ? s.value() : m.getName();
                shared.add(methodName);
            }
        }

        // class-level
        Sharedwall classShared = cls.getAnnotation(Sharedwall.class);
        if (classShared != null) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Sharedwall.class)) continue;
                shared.add(m.getName());
            }
        }

        // interface-level
        for (Class<?> iface : cls.getInterfaces()) {
            Sharedwall ifaceShared = iface.getAnnotation(Sharedwall.class);
            for (Method im : iface.getMethods()) {
                Sharedwall imShared = im.getAnnotation(Sharedwall.class);
                Sharedwall use = imShared != null ? imShared : ifaceShared;
                if (use == null) continue;
                String methodName = (imShared != null && imShared.value() != null && !imShared.value().isBlank())
                    ? imShared.value()
                    : im.getName();
                if (!shared.contains(methodName)) shared.add(methodName);
            }
        }

        return shared;
    }
}
