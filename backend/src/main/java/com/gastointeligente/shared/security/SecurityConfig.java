package com.gastointeligente.shared.security;

import com.gastointeligente.auth.AuthJsonHandlers;
import com.gastointeligente.auth.LoginAttemptService;
import com.gastointeligente.auth.LoginThrottleFilter;
import com.gastointeligente.auth.SessionRevalidationFilter;
import com.gastointeligente.household.MembershipRepository;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiErrorWriter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Política de segurança.
 *
 * <ul>
 * <li>Nega por padrão: toda a API exige sessão autenticada, exceto obter o token CSRF e fazer
 * login. Administração de membros exige papel ADMIN.</li>
 * <li>Login pelo mecanismo de formulário do Spring Security (URL-encoded), com handlers JSON,
 * troca do identificador de sessão e renovação do token CSRF. Logout invalida a sessão.</li>
 * <li>CSRF ativo em toda operação mutável, inclusive login e logout. O token fica na sessão do
 * servidor (não em cookie) e é entregue ao React por {@code GET /api/v1/auth/csrf}.</li>
 * <li>Health check restrito; demais endpoints do Actuator negados.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

	public static final String LOGIN_PATH = "/api/v1/auth/login";

	public static final String LOGOUT_PATH = "/api/v1/auth/logout";

	public static final String CSRF_HEADER = "X-CSRF-TOKEN";

	static final String CONTENT_SECURITY_POLICY = String.join("; ", "default-src 'self'", "script-src 'self'",
			"style-src 'self'", "img-src 'self' data:", "font-src 'self'", "connect-src 'self'", "object-src 'none'",
			"base-uri 'self'", "form-action 'self'", "frame-ancestors 'none'");

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errorWriter,
			AuthJsonHandlers authHandlers, LoginAttemptService loginAttempts, MembershipRepository memberships,
			SecurityContextRepository contextRepository) throws Exception {
		http.authorizeHttpRequests((auth) -> auth
			.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
					"/actuator/health/readiness")
			.permitAll()
			.requestMatchers("/actuator", "/actuator/**")
			.denyAll()
			.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
			.permitAll()
			.requestMatchers(HttpMethod.POST, LOGIN_PATH, LOGOUT_PATH)
			.permitAll()
			.requestMatchers("/api/v1/members", "/api/v1/members/**")
			.hasRole("ADMIN")
			.requestMatchers("/api", "/api/**")
			.authenticated()
			.requestMatchers("/error")
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/**")
			.permitAll()
			.requestMatchers(HttpMethod.HEAD, "/**")
			.permitAll()
			.anyRequest()
			.denyAll());

		HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
		csrfRepository.setHeaderName(CSRF_HEADER);
		http.csrf((csrf) -> csrf.csrfTokenRepository(csrfRepository));

		http.securityContext((context) -> context.securityContextRepository(contextRepository));

		http.formLogin((form) -> form
			// Rota da SPA: evita a página de login HTML gerada pelo Spring Security.
			.loginPage("/login")
			.loginProcessingUrl(LOGIN_PATH)
			.usernameParameter(AuthJsonHandlers.EMAIL_PARAMETER)
			.passwordParameter(AuthJsonHandlers.PASSWORD_PARAMETER)
			.successHandler(authHandlers)
			.failureHandler(authHandlers));

		http.logout((logout) -> logout.logoutUrl(LOGOUT_PATH)
			.invalidateHttpSession(true)
			.clearAuthentication(true)
			.deleteCookies("JSESSIONID")
			.logoutSuccessHandler(authHandlers));

		http.sessionManagement((session) -> session.sessionFixation((fixation) -> fixation.changeSessionId()));
		http.requestCache((cache) -> cache.disable());
		http.httpBasic(AbstractHttpConfigurer::disable);

		http.addFilterBefore(new LoginThrottleFilter(LOGIN_PATH, loginAttempts, errorWriter),
				UsernamePasswordAuthenticationFilter.class);
		http.addFilterBefore(new SessionRevalidationFilter(memberships, contextRepository), AuthorizationFilter.class);

		http.exceptionHandling((ex) -> ex
			.authenticationEntryPoint(
					(request, response, e) -> errorWriter.write(request, response, ApiErrorCode.UNAUTHENTICATED))
			.accessDeniedHandler((request, response, e) -> errorWriter.write(request, response,
					(e instanceof CsrfException) ? ApiErrorCode.CSRF_INVALID : ApiErrorCode.FORBIDDEN)));

		http.headers((headers) -> headers.contentSecurityPolicy((csp) -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
			.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));

		return http.build();
	}

}
