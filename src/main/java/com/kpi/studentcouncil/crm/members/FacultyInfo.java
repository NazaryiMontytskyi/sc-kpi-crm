package com.kpi.studentcouncil.crm.members;

import java.util.UUID;

/**
 * Read-only view of a {@code Faculty} for other modules (the entity stays internal).
 *
 * @param id     faculty id
 * @param code   short unique code
 * @param nameUk Ukrainian name
 * @param nameEn English name
 * @param active false when archived: existing references stay valid, but it is not selectable for new ones
 */
public record FacultyInfo(UUID id, String code, String nameUk, String nameEn, boolean active) {
}
