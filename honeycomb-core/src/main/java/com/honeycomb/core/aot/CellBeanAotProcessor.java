package com.honeycomb.core.aot;

import com.honeycomb.core.annotations.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * AOT processor that discovers every {@code @Cell}-annotated bean at
 * build time and registers full reflection hints so that GraalVM native
 * images can instantiate, serialize, and invoke methods on user-defined cells.
 *
 * <p>This complements the static {@link HoneycombRuntimeHints} registrar
 * by handling <em>application-defined</em> cell classes that are unknown
 * to the framework at compile time.</p>
 *
 * @since 1.4.2
 */
public class CellBeanAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final Logger log = LoggerFactory.getLogger(CellBeanAotProcessor.class);

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            ConfigurableListableBeanFactory beanFactory) {

        String[] cellBeanNames = beanFactory.getBeanNamesForAnnotation(Cell.class);
        if (cellBeanNames.length == 0) {
            return null; // nothing to contribute
        }

        return (generationContext, beanFactoryInitializationCode) -> {
            RuntimeHints hints = generationContext.getRuntimeHints();
            for (String beanName : cellBeanNames) {
                Class<?> beanType = beanFactory.getType(beanName);
                if (beanType == null) continue;
                log.debug("Registering native-image hints for @Cell bean: {} ({})",
                        beanName, beanType.getName());
                hints.reflection().registerType(beanType, MemberCategory.values());
            }
        };
    }
}
