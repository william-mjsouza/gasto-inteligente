package com.gastointeligente.shared.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.gastointeligente.shared.web.SpaWebConfig.SpaFallbackResourceResolver.isSpaRoute;
import static org.assertj.core.api.Assertions.assertThat;

class SpaFallbackResourceResolverTests {

	@ParameterizedTest
	@ValueSource(strings = { "", "previsao", "previsao/2026-11", "/importacoes/123", "apiario", "actuators" })
	void routesWithoutExtensionOutsideApiFallBackToIndex(String path) {
		assertThat(isSpaRoute(path)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "api", "api/", "api/v1/nao-existe", "/api/v1/x", "actuator", "actuator/env",
			"assets/app.js", "favicon.ico", "docs/arquivo.csv" })
	void apiActuatorAndFilesNeverFallBackToIndex(String path) {
		assertThat(isSpaRoute(path)).isFalse();
	}

}
