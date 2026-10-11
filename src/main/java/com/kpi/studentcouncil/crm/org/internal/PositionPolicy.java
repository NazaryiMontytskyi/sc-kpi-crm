package com.kpi.studentcouncil.crm.org.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;

/** Pure rules of positions and role binding. */
final class PositionPolicy {

	static final int MAX_CAPACITY = 100;

	private PositionPolicy() {
	}

	/** Capacity is 1..{@value #MAX_CAPACITY}; a head position has exactly one holder, so the unit head is unique. */
	static void assertCapacity(boolean head, int capacity) {
		if (capacity < 1 || capacity > MAX_CAPACITY) {
			throw new BusinessRuleException("POSITION_CAPACITY_INVALID", MAX_CAPACITY);
		}
		if (head && capacity != 1) {
			throw new BusinessRuleException("POSITION_HEAD_SINGLE_HOLDER");
		}
	}

	/** Active holders must never exceed the capacity. */
	static void assertHasRoom(long activeHolders, int capacity) {
		if (activeHolders >= capacity) {
			throw new ConflictException("POSITION_FULL", capacity);
		}
	}

	/** The capacity cannot be lowered below the number of current holders. */
	static void assertCapacityCoversHolders(long activeHolders, int newCapacity) {
		if (newCapacity < activeHolders) {
			throw new ConflictException("POSITION_CAPACITY_BELOW_HOLDERS", activeHolders);
		}
	}

	/** Roles to add and to remove when the bound role set changes (for the holders of the position). */
	record RoleDiff(Set<UUID> added, Set<UUID> removed) {
	}

	static RoleDiff diff(Set<UUID> before, Set<UUID> after) {
		Set<UUID> added = new HashSet<>(after);
		added.removeAll(before);
		Set<UUID> removed = new HashSet<>(before);
		removed.removeAll(after);
		return new RoleDiff(added, removed);
	}

}
