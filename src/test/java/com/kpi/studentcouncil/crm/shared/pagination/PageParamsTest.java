package com.kpi.studentcouncil.crm.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

class PageParamsTest {

	private static final Set<String> SORTABLE = Set.of("name", "createdAt");

	@Test
	void appliesDefaults() {
		var pageable = new PageParams(null, null, null).toPageable(SORTABLE, Sort.by("name"));

		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(PageParams.DEFAULT_SIZE);
		assertThat(pageable.getSort()).isEqualTo(Sort.by("name"));
	}

	@Test
	void parsesSortDirection() {
		var pageable = new PageParams(2, 10, "createdAt,desc").toPageable(SORTABLE, Sort.by("name"));

		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	@Test
	void rejectsNonWhitelistedSortField() {
		assertThatThrownBy(() -> new PageParams(0, 10, "passwordHash").toPageable(SORTABLE, Sort.unsorted()))
				.isInstanceOf(BusinessRuleException.class);
	}

}
