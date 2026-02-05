package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.STORAGE_PREFIX, ignoreInvalidFields = true)
public class HoneycombStorageProperties {
    /**
        * Supported values: memory, redis, hibernate
     */
    private String type = HoneycombConstants.Names.STORE_MEMORY;

    /**
     * Key prefix for redis-backed storage.
     */
    private String keyPrefix = com.example.honeycomb.util.HoneycombConstants.KeyPrefixes.CELL;

    /**
     * Hibernate Reactive settings.
     */
    private Hibernate hibernate = new Hibernate();

    /**
     * Per-cell routing settings.
     */
    private Routing routing = new Routing();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Hibernate getHibernate() {
        return hibernate;
    }

    public void setHibernate(Hibernate hibernate) {
        this.hibernate = hibernate;
    }

    public Routing getRouting() {
        return routing;
    }

    public void setRouting(Routing routing) {
        this.routing = routing;
    }

    public static class Hibernate {
        /**
         * Enable Hibernate Reactive SessionFactory when routing is used.
         */
        private boolean enabled = false;
        /**
         * Reactive connection URL, e.g. postgresql://localhost:5432/honeycomb
         */
        private String url = HoneycombConstants.StorageDefaults.HIBERNATE_URL;

        private String username = HoneycombConstants.StorageDefaults.HIBERNATE_USERNAME;

        private String password = HoneycombConstants.StorageDefaults.HIBERNATE_PASSWORD;

        private String dialect = HoneycombConstants.StorageDefaults.HIBERNATE_DIALECT;

        private String hbm2ddlAuto = HoneycombConstants.StorageDefaults.HIBERNATE_HBM2DDL;

        private boolean showSql = false;

        private int poolSize = 10;

        /**
         * If true, use the generic record table and do not require cell classes
         * to have Jakarta persistence annotations.
         */
        private boolean annotationFree = true;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDialect() {
            return dialect;
        }

        public void setDialect(String dialect) {
            this.dialect = dialect;
        }

        public String getHbm2ddlAuto() {
            return hbm2ddlAuto;
        }

        public void setHbm2ddlAuto(String hbm2ddlAuto) {
            this.hbm2ddlAuto = hbm2ddlAuto;
        }

        public boolean isShowSql() {
            return showSql;
        }

        public void setShowSql(boolean showSql) {
            this.showSql = showSql;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public boolean isAnnotationFree() {
            return annotationFree;
        }

        public void setAnnotationFree(boolean annotationFree) {
            this.annotationFree = annotationFree;
        }
    }

    public static class Routing {
        /**
         * Enable per-cell storage routing.
         */
        private boolean enabled = false;

        /**
         * Map of cell name -> storage type (memory | redis | hibernate)
         */
        private java.util.Map<String, String> perCell = new java.util.HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.util.Map<String, String> getPerCell() {
            return perCell;
        }

        public void setPerCell(java.util.Map<String, String> perCell) {
            this.perCell = perCell;
        }
    }
}
