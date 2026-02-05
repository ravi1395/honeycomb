package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.MethodOp;
import com.example.honeycomb.annotations.MethodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServiceCellRegistry {
    private static final Logger log = LoggerFactory.getLogger(ServiceCellRegistry.class);

    public static final class ServiceMethod {
        private final Object bean;
        private final Method method;
        private final MethodOp op;
        private final String path;

        public ServiceMethod(Object bean, Method method, MethodOp op, String path) {
            this.bean = bean;
            this.method = method;
            this.op = op;
            this.path = path;
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public MethodOp getOp() { return op; }
        public String getPath() { return path; }
    }

    private final Map<String, Map<String, ServiceMethod>> registry = new ConcurrentHashMap<>();

    public ServiceCellRegistry(ApplicationContext ctx) {
        Map<String, Object> cellBeans = ctx.getBeansWithAnnotation(Cell.class);
        for (Object bean : cellBeans.values()) {
            Class<?> cls = AopUtils.getTargetClass(bean);
            String cellName = resolveCellName(cls);
            Map<String, ServiceMethod> methods = new HashMap<>();

            for (Method m : cls.getMethods()) {
                MethodType mt = m.getAnnotation(MethodType.class);
                if (mt == null) {
                    mt = findInterfaceMethodType(cls, m);
                }
                if (mt == null) continue;
                String path = (mt.path() != null && !mt.path().isBlank()) ? mt.path() : m.getName();
                if (path.contains("/")) {
                    log.warn("ServiceCell {} method {} path contains '/': {} (ignored)", cellName, m.getName(), path);
                    continue;
                }
                if (methods.containsKey(path)) {
                    log.warn("ServiceCell {} has duplicate method path '{}'. Keeping first, ignoring {}", cellName, path, m.getName());
                    continue;
                }
                methods.put(path, new ServiceMethod(bean, m, mt.value(), path));
            }
            if (!methods.isEmpty()) {
                registry.put(cellName, methods);
            }
        }
    }

    private String resolveCellName(Class<?> cls) {
        Cell cellAnn = cls.getAnnotation(Cell.class);
        if (cellAnn != null && cellAnn.value() != null && !cellAnn.value().isBlank()) {
            return cellAnn.value();
        }
        return cls.getSimpleName();
    }

    private MethodType findInterfaceMethodType(Class<?> cls, Method impl) {
        for (Class<?> iface : cls.getInterfaces()) {
            try {
                Method im = iface.getMethod(impl.getName(), impl.getParameterTypes());
                MethodType mt = im.getAnnotation(MethodType.class);
                if (mt != null) return mt;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public boolean hasCell(String cellName) {
        return registry.containsKey(cellName);
    }

    public Map<String, ServiceMethod> getMethods(String cellName) {
        return registry.getOrDefault(cellName, Collections.emptyMap());
    }

    public ServiceMethod getMethod(String cellName, String pathSegment) {
        Map<String, ServiceMethod> methods = registry.get(cellName);
        return methods == null ? null : methods.get(pathSegment);
    }
}
