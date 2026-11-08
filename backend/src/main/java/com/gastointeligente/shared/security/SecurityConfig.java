package com.gastointeligente.shared.security;

import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiErrorWriter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Política de segurança da Fundação (Etapa 1).
 *
 * <ul>
 * <li>Nega por padrão: toda a API exige autenticação. Como ainda não há mecanismo de login
 * (Etapa 2), nenhum dado financeiro é acessível.</li>
 * <li>Health check restrito: apenas {@code /actuator/health} e as sondas liveness/readiness,
 * sem detalhes nem componentes. Demais endpoints do Actuator ficam negados.</li>
 * <li>Arquivos estáticos e rotas da SPA: apenas GET/HEAD públicos; não contêm dados.</li>
 * <li>CSRF permanece ativo para toda operação mutável.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	static final String CONTENT_SECURITY_POLICY = String.join("; ", "default-src 'self'", "script-src 'self'",
			"style-src 'self'", "img-src 'self' data:", "font-src 'self'", "connect-src 'self'", "object-src 'none'",
			"base-uri 'self'", "form-action 'self'", "frame-ancestors 'none'");

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errorWriter) throws Exception {
		http.authorizeHttpRequests((auth) -> auth
			.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
					"/actuator/health/readiness")
			.permitAll()
			.requestMatchers("/actuator", "/actuator/**")
			.denyAll()
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

		// Login por formulário e logout com handlers JSON serão configurados na Etapa 2.
		http.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable);

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
