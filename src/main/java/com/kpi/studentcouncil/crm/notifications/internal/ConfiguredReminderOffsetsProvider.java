package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

/** {@link ReminderOffsetsProvider} backed by {@code crm.notifications.due-reminders.offsets}. */
@Component
class ConfiguredReminderOffsetsProvider implements ReminderOffsetsProvider {

	private final List<Duration> offsets;

	ConfiguredReminderOffsetsProvider(DueDateReminderProperties properties) {
		this.offsets = normalize(properties.offsets());
	}

	@Override
	public List<Duration> offsets() {
		return offsets;
	}

	/** Drops null, zero and negative values and duplicates; sorts largest first. */
	static List<Duration> normalize(List<Duration> raw) {
		return raw.stream().filter(Objects::nonNull).filter(d -> d.compareTo(Duration.ZERO) > 0).distinct()
				.sorted(Comparator.reverseOrder()).toList();
	}

}
