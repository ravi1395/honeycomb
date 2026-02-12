package com.example.honeycomb.service;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.model.CellAddress;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CellAddressService {
    private final CellRegistry registry;
    private final Environment env;
    private final ReactiveDiscoveryClient discoveryClient;

    @Value(HoneycombConstants.PropertyValues.SERVICE_DISCOVERY_BASE_URL)
    private String baseUrl;

    public CellAddressService(CellRegistry registry, Environment env, ReactiveDiscoveryClient discoveryClient) {
        this.registry = registry;
        this.env = env;
        this.discoveryClient = discoveryClient;
    }

    /**
     * List all discovered cell addresses based on configured ports.
     */
    public Flux<CellAddress> listAll() {
        return Flux.fromIterable(registry.getCellNames())
                .flatMap(this::findByCell)
                .sort(Comparator.comparing(CellAddress::getCellName));
    }

    /**
     * Discovery registry is derived from configuration; lookup by id is not supported.
     */
    public Mono<CellAddress> get(Long id) {
        return Mono.empty();
    }

    public Flux<CellAddress> findByCell(String name) {
        return discoveryClient.getInstances(name)
                .map(instance -> new CellAddress(null, name, instance.getHost(), instance.getPort()))
                .switchIfEmpty(Flux.defer(() -> Flux.fromIterable(addressesForCell(name))));
    }

    /**
     * Persistence operations are no-ops with discovery-based addresses.
     */
    public Mono<CellAddress> create(CellAddress a) {
        return Mono.error(new UnsupportedOperationException(HoneycombConstants.Messages.CELL_ADDRESS_DISCOVERY_BASED));
    }

    public Mono<CellAddress> update(Long id, CellAddress a) {
        return Mono.error(new UnsupportedOperationException(HoneycombConstants.Messages.CELL_ADDRESS_DISCOVERY_BASED));
    }

    public Mono<Void> delete(Long id) {
        return Mono.error(new UnsupportedOperationException(HoneycombConstants.Messages.CELL_ADDRESS_DISCOVERY_BASED));
    }

    private List<CellAddress> addressesForCell(String name) {
        Optional<Class<?>> cellClass = registry.getCellClass(name);
        if (cellClass.isEmpty()) return List.of();
        Class<?> cls = cellClass.get();
        Cell ann = cls.getAnnotation(Cell.class);
        int annotatedPort = ann != null ? ann.port() : -1;
        String portProp = env.getProperty(HoneycombConstants.ConfigKeys.CELL_PORTS_PREFIX + name);
        List<Integer> ports = new ArrayList<>();
        if (portProp != null && !portProp.isBlank()) {
            for (String p : portProp.split(HoneycombConstants.Names.SEPARATOR_COMMA)) {
                try {
                    int v = Integer.parseInt(p.trim());
                    if (v > 0) ports.add(v);
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (annotatedPort > 0) {
            ports.add(annotatedPort);
        }

        String addressesProp = env.getProperty(HoneycombConstants.ConfigKeys.CELL_ADDRESSES_PREFIX + name);
        if (addressesProp != null && !addressesProp.isBlank()) {
            return parseAddressList(name, addressesProp);
        }

        String host = resolveBaseHost();
        List<CellAddress> addresses = new ArrayList<>();
        for (int port : ports) {
            CellAddress addr = new CellAddress(null, name, host, port);
            addresses.add(addr);
        }
        return addresses;
    }

    private List<CellAddress> parseAddressList(String name, String value) {
        List<CellAddress> addresses = new ArrayList<>();
        for (String token : value.split(HoneycombConstants.Names.SEPARATOR_COMMA)) {
            String entry = token.trim();
            if (entry.isBlank()) continue;
            String host = null;
            int port = -1;
            try {
                URI uri = entry.contains(HoneycombConstants.Regex.PROTOCOL_SEPARATOR)
                    ? URI.create(entry)
                    : URI.create(HoneycombConstants.Schemes.HTTP + entry);
                host = uri.getHost();
                port = uri.getPort();
            } catch (IllegalArgumentException ignored) {
            }
            if (host == null || host.isBlank()) {
                String raw = entry.replaceFirst(HoneycombConstants.Regex.HTTP_PREFIX, "");
                int slash = raw.indexOf('/');
                if (slash > 0) raw = raw.substring(0, slash);
                int colon = raw.indexOf(':');
                if (colon > 0) {
                    host = raw.substring(0, colon);
                    try {
                        port = Integer.parseInt(raw.substring(colon + 1));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    host = raw;
                }
            }
            if (host != null && !host.isBlank() && port > 0) {
                addresses.add(new CellAddress(null, name, host, port));
            }
        }
        return addresses;
    }

    private String resolveBaseHost() {
            if (baseUrl == null || baseUrl.isBlank()) return HoneycombConstants.Hosts.LOCALHOST;
        try {
                URI uri = baseUrl.contains(HoneycombConstants.Regex.PROTOCOL_SEPARATOR)
                        ? URI.create(baseUrl)
                        : URI.create(HoneycombConstants.Schemes.HTTP + baseUrl);
            if (uri.getHost() != null && !uri.getHost().isBlank()) return uri.getHost();
        } catch (IllegalArgumentException ignored) {
        }
        String candidate = baseUrl.replaceFirst(HoneycombConstants.Regex.HTTP_PREFIX, "");
        int slash = candidate.indexOf('/');
        if (slash > 0) candidate = candidate.substring(0, slash);
        int colon = candidate.indexOf(':');
        if (colon > 0) candidate = candidate.substring(0, colon);
        return candidate.isBlank() ? HoneycombConstants.Hosts.LOCALHOST : candidate;
    }
}
