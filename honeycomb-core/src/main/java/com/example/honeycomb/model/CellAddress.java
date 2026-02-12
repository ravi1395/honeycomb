package com.example.honeycomb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CellAddress {
    private Long id;
    private String cellName;
    private String host;
    private Integer port;
}
