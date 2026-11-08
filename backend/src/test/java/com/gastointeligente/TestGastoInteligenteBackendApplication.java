package com.gastointeligente;

import org.springframework.boot.SpringApplication;

/**
 * Executa a aplicação localmente com um PostgreSQL efêmero via Testcontainers:
 * {@code ./mvnw spring-boot:test-run}. Útil quando não se quer subir o Docker Compose.
 */
public class TestGastoInteligenteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(GastoInteligenteBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
