package com.kpi.studentcouncil.crmtest.shared;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Test-only controller that triggers each error kind. */
@ApiV1
@RequestMapping("/test-kernel")
class TestApiController {

	record Payload(@NotBlank String name) {
	}

	@GetMapping("/not-found")
	void notFound() {
		throw new NotFoundException("TestNote", UUID.randomUUID());
	}

	@GetMapping("/conflict")
	void conflict() {
		throw new ConflictException("ALREADY_ARCHIVED");
	}

	@GetMapping("/rule")
	void rule() {
		throw new BusinessRuleException("INVALID_SORT_FIELD", "title");
	}

	@GetMapping("/forbidden")
	@PreAuthorize("hasAuthority('test.never')")
	void forbidden() {
	}

	@GetMapping("/boom")
	void boom() {
		throw new IllegalStateException("secret internal detail");
	}

	@PostMapping("/validate")
	void validate(@Valid @RequestBody Payload payload) {
	}

	@GetMapping("/page")
	PageResponse<String> page(@Valid PageParams params) {
		var pageable = params.toPageable(Set.of("name"), Sort.by("name"));
		return PageResponse.of(new PageImpl<>(List.of("a", "b"), pageable, 42));
	}

}
