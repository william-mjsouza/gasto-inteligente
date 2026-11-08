package com.gastointeligente.shared.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte exceções em {@link ApiError}. Rotas inexistentes respondem JSON 404, nunca o HTML da
 * SPA. Mensagens internas de exceções não são devolvidas ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> api(HttpServletRequest request, ApiException ex) {
		return respond(request, ex.code(), ex.details());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ApiError> optimisticLock(HttpServletRequest request) {
		return respond(request, ApiErrorCode.VERSION_CONFLICT, List.of());
	}

	/** Violação de restrição do banco (por exemplo, unicidade sob concorrência). Sem detalhes do SQL. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> integrity(HttpServletRequest request) {
		return respond(request, ApiErrorCode.CONFLICT, List.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> accessDenied(HttpServletRequest request) {
		return respond(request, ApiErrorCode.FORBIDDEN, List.of());
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiError> unauthenticated(HttpServletRequest request) {
		return respond(request, ApiErrorCode.UNAUTHENTICATED, List.of());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> notFound(HttpServletRequest request) {
		return respond(request, ApiErrorCode.NOT_FOUND, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
		return respond(request, ApiErrorCode.METHOD_NOT_ALLOWED, List.of());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiError> unsupportedMediaType(HttpServletRequest request) {
		return respond(request, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<ApiError> malformed(HttpServletRequest request) {
		return respond(request, ApiErrorCode.MALFORMED_REQUEST, List.of());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiError> missingParameter(HttpServletRequest request, MissingServletRequestParameterException ex) {
		return respond(request, ApiErrorCode.VALIDATION_FAILED,
				List.of(new ApiError.Detail(ex.getParameterName(), null, "Obrigatório.")));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validation(HttpServletRequest request, MethodArgumentNotValidException ex) {
		List<ApiError.Detail> details = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new ApiError.Detail(error.getField(), null, error.getDefaultMessage()))
			.toList();
		return respond(request, ApiErrorCode.VALIDATION_FAILED, details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiError> methodValidation(HttpServletRequest request, HandlerMethodValidationException ex) {
		List<ApiError.Detail> details = ex.getParameterValidationResults()
			.stream()
			.flatMap((result) -> result.getResolvableErrors()
				.stream()
				.map((error) -> new ApiError.Detail(result.getMethodParameter().getParameterName(), null,
						error.getDefaultMessage())))
			.toList();
		return respond(request, ApiErrorCode.VALIDATION_FAILED, details);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(HttpServletRequest request, Exception ex) {
		// Registra apenas o tipo e a pilha; não registra corpo da requisição nem dados importados.
		log.error("Erro não tratado em {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(request, ApiErrorCode.INTERNAL_ERROR, List.of());
	}

	private static ResponseEntity<ApiError> respond(HttpServletRequest request, ApiErrorCode code,
			List<ApiError.Detail> details) {
		ApiError body = new ApiError(code.name(), code.defaultMessage(), details,
				RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(code.status()).body(body);
	}

}
