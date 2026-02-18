package com.honeycomb.tomcat;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Base class for deploying a Honeycomb cell as a WAR to an external Tomcat server.
 *
 * <h2>Usage</h2>
 * <p>In your cell application, create a class that extends this one:</p>
 * <pre>{@code
 * @SpringBootApplication
 * public class SampleModelApplication extends HoneycombServletInitializer {
 *
 *     public static void main(String[] args) {
 *         SpringApplication.run(SampleModelApplication.class, args);
 *     }
 *
 *     @Override
 *     protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
 *         return builder.sources(SampleModelApplication.class);
 *     }
 * }
 * }</pre>
 *
 * <h2>WAR packaging</h2>
 * <p>In your cell's {@code pom.xml} set {@code <packaging>war</packaging>} and mark
 * {@code spring-boot-starter-tomcat} as {@code provided}:</p>
 * <pre>{@code
 * <packaging>war</packaging>
 * <dependencies>
 *     <dependency>
 *         <groupId>com.honeycomb</groupId>
 *         <artifactId>honeycomb-tomcat</artifactId>
 *         <version>...</version>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-tomcat</artifactId>
 *         <scope>provided</scope>
 *     </dependency>
 * </dependencies>
 * }</pre>
 *
 * <h2>Cell naming</h2>
 * <p>When the WAR is deployed to Tomcat with the name {@code SampleModel.war},
 * Tomcat assigns the context path {@code /SampleModel}.
 * {@link HoneycombTomcatAutoConfiguration} reads this context path and automatically
 * sets {@code honeycomb.cell.name=SampleModel}, so you do not need to configure it
 * explicitly.</p>
 *
 * <h2>Multi-cell deployment</h2>
 * <p>Deploy multiple WARs to the same Tomcat instance; each gets its own context path
 * and cell name. Use {@code scripts/deploy-to-tomcat.sh} to automate the process.
 * This replaces the previous {@code run-multi-cells.sh} multi-JVM approach.</p>
 *
 * @see HoneycombTomcatAutoConfiguration
 */
public abstract class HoneycombServletInitializer extends SpringBootServletInitializer {

    /**
     * Subclasses must override this to provide the primary Spring Boot application source:
     * <pre>{@code
     * @Override
     * protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
     *     return builder.sources(MyApplication.class);
     * }
     * }</pre>
     */
    @Override
    protected abstract SpringApplicationBuilder configure(SpringApplicationBuilder builder);
}
