package com.kpi.studentcouncil.crm.shared.pagination;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** The single response shape of every paginated list endpoint. Pages are zero-based. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first,
		boolean last) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.isFirst(), page.isLast());
	}

	public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
		return of(page.map(mapper));
	}

}
