package com.example.honeycomb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("domain_addresses")
public class DomainAddress {
    @Id
    private Long id;
    private String domainName;
    private String host;
    private Integer port;
}
