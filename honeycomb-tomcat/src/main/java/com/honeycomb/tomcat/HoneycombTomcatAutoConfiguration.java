package com.honeycomb.tomcat;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for the {@code honeycomb-tomcat} module.
 *
 * <h2>What this does</h2>
 * <ul>
 *   <li>Activates {@link HoneycombContextPathCellNameConfigurer} so that the Tomcat
 *       context path (derived from the WAR filename) is automatically used as the
 *       Honeycomb cell name.</li>
 *   <li>Works both in the embedded-Tomcat mode (regular {@code main} method) and when
 *       deployed as a WAR to an external Tomcat server.</li>
 * </ul>
 *
 * <h2>Multi-cell setup with one Tomcat instance</h2>
 * <ol>
 *   <li>Add {@code honeycomb-tomcat} as a dependency in your cell application.</li>
 *   <li>Extend {@link HoneycombServletInitializer} as your application class.</li>
 *   <li>Build a WAR: {@code mvn package -P war}</li>
 *   <li>Rename the WAR to the cell name: {@code mv target/myapp.war SampleModel.war}</li>
 *   <li>Copy the WAR to {@code $CATALINA_HOME/webapps/}.</li>
 *   <li>Repeat for each cell; all cells share the same Tomcat on different context paths.</li>
 *   <li>Or use {@code scripts/deploy-to-tomcat.sh} to automate steps 3–5.</li>
 * </ol>
 *
 * @see HoneycombServletInitializer
 * @see HoneycombContextPathCellNameConfigurer
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ComponentScan(basePackages = "com.honeycomb.tomcat")
public class HoneycombTomcatAutoConfiguration {
}
