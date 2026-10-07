package com.kpi.studentcouncil.crm.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marks a REST controller of the public API: its mappings are automatically prefixed with {@code /api/v1}
 * (see {@link WebConfig}). Use instead of {@code @RestController}, and map only the resource path
 * (e.g. {@code @RequestMapping("/members")}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
public @interface ApiV1 {

	String PREFIX = "/api/v1";

	@AliasFor(annotation = RestController.class, attribute = "value")
	String value() default "";

}
