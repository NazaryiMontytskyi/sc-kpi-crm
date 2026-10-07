package com.kpi.studentcouncil.crm.shared.pagination;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * Standard pagination query parameters: {@code ?page=0&size=20&sort=field,asc}. Bind as a method parameter
 * annotated {@code @Valid}; convert with {@link #toPageable} supplying the whitelist of sortable fields.
 */
public record PageParams(@Min(0) Integer page, @Min(1) @Max(MAX_SIZE) Integer size, String sort) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	public Pageable toPageable(Set<String> sortableFields, Sort defaultSort) {
		int p = page == null ? 0 : page;
		int s = size == null ? DEFAULT_SIZE : size;
		return PageRequest.of(p, s, parseSort(sortableFields, defaultSort));
	}

	private Sort parseSort(Set<String> sortableFields, Sort defaultSort) {
		if (sort == null || sort.isBlank()) {
			return defaultSort;
		}
		String[] parts = sort.split(",", 2);
		String field = parts[0].trim();
		if (!sortableFields.contains(field)) {
			throw new BusinessRuleException("INVALID_SORT_FIELD", field);
		}
		boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
		return Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field);
	}

}
