/**
 * Shared kernel: base entities, soft delete, error handling, pagination, i18n and time.
 * Not a business module; it is OPEN so every module may use it, and it never depends on a business module.
 */
@ApplicationModule(displayName = "shared", type = ApplicationModule.Type.OPEN)
package com.kpi.studentcouncil.crm.shared;

import org.springframework.modulith.ApplicationModule;
