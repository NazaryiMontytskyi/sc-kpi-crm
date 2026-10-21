package com.kpi.studentcouncil.crm.notifications;

/**
 * A way of delivering notifications. The in-app channel is the only implementation now; Telegram and e-mail
 * [LATER] are added by implementing this interface as a Spring bean - business modules and event listeners
 * depend only on this abstraction and need no change.
 */
public interface NotificationChannel {

	/** Delivers the message to its recipient. Runs in the transaction of the business change. */
	void deliver(NotificationMessage message);

}
