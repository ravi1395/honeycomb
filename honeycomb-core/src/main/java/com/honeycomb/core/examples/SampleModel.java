package com.honeycomb.core.examples;

import com.honeycomb.core.annotations.Cell;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Built-in example {@link com.honeycomb.core.annotations.Cell @Cell}
 * shipped with honeycomb-core for quick-start demos.
 *
 * <p>Listens on port 8081. Contains two fields: {@code name} and
 * {@code value}.</p>
 */
@Cell(port = 8081)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleModel {
    private String id;
    private String name;
    private int value;
}
