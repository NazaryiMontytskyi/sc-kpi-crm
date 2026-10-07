package com.kpi.studentcouncil.crm.shared.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC conventions: {@code /api/v1} prefix for {@link ApiV1} controllers, locale resolution, localized validation. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final MessageSource messageSource;

	public WebConfig(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(ApiV1.PREFIX, type -> type.isAnnotationPresent(ApiV1.class));
	}

	/** Must be named {@code localeResolver} to be picked up by the DispatcherServlet. */
	@Bean
	LocaleResolver localeResolver(ObjectProvider<UserLocaleProvider> userLocale) {
		return new KernelLocaleResolver(userLocale);
	}

	@Override
	public Validator getValidator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.setValidationMessageSource(messageSource);
		return validator;
	}

}
