package com.gastointeligente.shared.web;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Escreve {@link ApiError} diretamente na resposta, para uso fora do Spring MVC (filtros). */
@Component
public class ApiErrorWriter {

	private final JsonMapper jsonMapper;

	public ApiErrorWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ApiErrorCode code) throws IOException {
		ApiError body = new ApiError(code.name(), code.defaultMessage(), List.of(),
				RequestIdFilter.currentRequestId(request));
		response.setStatus(code.status().value());
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
