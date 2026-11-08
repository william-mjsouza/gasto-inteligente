package com.gastointeligente.auth;

import java.time.Duration;

import com.gastointeligente.support.MutableClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordAndThrottleUnitTests {

	@Test
	void passwordPolicy() {
		assertThat(PasswordPolicy.violation(null)).isNotNull();
		assertThat(PasswordPolicy.violation("curta-11ch")).isNotNull();
		assertThat(PasswordPolicy.violation(" com-espaco-inicial")).isNotNull();
		assertThat(PasswordPolicy.violation("a".repeat(73))).contains("72 bytes");
		// 36 caracteres de 2 bytes = 72 bytes: aceito; 37 = 74 bytes: rejeitado (limite do BCrypt).
		assertThat(PasswordPolicy.violation("é".repeat(36))).isNull();
		assertThat(PasswordPolicy.violation("é".repeat(37))).isNotNull();
		assertThat(PasswordPolicy.violation("frase longa e segura")).isNull();
	}

	@Test
	void throttleCountsPerEmailAndPerIpWithinWindow() {
		MutableClock clock = new MutableClock();
		LoginAttemptService service = new LoginAttemptService(clock, 3, 5, Duration.ofMinutes(15));

		for (int i = 0; i < 3; i++) {
			service.recordFailure("Ana@Exemplo.test", "10.0.0.1");
		}
		assertThat(service.isBlocked(" ana@exemplo.test", "10.0.0.9")).isTrue();
		assertThat(service.isBlocked("bia@exemplo.test", "10.0.0.9")).isFalse();

		service.recordFailure("c@exemplo.test", "10.0.0.1");
		service.recordFailure("d@exemplo.test", "10.0.0.1");
		assertThat(service.isBlocked("nova@exemplo.test", "10.0.0.1")).isTrue();

		clock.advance(Duration.ofMinutes(15).plusSeconds(1));
		assertThat(service.isBlocked("ana@exemplo.test", "10.0.0.1")).isFalse();
	}

	@Test
	void successClearsEmailCounterOnly() {
		MutableClock clock = new MutableClock();
		LoginAttemptService service = new LoginAttemptService(clock, 2, 3, Duration.ofMinutes(15));
		service.recordFailure("ana@exemplo.test", "10.0.0.1");
		service.recordFailure("ana@exemplo.test", "10.0.0.1");
		assertThat(service.isBlocked("ana@exemplo.test", "10.0.0.2")).isTrue();

		service.recordSuccess("ana@exemplo.test");
		assertThat(service.isBlocked("ana@exemplo.test", "10.0.0.2")).isFalse();
		service.recordFailure("x@exemplo.test", "10.0.0.1");
		assertThat(service.isBlocked("y@exemplo.test", "10.0.0.1")).isTrue();
	}

}
