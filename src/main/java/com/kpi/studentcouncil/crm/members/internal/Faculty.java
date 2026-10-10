package com.kpi.studentcouncil.crm.members.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** Faculty or institute of the university. Never hard-deleted: archiving makes it non-selectable. */
@Entity
@Table(name = "faculty")
class Faculty extends ArchivableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name_uk", nullable = false, length = 200)
	private String nameUk;

	@Column(name = "name_en", nullable = false, length = 200)
	private String nameEn;

	protected Faculty() {
	}

	Faculty(String code, String nameUk, String nameEn) {
		this.code = code;
		this.nameUk = nameUk;
		this.nameEn = nameEn;
	}

	String getCode() {
		return code;
	}

	String getNameUk() {
		return nameUk;
	}

	String getNameEn() {
		return nameEn;
	}

	void update(String code, String nameUk, String nameEn) {
		this.code = code;
		this.nameUk = nameUk;
		this.nameEn = nameEn;
	}

}
