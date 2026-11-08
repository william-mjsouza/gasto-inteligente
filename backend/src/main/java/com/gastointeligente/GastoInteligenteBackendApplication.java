package com.gastointeligente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada do monólito.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} é excluída para impedir que o Spring Boot
 * crie um usuário padrão com senha gerada. Usuários reais e login com sessão entram na Etapa 2.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GastoInteligenteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GastoInteligenteBackendApplication.class, args);
	}

}
