package com.gastointeligente.shared.web;

import java.util.List;

/** Erro de negócio ou de acesso convertido em {@link ApiError} pelo {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

	private final ApiErrorCode code;

	private final List<ApiError.Detail> details;

	public ApiException(ApiErrorCode code) {
		this(code, List.of());
	}

	public ApiException(ApiErrorCode code, List<ApiError.Detail> details) {
		super(code.name(), null, false, false);
		this.code = code;
		this.details = List.copyOf(details);
	}

	public static ApiException notFound() {
		return new ApiException(ApiErrorCode.NOT_FOUND);
	}

	public static ApiException validation(String field, String message) {
		return new ApiException(ApiErrorCode.VALIDATION_FAILED, List.of(new ApiError.Detail(field, null, message)));
	}

	public ApiErrorCode code() {
		return this.code;
	}

	public List<ApiError.Detail> details() {
		return this.details;
	}

}
