package com.kpi.studentcouncil.crm.shared.web;

import java.util.Locale;
import java.util.Optional;

/**
 * Extension point: the identity module supplies the signed-in user's saved locale (uk|en).
 * Takes precedence over {@code Accept-Language}. No implementation exists until INC-004.
 */
public interface UserLocaleProvider {

	Optional<Locale> currentUserLocale();

}
