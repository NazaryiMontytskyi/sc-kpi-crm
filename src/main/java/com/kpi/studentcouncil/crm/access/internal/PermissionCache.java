package com.kpi.studentcouncil.crm.access.internal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * In-memory cache of effective permissions per user (single instance, ~80 users). It is flushed completely on any
 * role or assignment change, immediately and again after the transaction completes. A load that raced with a flush
 * is not stored (version check), so a stale value cannot survive an invalidation. Open sessions see changes on the
 * next request because identity asks for the permissions on every request.
 */
@Component
class PermissionCache {

	private final Map<UUID, Set<String>> entries = new ConcurrentHashMap<>();

	private final AtomicLong version = new AtomicLong();

	Set<String> get(UUID userId, Supplier<Set<String>> loader) {
		Set<String> cached = entries.get(userId);
		if (cached != null) {
			return cached;
		}
		long seen = version.get();
		Set<String> loaded = Set.copyOf(loader.get());
		if (version.get() == seen) {
			entries.put(userId, loaded);
			if (version.get() != seen) {
				entries.remove(userId);
			}
		}
		return loaded;
	}

	Optional<Set<String>> peek(UUID userId) {
		return Optional.ofNullable(entries.get(userId));
	}

	/** Flushes now and, inside a transaction, again once it has completed. */
	void invalidateAll() {
		flush();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					flush();
				}
			});
		}
	}

	private void flush() {
		version.incrementAndGet();
		entries.clear();
	}

}
