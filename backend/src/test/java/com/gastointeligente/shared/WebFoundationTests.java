package com.gastointeligente.shared;

import com.gastointeligente.TestcontainersConfiguration;
import com.gastointeligente.shared.web.RequestIdFilter;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comportamento HTTP da Fundação: health restrito, negação por padrão, erros JSON e SPA. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebFoundationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void healthIsPublicAndExposesOnlyStatus() throws Exception {
		this.mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist())
			.andExpect(jsonPath("$.details").doesNotExist());
		this.mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		this.mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	@Test
	void otherActuatorEndpointsAreNotAccessible() throws Exception {
		for (String path : new String[] { "/actuator", "/actuator/env", "/actuator/info", "/actuator/beans" }) {
			this.mvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@WithMockUser
	void otherActuatorEndpointsStayDeniedEvenWhenAuthenticated() throws Exception {
		this.mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
	}

	@Test
	void apiDeniesUnauthenticatedRequestsWithJsonError() throws Exception {
		MvcResult result = this.mvc.perform(get("/api/v1/transactions"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").isNotEmpty())
			.andExpect(jsonPath("$.requestId").isNotEmpty())
			.andReturn();

		String headerId = result.getResponse().getHeader(RequestIdFilter.HEADER);
		assertThat(headerId).isNotBlank();
		assertThat(result.getResponse().getContentAsString()).contains(headerId);
	}

	@Test
	void clientSuppliedRequestIdIsIgnored() throws Exception {
		MvcResult result = this.mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "forjado\nlinha"))
			.andReturn();

		assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER)).isNotEqualTo("forjado\nlinha")
			.matches("[0-9a-f-]{36}");
	}

	@Test
	@WithMockUser
	void unknownApiRouteReturnsJson404InsteadOfSpaHtml() throws Exception {
		this.mvc.perform(get("/api/v1/rota-inexistente").accept(MediaType.TEXT_HTML, MediaType.ALL))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	@WithMockUser
	void mutatingRequestWithoutCsrfTokenIsRejected() throws Exception {
		this.mvc.perform(post("/api/v1/imports/preview"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@WithMockUser
	void mutatingRequestWithValidCsrfPassesCsrfCheck() throws Exception {
		// Sem endpoint implementado ainda: a requisição passa pelo CSRF e não encontra recurso.
		this.mvc.perform(post("/api/v1/imports/preview").with(csrf()))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("CSRF_INVALID")));
	}

	@Test
	void spaRoutesServeIndexHtml() throws Exception {
		this.mvc.perform(get("/previsao/2026-11"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("spa-index-de-teste")));
		// Na raiz, a welcome page do Spring Boot encaminha (forward) para index.html; o MockMvc não
		// segue forwards, então verificamos o destino. O conteúdo é validado no servidor real.
		this.mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
	}

	@Test
	void missingStaticAssetIsNotReplacedByIndexHtml() throws Exception {
		this.mvc.perform(get("/assets/inexistente.js")).andExpect(status().isNotFound());
	}

	@Test
	void nonGetRequestsOutsideApiAreDenied() throws Exception {
		this.mvc.perform(post("/qualquer").with(csrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void securityHeadersArePresent() throws Exception {
		this.mvc.perform(get("/"))
			.andExpect(header().string("Content-Security-Policy",
					org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Referrer-Policy", "same-origin"));
	}

}
