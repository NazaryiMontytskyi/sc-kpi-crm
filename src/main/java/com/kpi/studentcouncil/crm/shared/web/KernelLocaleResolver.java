package com.kpi.studentcouncil.crm.shared.web;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Resolves the request locale: saved user locale, else {@code Accept-Language}, else default {@code uk}.
 * Only {@code uk} and {@code en} are supported.
 */
public class KernelLocaleResolver extends AcceptHeaderLocaleResolver {

	public static final Locale UK = Locale.of("uk");
	public static final Locale EN = Locale.of("en");

	private final ObjectProvider<UserLocaleProvider> userLocale;

	public KernelLocaleResolver(ObjectProvider<UserLocaleProvider> userLocale) {
		this.userLocale = userLocale;
		setSupportedLocales(List.of(UK, EN));
		setDefaultLocale(UK);
	}

	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		UserLocaleProvider provider = userLocale.getIfAvailable();
		if (provider != null) {
			var saved = provider.currentUserLocale().filter(l -> getSupportedLocales().contains(Locale.of(l.getLanguage())));
			if (saved.isPresent()) {
				return Locale.of(saved.get().getLanguage());
			}
		}
		return super.resolveLocale(request);
	}

}
