package com.kpi.studentcouncil.crmtest.shared;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Registers the test entity/repository/controller and a throw-away schema (Hibernate create-drop, set by the tests). */
@TestConfiguration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = KernelTestConfig.class)
@EnableJpaRepositories(basePackageClasses = KernelTestConfig.class)
@EnableMethodSecurity
@Import(TestApiController.class)
public class KernelTestConfig {
}
