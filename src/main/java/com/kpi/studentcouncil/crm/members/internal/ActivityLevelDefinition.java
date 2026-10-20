package com.kpi.studentcouncil.crm.members.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** One step of the activity level scale. Editable reference data; archived, never deleted (OPEN-Q3). */
@Entity
@Table(name = "activity_level_definition")
class ActivityLevelDefinition extends ArchivableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "label_uk", nullable = false, length = 100)
	private String labelUk;

	@Column(name = "label_en", nullable = false, length = 100)
	private String labelEn;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected ActivityLevelDefinition() {
	}

	ActivityLevelDefinition(String code, String labelUk, String labelEn, int sortOrder) {
		update(code, labelUk, labelEn, sortOrder);
	}

	void update(String code, String labelUk, String labelEn, int sortOrder) {
		this.code = code;
		this.labelUk = labelUk;
		this.labelEn = labelEn;
		this.sortOrder = sortOrder;
	}

	String getCode() {
		return code;
	}

	String getLabelUk() {
		return labelUk;
	}

	String getLabelEn() {
		return labelEn;
	}

	int getSortOrder() {
		return sortOrder;
	}

}
