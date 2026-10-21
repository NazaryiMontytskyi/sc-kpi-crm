package com.kpi.studentcouncil.crm.members;

import java.util.UUID;

/**
 * Searchable text of a member profile.
 *
 * @param id       the member, equal to the user id
 * @param fullName last name, first name and middle name
 * @param details  study group, telegram handle and the faculty code and names (both languages)
 * @param archived whether the profile is archived
 */
public record MemberText(UUID id, String fullName, String details, boolean archived) {
}
