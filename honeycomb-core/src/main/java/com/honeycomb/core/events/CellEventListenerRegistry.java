package com.honeycomb.core.events;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.dto.CellEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers methods annotated with {@link CellEventListener} in {@link Cell} beans
 * and routes incoming events to them.
 *
 * <p><b>Added in v1.3</b> — powers the declarative event listener mechanism.</p>
 *
 * <p>At startup ({@code @PostConstruct}), scans all Spring beans for methods
 * annotated with {@code @CellEventListener}. Handlers are indexed by event type
 * for O(1) lookup. Wildcard handlers (no type filter) receive all events.</p>
 *
 * <p>Event routing respects the {@code order()} attribute on the annotation —
 * lower values execute first. Source-cell filtering via {@code fromCells()} allows
 * listeners to react only to events from specific cells.</p>
 */
@Component
public class CellEventListenerRegistry {
    private static final Logger log = LoggerFactory.getLogger(CellEventListenerRegistry.class);

    private final ApplicationContext context;
    private final CellEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    // eventType -> list of handlers, sorted by @CellEventListener.order()
    private final Map<String, List<HandlerEntry>> handlers = new ConcurrentHashMap<>();
    // handlers with no type filter — invoked for every event type
    private final List<HandlerEntry> wildcardHandlers = Collections.synchronizedList(new ArrayList<>());

    private Counter eventsRouted;
    private Counter eventsDropped;

    public CellEventListenerRegistry(ApplicationContext context,
                                     CellEventPublisher eventPublisher,
                                     MeterRegistry meterRegistry) {
        this.context = context;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        this.eventsRouted = meterRegistry.counter("honeycomb.events.routed");
        this.eventsDropped = meterRegistry.counter("honeycomb.events.dropped");

        // ─── Phase 1: Discovery ─────────────────────────────────
        // Scan all Spring beans (not just @Cell) for @CellEventListener methods.
        // AopUtils.getTargetClass() is used to see through Spring proxies.
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                @SuppressWarnings("null")
                Object bean = context.getBean(beanName);
                Class<?> cls = AopUtils.getTargetClass(bean);
                for (Method m : cls.getDeclaredMethods()) {
                    CellEventListener ann = m.getAnnotation(CellEventListener.class);
                    if (ann == null) continue;
                    // Validate method signature: must accept exactly one CellEvent parameter
                    if (m.getParameterCount() != 1 || !CellEvent.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        log.warn("@CellEventListener method {}.{} must accept a single CellEvent parameter — skipping",
                                cls.getSimpleName(), m.getName());
                        continue;
                    }
                    m.setAccessible(true);
                    HandlerEntry entry = new HandlerEntry(bean, m, ann.fromCells(), ann.order());

                    String[] types = ann.value();
                    if (types.length == 0) {
                        // No type filter → wildcard handler, receives all events
                        wildcardHandlers.add(entry);
                        log.debug("Registered wildcard event listener: {}.{}", cls.getSimpleName(), m.getName());
                    } else {
                        // Register handler under each specified event type for O(1) lookup
                        for (String type : types) {
                            handlers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>()))
                                    .add(entry);
                            log.debug("Registered event listener for type '{}': {}.{}", type, cls.getSimpleName(), m.getName());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // ─── Phase 2: Sort by priority ──────────────────────────
        // Lower order values run first (same pattern as Spring @Order)
        wildcardHandlers.sort(Comparator.comparingInt(HandlerEntry::order));
        handlers.values().forEach(list -> list.sort(Comparator.comparingInt(HandlerEntry::order)));

        log.info("CellEventListenerRegistry initialized: {} typed handlers, {} wildcard handlers",
                handlers.values().stream().mapToInt(List::size).sum(), wildcardHandlers.size());

        // ─── Phase 3: Subscribe to event bus ────────────────────
        // Subscribe to the global event stream and route each event to matching handlers.
        // Uses boundedElastic scheduler to avoid blocking the event loop.
        eventPublisher.subscribe()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(this::routeEvent)
                .subscribe();
    }

    /**
     * Routes an event to all matching handlers (typed + wildcard).
     * Handlers are filtered by source cell before invocation.
     * All matching handlers execute in parallel via {@code Mono.when()}.
     */
    private Mono<Void> routeEvent(CellEvent event) {
        // Merge typed handlers (exact match on event type) with wildcard handlers
        List<HandlerEntry> typed = handlers.getOrDefault(event.type(), List.of());
        List<HandlerEntry> all = new ArrayList<>(typed.size() + wildcardHandlers.size());
        all.addAll(typed);
        all.addAll(wildcardHandlers);

        if (all.isEmpty()) {
            eventsDropped.increment();  // no handler registered for this event type
            return Mono.empty();
        }

        eventsRouted.increment();
        // Filter by fromCells() and invoke all matching handlers in parallel
        List<Mono<Void>> invocations = all.stream()
                .filter(h -> h.matchesSource(event.sourceCell()))
                .map(h -> invokeHandler(h, event))
                .toList();

        return Mono.when(invocations)
                .doOnError(ex -> log.error("Error routing event {}: {}", event.type(), ex.getMessage()));
    }

    /**
     * Invokes a single handler method via reflection.
     * Supports void, Mono, and Publisher return types.
     */
    private Mono<Void> invokeHandler(HandlerEntry handler, CellEvent event) {
        return Mono.defer(() -> {
            try {
                Object result = handler.method.invoke(handler.bean, event);
                if (result instanceof Mono<?> mono) {
                    return mono.then();
                }
                if (result instanceof Publisher<?> pub) {
                    return Mono.from(pub).then();
                }
                return Mono.empty();
            } catch (Exception ex) {
                log.error("Error invoking event handler {}.{}: {}",
                        handler.bean.getClass().getSimpleName(), handler.method.getName(), ex.getMessage());
                return Mono.empty();
            }
        });
    }

    /**
     * Compact handler descriptor holding the bean instance, method, source-cell filter,
     * and ordering priority. Used for both typed and wildcard handler lists.
     */
    private record HandlerEntry(Object bean, Method method, String[] fromCells, int order) {
        /** Returns true if this handler should process events from the given source cell. */
        boolean matchesSource(String sourceCell) {
            if (fromCells == null || fromCells.length == 0) return true;
            for (String cell : fromCells) {
                if ("*".equals(cell) || cell.equalsIgnoreCase(sourceCell)) return true;
            }
            return false;
        }
    }
}
