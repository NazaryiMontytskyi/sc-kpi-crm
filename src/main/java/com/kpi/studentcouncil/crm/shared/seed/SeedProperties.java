package com.kpi.studentcouncil.crm.shared.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Seed framework settings.
 *
 * @param enabled master switch ({@code crm.seed.enabled}); when false no seeder runs in any profile
 */
@ConfigurationProperties("crm.seed")
public record SeedProperties(@DefaultValue("true") boolean enabled) {
}
