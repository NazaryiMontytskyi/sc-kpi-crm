package com.kpi.studentcouncil.crm.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Preferred UI language of the signed-in user. */
public record ChangeLocaleRequest(@NotBlank @Pattern(regexp = "uk|en") String locale) {
}
