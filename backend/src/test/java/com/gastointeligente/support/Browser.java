package com.gastointeligente.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP real com cookies, simulando um navegador: mantém o cookie de sessão e o token CSRF
 * obtido pelo endpoint dedicado, como o frontend faz. Usado para testar sessão, CSRF e isolamento
 * contra o servidor embarcado e o PostgreSQL real.
 */
public class Browser {

	public static final String SESSION_COOKIE = "JSESSIONID";

	private final String baseUrl;

	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	private final HttpClient http;

	private final JsonMapper json = JsonMapper.builder().build();

	private String csrfHeader = "X-CSRF-TOKEN";

	private String csrfToken;

	public Browser(int port) {
		this.baseUrl = "http://localhost:" + port;
		this.http = HttpClient.newBuilder().cookieHandler(this.cookies).build();
	}

	public record Response(int status, String body, HttpResponse<String> raw, JsonMapper mapper) {

		public JsonNode json() {
			return this.mapper.readTree(this.body);
		}

		public String code() {
			return json().path("code").asString();
		}

		public Optional<String> header(String name) {
			return this.raw.headers().firstValue(name);
		}

	}

	// --- Sessão e CSRF ---

	/** Obtém (ou renova) o token CSRF da sessão atual. */
	public String refreshCsrf() {
		Response response = get("/api/v1/auth/csrf");
		if (response.status() != 200) {
			throw new IllegalStateException("csrf: " + response.status());
		}
		this.csrfHeader = response.json().path("headerName").asString();
		this.csrfToken = response.json().path("token").asString();
		return this.csrfToken;
	}

	public String csrfToken() {
		return this.csrfToken;
	}

	public void useCsrfToken(String token) {
		this.csrfToken = token;
	}

	public Response login(String email, String password) {
		if (this.csrfToken == null) {
			refreshCsrf();
		}
		Response response = postForm("/api/v1/auth/login", Map.of("email", email, "password", password), true);
		if (response.status() == 200) {
			// O token é renovado no login; o corpo traz o novo.
			this.csrfToken = response.json().path("csrf").path("token").asString();
		}
		return response;
	}

	public Response loginOrFail(String email, String password) {
		Response response = login(email, password);
		if (response.status() != 200) {
			throw new IllegalStateException("login falhou: " + response.status() + " " + response.body());
		}
		return response;
	}

	public Response logout() {
		return send(builder("/api/v1/auth/logout").POST(HttpRequest.BodyPublishers.noBody()), true);
	}

	public String sessionId() {
		return this.cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter((c) -> c.getName().equals(SESSION_COOKIE))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

	/** Substitui o cookie de sessão (por exemplo, para reutilizar uma sessão encerrada). */
	public void forceSessionId(String value) {
		this.cookies.getCookieStore().removeAll();
		HttpCookie cookie = new HttpCookie(SESSION_COOKIE, value);
		cookie.setPath("/");
		cookie.setVersion(0);
		this.cookies.getCookieStore().add(URI.create(this.baseUrl), cookie);
	}

	// --- Requisições ---

	public Response get(String path) {
		return send(builder(path).GET(), false);
	}

	public Response postJson(String path, Object body) {
		return sendJson("POST", path, body, true);
	}

	public Response postJsonWithoutCsrf(String path, Object body) {
		return sendJson("POST", path, body, false);
	}

	public Response patchJson(String path, Object body) {
		return sendJson("PATCH", path, body, true);
	}

	public Response patchJsonWithoutCsrf(String path, Object body) {
		return sendJson("PATCH", path, body, false);
	}

	public Response delete(String path) {
		return send(builder(path).DELETE(), true);
	}

	public Response deleteWithoutCsrf(String path) {
		return send(builder(path).DELETE(), false);
	}

	public Response postForm(String path, Map<String, String> form, boolean withCsrf) {
		String encoded = new LinkedHashMap<>(form).entrySet()
			.stream()
			.map((e) -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
					+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		return send(builder(path).header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(encoded)), withCsrf);
	}

	private Response sendJson(String method, String path, Object body, boolean withCsrf) {
		String payload = this.json.writeValueAsString(body);
		return send(builder(path).header("Content-Type", "application/json")
			.method(method, HttpRequest.BodyPublishers.ofString(payload)), withCsrf);
	}

	private HttpRequest.Builder builder(String path) {
		return HttpRequest.newBuilder(URI.create(this.baseUrl + path)).header("Accept", "application/json");
	}

	private Response send(HttpRequest.Builder builder, boolean withCsrf) {
		if (withCsrf && this.csrfToken != null) {
			builder.header(this.csrfHeader, this.csrfToken);
		}
		try {
			HttpResponse<String> response = this.http.send(builder.build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new Response(response.statusCode(), response.body(), response, this.json);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
