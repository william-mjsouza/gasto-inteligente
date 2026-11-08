package com.gastointeligente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do monólito. Usuários vêm exclusivamente do banco
 * ({@code AppUserDetailsService}); não existe usuário padrão nem senha gerada.
 */
@SpringBootApplication
public class GastoInteligenteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GastoInteligenteBackendApplication.class, args);
	}

}
