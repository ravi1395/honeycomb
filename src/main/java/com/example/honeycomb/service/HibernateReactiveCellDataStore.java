package com.example.honeycomb.service;

import com.example.honeycomb.persistence.CellRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.example.honeycomb.util.HoneycombConstants;

public class HibernateReactiveCellDataStore implements CellDataStore {
    private static final TypeReference<Map<String,Object>> MAP_TYPE = new TypeReference<>() {};

    private final Mutiny.SessionFactory sessionFactory;
    private final ObjectMapper objectMapper;

    public HibernateReactiveCellDataStore(Mutiny.SessionFactory sessionFactory, ObjectMapper objectMapper) {
        this.sessionFactory = sessionFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<Map<String,Object>> list(String cell) {
        Uni<List<CellRecord>> uni = sessionFactory.withSession(session ->
            session.createQuery(HoneycombConstants.Persistence.HQL_FIND_BY_CELL, CellRecord.class)
                .setParameter(HoneycombConstants.Persistence.PARAM_CELL, cell)
                .getResultList());
        return monoFromUni(uni)
                .flatMapMany(records -> Flux.fromIterable(records)
                        .flatMap(record -> deserialize(record.getPayloadJson())));
    }

    @Override
    public Mono<Map<String,Object>> get(String cell, String id) {
        String recordKey = key(cell, id);
        Uni<CellRecord> uni = sessionFactory.withSession(session -> session.find(CellRecord.class, recordKey));
        return monoFromUni(uni)
                .flatMap(record -> record == null ? Mono.empty() : deserialize(record.getPayloadJson()));
    }

    @Override
    public Mono<Map<String,Object>> create(String cell, Map<String,Object> payload) {
        String id = Optional.ofNullable(payload).map(p -> (String) p.get(HoneycombConstants.JsonKeys.ID))
                .orElse(UUID.randomUUID().toString());
        return Mono.fromCallable(() -> serializePayloadSync(id, payload))
            .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> monoFromUni(sessionFactory.withTransaction((session, tx) -> {
                    CellRecord record = new CellRecord(key(cell, id), cell, id, json);
                    return session.persist(record).replaceWith(record);
                })).flatMap(record -> deserialize(record.getPayloadJson())));
    }

    @Override
    public Mono<Map<String,Object>> update(String cell, String id, Map<String,Object> payload) {
        String recordKey = key(cell, id);
        return Mono.fromCallable(() -> serializePayloadSync(id, payload))
            .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> monoFromUni(sessionFactory.withTransaction((session, tx) ->
                        session.find(CellRecord.class, recordKey)
                                .chain(existing -> {
                                    if (existing == null) {
                                        return Uni.createFrom().nullItem();
                                    }
                                    existing.setPayloadJson(json);
                                    existing.setCellName(cell);
                                    existing.setItemId(id);
                                    return session.merge(existing);
                                })
                ))).flatMap(record -> record == null ? Mono.empty() : deserialize(record.getPayloadJson()));
    }

    @Override
    public Mono<Boolean> delete(String cell, String id) {
        String recordKey = key(cell, id);
        Uni<Boolean> uni = sessionFactory.withTransaction((session, tx) ->
                session.find(CellRecord.class, recordKey)
                        .chain(existing -> {
                            if (existing == null) {
                                return Uni.createFrom().item(false);
                            }
                            return session.remove(existing).replaceWith(true);
                        }));
        return monoFromUni(uni);
    }

    private String key(String cell, String id) {
        return cell + HoneycombConstants.Names.SEPARATOR_COLON + id;
    }

    private Mono<Map<String,Object>> deserialize(String json) {
        if (json == null || json.isBlank()) return Mono.empty();
        return Mono.fromCallable(() -> objectMapper.readValue(json, MAP_TYPE))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private String serializePayloadSync(String id, Map<String,Object> payload) throws Exception {
        Map<String,Object> copy = payload == null ? Map.of(HoneycombConstants.JsonKeys.ID, id) : new java.util.HashMap<>(payload);
        copy.put(HoneycombConstants.JsonKeys.ID, id);
        return objectMapper.writeValueAsString(copy);
    }

    private <T> Mono<T> monoFromUni(Uni<T> uni) {
        return Mono.fromCompletionStage(uni.subscribeAsCompletionStage());
    }
}
