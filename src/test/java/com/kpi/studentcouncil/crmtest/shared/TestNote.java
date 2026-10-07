package com.kpi.studentcouncil.crmtest.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** Test-only entity used to exercise the kernel. Lives outside the application package so it is never scanned by other tests. */
@Entity
@Table(name = "test_note")
public class TestNote extends ArchivableEntity {

	@Column(name = "title", nullable = false)
	private String title;

	protected TestNote() {
	}

	public TestNote(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}
