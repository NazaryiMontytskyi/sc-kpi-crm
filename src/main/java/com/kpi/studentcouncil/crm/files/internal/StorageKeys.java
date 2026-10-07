package com.kpi.studentcouncil.crm.files.internal;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates and validates storage keys: {@code yyyy/MM/<random uuid>}. The original file name never takes part,
 * so a hostile name cannot influence where bytes are written.
 */
final class StorageKeys {

	private static final Pattern VALID = Pattern
			.compile("\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private StorageKeys() {
	}

	static String generate(Clock clock) {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
		return "%04d/%02d/%s".formatted(now.getYear(), now.getMonthValue(), UUID.randomUUID());
	}

	static boolean isValid(String key) {
		return key != null && VALID.matcher(key).matches();
	}

}
