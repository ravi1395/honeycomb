package com.honeycomb.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable model representing a network address for a cell instance.
 * Used for static service discovery and inter-cell routing.
 *
 * @see com.honeycomb.core.service.CellAddressService
 * @see com.honeycomb.core.repo.CellAddressRepository
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CellAddress {
    private Long id;
    private String cellName;
    private String host;
    private Integer port;
}
