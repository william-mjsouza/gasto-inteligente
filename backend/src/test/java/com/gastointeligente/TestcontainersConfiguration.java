package com.gastointeligente;

import java.time.Clock;

import com.gastointeligente.support.MutableClock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real via Testcontainers (nunca H2). A imagem vem da propriedade {@code postgres.image}
 * definida no pom.xml, a mesma de compose.yaml; o CI verifica que as duas coincidem.
 *
 * <p>Um único contêiner é compartilhado por todos os contextos de teste da JVM. Também expõe um
 * relógio controlável ({@link MutableClock}) como {@link Clock} primário.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String POSTGRES_IMAGE = System.getProperty("postgres.image", "postgres:18.6-alpine3.24");

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));

	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
