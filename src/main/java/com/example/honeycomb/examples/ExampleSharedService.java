package com.example.honeycomb.examples;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import org.springframework.stereotype.Component;

import java.util.Map;

@Cell
@Component
public class ExampleSharedService {

    @Sharedwall("echo")
    public String echo(String input) {
        return "echo:" + input;
    }

    @Sharedwall
    public Map<String,Object> summarize(Map<String,Object> payload) {
        return Map.of(
                "receivedKeys", payload == null ? 0 : payload.keySet().size(),
                "original", payload
        );
    }
}
