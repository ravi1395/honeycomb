package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.provider.ReactivePersistenceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnClass(Mutiny.SessionFactory.class)
@ConditionalOnExpression(HoneycombConstants.ConfigExpressions.STORAGE_HIBERNATE_ENABLED)
public class HibernateReactiveConfig {
    @Bean(destroyMethod = "close")
    public Mutiny.SessionFactory reactiveSessionFactory(HoneycombStorageProperties storageProperties) {
        HoneycombStorageProperties.Hibernate h = storageProperties.getHibernate();
        Map<String,Object> settings = new HashMap<>();
        settings.put(HoneycombConstants.HibernateConfigKeys.CONNECTION_URL, h.getUrl());
        settings.put(HoneycombConstants.HibernateConfigKeys.CONNECTION_USERNAME, h.getUsername());
        settings.put(HoneycombConstants.HibernateConfigKeys.CONNECTION_PASSWORD, h.getPassword());
        settings.put(HoneycombConstants.HibernateConfigKeys.DIALECT, h.getDialect());
        settings.put(HoneycombConstants.HibernateConfigKeys.HBM2DDL, h.getHbm2ddlAuto());
        settings.put(HoneycombConstants.HibernateConfigKeys.SHOW_SQL, String.valueOf(h.isShowSql()));
        settings.put(HoneycombConstants.HibernateConfigKeys.FORMAT_SQL, HoneycombConstants.StorageDefaults.HIBERNATE_FORMAT_SQL);
        settings.put(HoneycombConstants.HibernateConfigKeys.POOL_SIZE, String.valueOf(h.getPoolSize()));
        settings.put(HoneycombConstants.HibernateConfigKeys.ARCHIVE_AUTODETECTION,
            HoneycombConstants.StorageDefaults.HIBERNATE_ARCHIVE_AUTODETECTION);

        EntityManagerFactory emf = new ReactivePersistenceProvider()
                .createEntityManagerFactory(HoneycombConstants.StorageDefaults.PERSISTENCE_UNIT, settings);
        return emf.unwrap(Mutiny.SessionFactory.class);
    }
}
