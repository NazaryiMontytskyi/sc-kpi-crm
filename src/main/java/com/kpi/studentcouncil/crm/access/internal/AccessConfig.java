package com.kpi.studentcouncil.crm.access.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Enables {@code @PreAuthorize}/{@code @PostAuthorize}; permission keys are the granted authorities. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class AccessConfig {

}
