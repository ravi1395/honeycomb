package com.example.honeycomb.examples;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Map;

@Cell
@Component
public class ExampleSharedService {

    @Sharedwall(value = HoneycombConstants.Examples.SHARED_ECHO,
            allowedFrom = {HoneycombConstants.Examples.SHARED_TEST_CLIENT})
    public Mono<String> echo(String input) {
        return Mono.just(HoneycombConstants.Examples.ECHO_PREFIX + input);
    }

    @Sharedwall
    public Mono<Map<String,Object>> summarize(Map<String,Object> payload) {
        return Mono.just(Map.of(
                HoneycombConstants.Examples.RECEIVED_KEYS, payload == null ? 0 : payload.keySet().size(),
                HoneycombConstants.Examples.ORIGINAL, payload
        ));
    }

    @Sharedwall(HoneycombConstants.Examples.SHARED_CONCAT)
    public Mono<String> concat(String a, String b) {
        return Mono.just(a + HoneycombConstants.Names.SEPARATOR_COLON + b);
    }

    @Sharedwall(HoneycombConstants.Examples.SHARED_SUM_LIST)
    public Mono<Integer> sumList(java.util.List<Integer> nums) {
        if (nums == null) return Mono.just(0);
        return Mono.just(nums.stream().mapToInt(Integer::intValue).sum());
    }

    @Sharedwall(HoneycombConstants.Examples.SHARED_BOOM)
    public Mono<Void> boom(String in) {
        if (HoneycombConstants.Examples.BOOM.equals(in)) {
            return Mono.error(new RuntimeException(HoneycombConstants.Examples.BOOM_EXCEPTION));
        }
        return Mono.empty();
    }
}
