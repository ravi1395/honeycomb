package com.honeycomb.core.examples;

import com.honeycomb.core.annotations.Cell;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Cell(port = 8081)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleModel {
    private String id;
    private String name;
    private int value;
}
