package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombRoutingProperties;
import com.example.honeycomb.model.CellAddress;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RoutingPolicyService {
    private final HoneycombRoutingProperties props;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public RoutingPolicyService(HoneycombRoutingProperties props) {
        this.props = props;
    }

    public List<CellAddress> selectTargets(String cell, List<CellAddress> addresses, String policyOverride) {
        if (addresses == null || addresses.isEmpty()) return List.of();
        String policy = policyOverride == null || policyOverride.isBlank() ? props.resolvePolicy(cell) : policyOverride;
        String normalized = policy.toLowerCase();
        return switch (normalized) {
            case HoneycombConstants.RoutingPolicies.ONE, HoneycombConstants.RoutingPolicies.RANDOM
                    -> List.of(addresses.get(random.nextInt(addresses.size())));
            case HoneycombConstants.RoutingPolicies.ROUND_ROBIN -> List.of(roundRobin(cell, addresses));
            case HoneycombConstants.RoutingPolicies.WEIGHTED -> List.of(weighted(cell, addresses));
            case HoneycombConstants.RoutingPolicies.ALL -> addresses;
            default -> addresses;
        };
    }

    private CellAddress roundRobin(String cell, List<CellAddress> addresses) {
        AtomicInteger counter = counters.computeIfAbsent(
            cell == null ? HoneycombConstants.ConfigKeys.GLOBAL_ALL : cell,
            k -> new AtomicInteger()
        );
        int idx = Math.floorMod(counter.getAndIncrement(), addresses.size());
        return addresses.get(idx);
    }

    private CellAddress weighted(String cell, List<CellAddress> addresses) {
        Map<String, Integer> weights = props.resolveWeights(cell);
        List<CellAddress> expanded = new ArrayList<>();
        for (CellAddress addr : addresses) {
            String key = addr.getHost() + HoneycombConstants.Names.SEPARATOR_COLON + addr.getPort();
            int weight = Math.max(1, weights.getOrDefault(key, 1));
            for (int i = 0; i < weight; i++) {
                expanded.add(addr);
            }
        }
        if (expanded.isEmpty()) return addresses.get(0);
        return expanded.get(random.nextInt(expanded.size()));
    }
}
