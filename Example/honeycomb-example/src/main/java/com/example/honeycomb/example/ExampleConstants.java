package com.example.honeycomb.example;

public final class ExampleConstants {
    private ExampleConstants() {}

    public static final class Cells {
        private Cells() {}
        public static final String INVENTORY = "InventoryCell";
        public static final String PRICING = "PricingCell";
    }

    public static final class Shared {
        private Shared() {}
        public static final String PRICE = "price";
        public static final String DISCOUNT = "discount";
        public static final String PING = "ping";
        public static final String DEMO_CALLER = "demo-client";
    }

    public static final class JsonKeys {
        private JsonKeys() {}
        public static final String ID = "id";
        public static final String SKU = "sku";
        public static final String QUANTITY = "quantity";
        public static final String WAREHOUSE = "warehouse";
        public static final String LIST_PRICE = "listPrice";
        public static final String TAX_RATE = "taxRate";
        public static final String TOTAL = "total";
        public static final String DISCOUNT_PCT = "discountPct";
        public static final String DISCOUNTED = "discounted";
        public static final String CURRENCY = "currency";
    }

    public static final class Values {
        private Values() {}
        public static final String USD = "USD";
        public static final String PONG = "pong";
        public static final String ROUTE_ITEMS = "items";
        public static final String ROUTE_DISCOUNT = "discount";
        public static final String DEFAULT_PORT = "8080";
        public static final String SAMPLE_INVENTORY_ID = "inv-100";
        public static final String SAMPLE_SKU = "SKU-RED-1";
        public static final String SAMPLE_WAREHOUSE = "west-1";
    }

    public static final class PropertyValues {
        private PropertyValues() {}
        public static final String SERVER_PORT = "${server.port:8080}";
        public static final String API_KEY_HEADER = "${honeycomb.security.api-keys.header:X-API-Key}";
        public static final String ADMIN_KEY = "${honeycomb.security.api-keys.keys.admin:admin-key}";
        public static final String SHARED_USER = "${shared.user:shared}";
        public static final String SHARED_PASSWORD = "${shared.password:changeit}";
        public static final String CALLER_HEADER = "${shared.caller-header:X-From-Cell}";
        public static final String OAUTH2_REGISTRATION_ID = "${example.oauth2.registration-id:}";
    }

    public static final class Hosts {
        private Hosts() {}
        public static final String LOCALHOST = "localhost";
    }

    public static final class Query {
        private Query() {}
        public static final String POLICY_ROUND_ROBIN = "round-robin";
    }

    public static final class Packages {
        private Packages() {}
        public static final String HONEYCOMB = "com.example.honeycomb";
        public static final String HONEYCOMB_EXAMPLE = "com.example.honeycomb.example";
    }

    public static final class AutoConfig {
        private AutoConfig() {}
        public static final String SIMPLE_REACTIVE_DISCOVERY =
                "org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration";
        public static final String COMPOSITE_REACTIVE_DISCOVERY =
                "org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration";
    }

    public static final class PropertyKeys {
        private PropertyKeys() {}
        public static final String LOCAL_SERVER_PORT = "local.server.port";
        public static final String SERVER_PORT = "server.port";
    }

    public static final class Messages {
        private Messages() {}
        public static final String BOOT_STARTED = "Spring Boot started on port {}";
        public static final String DEMO_RUNNER_FAILED = "demo runner failed";
        public static final String LOG_MODELS = "models: {}";
        public static final String LOG_DESCRIBE = "describe {}: {}";
        public static final String LOG_CREATE_INVENTORY = "create inventory: {}";
        public static final String LOG_INVENTORY_ITEMS = "inventory items: {}";
        public static final String LOG_SHARED_DISCOUNT = "shared discount: {}";
        public static final String LOG_SHARED_DISCOUNT_UTIL = "shared discount (util): {}";
        public static final String LOG_CELLS_DISCOUNT = "cells invoke discount: {}";
        public static final String LOG_METRICS = "metrics: {}";
        public static final String LOG_AUDIT = "audit: {}";
    }

    public static final class Discovery {
        private Discovery() {}
        public static final String NOOP_DESCRIPTION = "noop-reactive-discovery-client";
    }
}
