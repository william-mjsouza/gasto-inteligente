package com.gastointeligente.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Beans de segurança independentes do servidor web, também usados pelos comandos administrativos
 * executados sem servidor HTTP.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityBeans {

	@Bean
	PasswordEncoder passwordEncoder() {
		// Delegating: BCrypt por padrão, com prefixo do algoritmo para permitir migração futura.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

}
