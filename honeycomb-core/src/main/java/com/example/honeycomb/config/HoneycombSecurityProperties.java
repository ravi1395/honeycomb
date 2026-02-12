package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = HoneycombConstants.ConfigKeys.SECURITY_PREFIX)
public class HoneycombSecurityProperties {
    private ApiKeys apiKeys = new ApiKeys();
    private Jwt jwt = new Jwt();
    private Mtls mtls = new Mtls();

    /**
     * If true, require authentication for /honeycomb/** requests.
     */
    private boolean requireAuth = false;

    public ApiKeys getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(ApiKeys apiKeys) {
        this.apiKeys = apiKeys;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Mtls getMtls() {
        return mtls;
    }

    public void setMtls(Mtls mtls) {
        this.mtls = mtls;
    }

    public boolean isRequireAuth() {
        return requireAuth;
    }

    public void setRequireAuth(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public static class ApiKeys {
        private boolean enabled = false;
        private String header = HoneycombConstants.Headers.API_KEY;
        private Map<String, String> keys = new HashMap<>();
        private Map<String, List<String>> perCell = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public Map<String, String> getKeys() {
            return keys;
        }

        public void setKeys(Map<String, String> keys) {
            this.keys = keys;
        }

        public Map<String, List<String>> getPerCell() {
            return perCell;
        }

        public void setPerCell(Map<String, List<String>> perCell) {
            this.perCell = perCell;
        }

        public List<String> resolveAllowedKeys(String cellName) {
            if (cellName == null || cellName.isBlank()) {
                return List.of();
            }
            List<String> direct = perCell.get(cellName);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
            List<String> fallback = perCell.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
            if (fallback == null) fallback = perCell.get(HoneycombConstants.ConfigKeys.GLOBAL_ALL);
            return fallback == null ? List.of() : fallback;
        }

        public boolean isKnownKey(String value) {
            if (value == null || value.isBlank()) return false;
            return keys.values().stream().anyMatch(value::equals);
        }

        public String resolveKeyName(String value) {
            if (value == null) return HoneycombConstants.Messages.EMPTY;
            return keys.entrySet().stream()
                    .filter(e -> value.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(com.example.honeycomb.util.HoneycombConstants.Messages.UNKNOWN);
        }

        public List<String> allKeys() {
            return new ArrayList<>(keys.values());
        }
    }

    public static class Jwt {
        private boolean enabled = false;
        private String issuerUri;
        private String jwkSetUri;
        private String audience;
        private String rolesClaim = HoneycombConstants.SecurityDefaults.ROLES_CLAIM;
        private String rolePrefix = HoneycombConstants.SecurityDefaults.ROLE_PREFIX;
        private String scopesClaim = HoneycombConstants.SecurityDefaults.SCOPES_CLAIM;
        private String scopePrefix = HoneycombConstants.SecurityDefaults.SCOPE_PREFIX;
        private String sharedRolesClaim = HoneycombConstants.SecurityDefaults.SHARED_ROLES_CLAIM;
        private String sharedRolePrefix = HoneycombConstants.SecurityDefaults.SHARED_ROLE_PREFIX;
        private java.util.List<String> defaultRoles = new java.util.ArrayList<>();
        private java.util.Map<String, java.util.List<String>> perCellRoles = new java.util.HashMap<>();
        private java.util.Map<String, java.util.Map<String, java.util.List<String>>> perCellOperationRoles = new java.util.HashMap<>();
        private java.util.Map<String, java.util.List<String>> sharedMethodRoles = new java.util.HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getRolePrefix() {
            return rolePrefix;
        }

        public void setRolePrefix(String rolePrefix) {
            this.rolePrefix = rolePrefix;
        }

        public String getScopesClaim() {
            return scopesClaim;
        }

        public void setScopesClaim(String scopesClaim) {
            this.scopesClaim = scopesClaim;
        }

        public String getScopePrefix() {
            return scopePrefix;
        }

        public void setScopePrefix(String scopePrefix) {
            this.scopePrefix = scopePrefix;
        }

        public String getSharedRolesClaim() {
            return sharedRolesClaim;
        }

        public void setSharedRolesClaim(String sharedRolesClaim) {
            this.sharedRolesClaim = sharedRolesClaim;
        }

        public String getSharedRolePrefix() {
            return sharedRolePrefix;
        }

        public void setSharedRolePrefix(String sharedRolePrefix) {
            this.sharedRolePrefix = sharedRolePrefix;
        }

        public java.util.List<String> getDefaultRoles() {
            return defaultRoles;
        }

        public void setDefaultRoles(java.util.List<String> defaultRoles) {
            this.defaultRoles = defaultRoles;
        }

        public java.util.Map<String, java.util.List<String>> getPerCellRoles() {
            return perCellRoles;
        }

        public void setPerCellRoles(java.util.Map<String, java.util.List<String>> perCellRoles) {
            this.perCellRoles = perCellRoles;
        }

        public java.util.Map<String, java.util.Map<String, java.util.List<String>>> getPerCellOperationRoles() {
            return perCellOperationRoles;
        }

        public void setPerCellOperationRoles(java.util.Map<String, java.util.Map<String, java.util.List<String>>> perCellOperationRoles) {
            this.perCellOperationRoles = perCellOperationRoles;
        }

        public java.util.Map<String, java.util.List<String>> getSharedMethodRoles() {
            return sharedMethodRoles;
        }

        public void setSharedMethodRoles(java.util.Map<String, java.util.List<String>> sharedMethodRoles) {
            this.sharedMethodRoles = sharedMethodRoles;
        }

        public java.util.List<String> resolveRequiredRoles(String cell) {
            if (cell != null && perCellRoles != null) {
                java.util.List<String> direct = perCellRoles.get(cell);
                if (direct != null && !direct.isEmpty()) return direct;
                java.util.List<String> wildcard = perCellRoles.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
                if (wildcard != null && !wildcard.isEmpty()) return wildcard;
            }
            return defaultRoles == null ? java.util.List.of() : defaultRoles;
        }

        public java.util.List<String> resolveRequiredRoles(String cell, String operation) {
            if (cell != null && operation != null && perCellOperationRoles != null) {
                var cellMap = perCellOperationRoles.get(cell);
                if (cellMap == null) cellMap = perCellOperationRoles.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
                if (cellMap != null) {
                    var opRoles = cellMap.get(operation);
                    if (opRoles != null && !opRoles.isEmpty()) return opRoles;
                }
            }
            return resolveRequiredRoles(cell);
        }

        public java.util.List<String> resolveSharedMethodRoles(String method) {
            if (method != null && sharedMethodRoles != null) {
                java.util.List<String> direct = sharedMethodRoles.get(method);
                if (direct != null && !direct.isEmpty()) return direct;
                java.util.List<String> wildcard = sharedMethodRoles.get(HoneycombConstants.ConfigKeys.GLOBAL_WILDCARD);
                if (wildcard != null && !wildcard.isEmpty()) return wildcard;
            }
            return java.util.List.of();
        }
    }

    public static class Mtls {
        private boolean enabled = false;
        private boolean requireClientCert = false;
        private java.util.List<String> allowedSubjects = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireClientCert() {
            return requireClientCert;
        }

        public void setRequireClientCert(boolean requireClientCert) {
            this.requireClientCert = requireClientCert;
        }

        public java.util.List<String> getAllowedSubjects() {
            return allowedSubjects;
        }

        public void setAllowedSubjects(java.util.List<String> allowedSubjects) {
            this.allowedSubjects = allowedSubjects;
        }
    }
}
