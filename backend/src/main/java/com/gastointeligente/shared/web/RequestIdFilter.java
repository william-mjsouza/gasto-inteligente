package com.gastointeligente.shared.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Atribui um identificador a cada requisição, devolvido no cabeçalho {@value #HEADER}, nos corpos
 * de erro e nos logs (MDC {@code requestId}). O valor é sempre gerado no servidor; um cabeçalho
 * enviado pelo cliente é ignorado para não permitir injeção de conteúdo nos logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

	private static final String MDC_KEY = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE, requestId);
		response.setHeader(HEADER, requestId);
		MDC.put(MDC_KEY, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		// O despacho para /error mantém o mesmo identificador já atribuído.
		return true;
	}

	/** Identificador da requisição atual, ou {@code null} fora do filtro. */
	public static String currentRequestId(HttpServletRequest request) {
		Object value = request.getAttribute(ATTRIBUTE);
		return value instanceof String id ? id : null;
	}

}
