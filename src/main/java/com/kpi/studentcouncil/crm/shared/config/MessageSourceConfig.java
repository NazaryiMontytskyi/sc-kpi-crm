package com.kpi.studentcouncil.crm.shared.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Backend i18n: {@code messages_uk.properties} and {@code messages_en.properties}. Declared explicitly so the
 * JVM default locale is never used as a fallback (the product default is {@code uk}, see KernelLocaleResolver).
 */
@Configuration(proxyBeanMethods = false)
public class MessageSourceConfig {

	@Bean
	MessageSource messageSource() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding(StandardCharsets.UTF_8.name());
		source.setFallbackToSystemLocale(false);
		return source;
	}

}
