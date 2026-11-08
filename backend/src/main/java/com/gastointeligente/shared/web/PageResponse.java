package com.gastointeligente.shared.web;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Página de resultados. Tamanho máximo e campos de ordenação permitidos são definidos pelo servidor. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/**
	 * Valida parâmetros de paginação. {@code sort} tem o formato {@code campo} ou {@code campo,desc};
	 * apenas campos de {@code allowedSort} são aceitos.
	 */
	public static Pageable pageable(Integer page, Integer size, String sort, Set<String> allowedSort,
			Sort defaultSort) {
		int p = (page == null) ? 0 : page;
		int s = (size == null) ? DEFAULT_SIZE : size;
		if (p < 0) {
			throw ApiException.validation("page", "Deve ser maior ou igual a 0.");
		}
		if (s < 1 || s > MAX_SIZE) {
			throw ApiException.validation("size", "Deve estar entre 1 e " + MAX_SIZE + ".");
		}
		Sort order = defaultSort;
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",", -1);
			String field = parts[0].trim();
			if (!allowedSort.contains(field) || parts.length > 2) {
				throw ApiException.validation("sort", "Ordenação permitida: " + String.join(", ", allowedSort) + ".");
			}
			Sort.Direction direction = Sort.Direction.ASC;
			if (parts.length == 2) {
				String dir = parts[1].trim().toLowerCase(java.util.Locale.ROOT);
				if (!dir.equals("asc") && !dir.equals("desc")) {
					throw ApiException.validation("sort", "Direção deve ser asc ou desc.");
				}
				direction = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
			}
			order = Sort.by(direction, field).and(Sort.by("id"));
		}
		return PageRequest.of(p, s, order);
	}

}
