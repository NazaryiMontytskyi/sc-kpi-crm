package com.kpi.studentcouncil.crm.notifications.internal;

import java.time.Duration;
import java.util.List;

/**
 * The single source of the "due soon" reminder offsets (NT-1). The default implementation reads the global
 * configuration {@code crm.notifications.due-reminders.offsets}. Extension point for per-user offsets [OPEN]: such an
 * implementation would add a recipient parameter; the reminder service already selects the threshold per task and
 * keys idempotency per recipient, so only the selection call would change.
 */
interface ReminderOffsetsProvider {

	/** Distinct positive offsets, largest (earliest reminder) first; may be empty (then only "overdue" is sent). */
	List<Duration> offsets();

}
