package com.honeycomb.core.web;

import com.honeycomb.core.locking.DistributedLock;
import com.honeycomb.core.locking.LeaderElectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for distributed lock and leader election management.
 *
 * <p>Active only when {@code honeycomb.locking.enabled=true}.</p>
 *
 * @since 1.4.2
 */
@RestController
@RequestMapping("/honeycomb/locking")
@ConditionalOnProperty(name = "honeycomb.locking.enabled", havingValue = "true")
@Tag(name = "Distributed Locking", description = "Distributed lock management and leader election status")
public class DistributedLockController {

    private final DistributedLock lock;
    private final ObjectProvider<LeaderElectionService> leaderElectionProvider;

    public DistributedLockController(DistributedLock lock,
                                     ObjectProvider<LeaderElectionService> leaderElectionProvider) {
        this.lock = lock;
        this.leaderElectionProvider = leaderElectionProvider;
    }

    @GetMapping("/leader")
    @Operation(summary = "Get leader election status")
    public Mono<ResponseEntity<Map<String, Object>>> leaderStatus() {
        LeaderElectionService les = leaderElectionProvider.getIfAvailable();
        if (les == null) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "leaderElection", "disabled"
            )));
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "isLeader", les.isLeader(),
                "instanceId", les.getInstanceId(),
                "leaderElection", "enabled"
        )));
    }

    @PostMapping("/leader/relinquish")
    @Operation(summary = "Voluntarily give up leadership")
    public Mono<ResponseEntity<Map<String, Object>>> relinquish() {
        LeaderElectionService les = leaderElectionProvider.getIfAvailable();
        if (les == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "Leader election is not enabled"
            )));
        }
        return les.relinquish()
                .map(released -> ResponseEntity.ok(Map.<String, Object>of(
                        "released", released,
                        "instanceId", les.getInstanceId()
                )));
    }

    @PostMapping("/acquire")
    @Operation(summary = "Acquire a distributed lock",
               description = "Acquire a named lock with optional TTL in seconds")
    public Mono<ResponseEntity<Map<String, Object>>> acquire(
            @RequestParam String key,
            @RequestParam(defaultValue = "30") int ttlSeconds) {
        String owner = UUID.randomUUID().toString();
        return lock.tryAcquire(key, owner, Duration.ofSeconds(ttlSeconds))
                .map(acquired -> ResponseEntity.ok(Map.<String, Object>of(
                        "key", key,
                        "acquired", acquired,
                        "owner", owner,
                        "ttlSeconds", ttlSeconds
                )));
    }

    @PostMapping("/release")
    @Operation(summary = "Release a distributed lock")
    public Mono<ResponseEntity<Map<String, Object>>> release(
            @RequestParam String key,
            @RequestParam String owner) {
        return lock.release(key, owner)
                .map(released -> ResponseEntity.ok(Map.<String, Object>of(
                        "key", key,
                        "released", released,
                        "owner", owner
                )));
    }

    @GetMapping("/status")
    @Operation(summary = "Check if a lock is held")
    public Mono<ResponseEntity<Map<String, Object>>> lockStatus(@RequestParam String key) {
        return lock.isLocked(key)
                .map(locked -> ResponseEntity.ok(Map.<String, Object>of(
                        "key", key,
                        "locked", locked
                )));
    }
}
