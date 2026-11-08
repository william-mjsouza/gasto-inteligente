package com.gastointeligente.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.gastointeligente.TestcontainersConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Teste de integração com servidor HTTP real (porta aleatória), PostgreSQL real e
 * {@link TestFamilies}. O cookie de sessão não é Secure aqui porque o teste usa HTTP local.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "server.servlet.session.cookie.secure=false")
@Import({ TestcontainersConfiguration.class, TestFamilies.class })
public @interface HttpIntegrationTest {

}
