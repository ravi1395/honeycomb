package com.honeycomb.core.examples;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.util.HoneycombConstants;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Built-in example cell bean that exposes {@link com.honeycomb.core.annotations.Sharedwall @Sharedwall}
 * methods for quick-start testing.
 *
 * <p>Provides {@code echo} and {@code info} shared methods out of the box.</p>
 */
@Cell
@Component
public class ExampleSharedService {

    @Sharedwall(value = HoneycombConstants.Examples.SHARED_ECHO,
            allowedFrom = {HoneycombConstants.Examples.SHARED_TEST_CLIENT})
    public Mono<String> echo(String input) {
        return Mono.just(HoneycombConstants.Examples.ECHO_PREFIX + input);
    }

    @Sharedwall(value = HoneycombConstants.Examples.SHARED_ECHO,
            version = "v2",
            allowedFrom = {HoneycombConstants.Examples.SHARED_TEST_CLIENT})
    public Mono<String> echoV2(String input) {
        return Mono.just("echo-v2:" + input);
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
