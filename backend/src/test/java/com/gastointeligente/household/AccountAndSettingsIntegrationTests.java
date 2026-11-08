package com.gastointeligente.household;

import java.util.Map;

import com.gastointeligente.auth.LoginAttemptService;
import com.gastointeligente.support.Browser;
import com.gastointeligente.support.HttpIntegrationTest;
import com.gastointeligente.support.TestFamilies;
import com.gastointeligente.support.TestFamilies.TestFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras de contas e configurações: validação, unicidade, concorrência otimista e paginação. */
@HttpIntegrationTest
class AccountAndSettingsIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestFamilies families;

	@Autowired
	private LoginAttemptService attempts;

	@Autowired
	private JdbcTemplate jdbc;

	private TestFamily family;

	private Browser browser;

	@BeforeEach
	void setUp() {
		this.attempts.reset();
		this.family = this.families.createFamily("Família Contas");
		this.browser = new Browser(this.port);
		this.browser.loginOrFail(this.family.admin().email(), TestFamilies.PASSWORD);
	}

	@Test
	void newFamilyHasDefaultSettings() {
		JsonNode settings = this.browser.get("/api/v1/family/settings").json();

		assertThat(settings.path("currency").asString()).isEqualTo("BRL");
		assertThat(settings.path("timezone").asString()).isEqualTo("America/Fortaleza");
		assertThat(settings.path("averageWindowMonths").asInt()).isEqualTo(3);
		assertThat(settings.path("prudentMarginPercent").isString()).isTrue();
		assertThat(settings.path("prudentMarginPercent").asString()).isEqualTo("10.00");
		assertThat(settings.path("cardPaymentOffsetMonths").asInt()).isEqualTo(1);
	}

	@Test
	void settingsUpdateValidatesAndUsesOptimisticLocking() {
		long version = this.browser.get("/api/v1/family/settings").json().path("version").asLong();

		assertThat(patchSettings(Map.of("timezone", "Lua/Base", "version", version)).code())
			.isEqualTo("VALIDATION_FAILED");
		assertThat(patchSettings(Map.of("prudentMarginPercent", "10,5", "version", version)).status())
			.isEqualTo(400);
		assertThat(patchSettings(Map.of("prudentMarginPercent", "100.01", "version", version)).status())
			.isEqualTo(400);
		assertThat(patchSettings(Map.of("prudentMarginPercent", "1.234", "version", version)).status())
			.isEqualTo(400);
		assertThat(patchSettings(Map.of("averageWindowMonths", 0, "version", version)).status()).isEqualTo(400);
		assertThat(patchSettings(Map.of("cardPaymentOffsetMonths", 4, "version", version)).status()).isEqualTo(400);
		assertThat(patchSettings(Map.of("timezone", "America/Recife")).status()).isEqualTo(400);

		Browser.Response ok = patchSettings(Map.of("timezone", "America/Recife", "averageWindowMonths", 6,
				"prudentMarginPercent", "7.5", "cardPaymentOffsetMonths", 0, "version", version));
		assertThat(ok.status()).isEqualTo(200);
		assertThat(ok.json().path("timezone").asString()).isEqualTo("America/Recife");
		assertThat(ok.json().path("prudentMarginPercent").asString()).isEqualTo("7.50");
		assertThat(ok.json().path("version").asLong()).isGreaterThan(version);

		Browser.Response stale = patchSettings(Map.of("averageWindowMonths", 2, "version", version));
		assertThat(stale.status()).isEqualTo(409);
		assertThat(stale.code()).isEqualTo("VERSION_CONFLICT");
		assertThat(this.browser.get("/api/v1/family/settings").json().path("averageWindowMonths").asInt())
			.isEqualTo(6);

		String margin = this.jdbc.queryForObject(
				"select prudent_margin_percent::text from family_settings where family_id = ?", String.class,
				this.family.familyId());
		assertThat(margin).isEqualTo("7.50");
	}

	@Test
	void accountLifecycleWithConflictsAndValidation() {
		Browser.Response created = this.browser.postJson("/api/v1/accounts",
				Map.of("name", "  Conta Corrente  ", "kind", "CASH"));
		assertThat(created.status()).isEqualTo(201);
		JsonNode account = created.json();
		assertThat(account.path("name").asString()).isEqualTo("Conta Corrente");
		assertThat(account.path("status").asString()).isEqualTo("ACTIVE");
		String path = "/api/v1/accounts/" + account.path("id").asString();
		long version = account.path("version").asLong();

		Browser.Response duplicate = this.browser.postJson("/api/v1/accounts",
				Map.of("name", "CONTA CORRENTE", "kind", "CARD"));
		assertThat(duplicate.status()).isEqualTo(409);
		assertThat(duplicate.code()).isEqualTo("DUPLICATE_NAME");

		assertThat(this.browser.postJson("/api/v1/accounts", Map.of("name", " ", "kind", "CASH")).status())
			.isEqualTo(400);
		assertThat(this.browser.postJson("/api/v1/accounts", Map.of("name", "X", "kind", "POUPANCA")).status())
			.isEqualTo(400);
		assertThat(this.browser.postJson("/api/v1/accounts", Map.of("name", "X")).status()).isEqualTo(400);

		Browser.Response archived = this.browser.patchJson(path, Map.of("status", "ARCHIVED", "version", version));
		assertThat(archived.status()).isEqualTo(200);
		assertThat(archived.json().path("status").asString()).isEqualTo("ARCHIVED");

		Browser.Response stale = this.browser.patchJson(path, Map.of("name", "Outro", "version", version));
		assertThat(stale.code()).isEqualTo("VERSION_CONFLICT");

		Browser.Response staleDelete = this.browser.delete(path + "?version=" + version);
		assertThat(staleDelete.code()).isEqualTo("VERSION_CONFLICT");
		long current = archived.json().path("version").asLong();
		assertThat(this.browser.delete(path + "?version=" + current).status()).isEqualTo(204);
		assertThat(this.browser.get(path).status()).isEqualTo(404);
	}

	@Test
	void accountListIsPaginatedWithLimitsAndAllowedSorting() {
		for (String name : new String[] { "Carteira", "Banco Alfa", "Cartão Beta" }) {
			String kind = name.startsWith("Cartão") ? "CARD" : "CASH";
			this.browser.postJson("/api/v1/accounts", Map.of("name", name, "kind", kind));
		}

		JsonNode page = this.browser.get("/api/v1/accounts?size=2").json();
		assertThat(page.path("items").size()).isEqualTo(2);
		assertThat(page.path("totalItems").asLong()).isEqualTo(3);
		assertThat(page.path("totalPages").asInt()).isEqualTo(2);
		assertThat(page.path("items").get(0).path("name").asString()).isEqualTo("Banco Alfa");

		JsonNode cards = this.browser.get("/api/v1/accounts?kind=CARD").json();
		assertThat(cards.path("totalItems").asLong()).isEqualTo(1);

		JsonNode desc = this.browser.get("/api/v1/accounts?sort=name,desc").json();
		assertThat(desc.path("items").get(0).path("name").asString()).isEqualTo("Carteira");

		assertThat(this.browser.get("/api/v1/accounts?size=101").status()).isEqualTo(400);
		assertThat(this.browser.get("/api/v1/accounts?page=-1").status()).isEqualTo(400);
		assertThat(this.browser.get("/api/v1/accounts?sort=familyId").code()).isEqualTo("VALIDATION_FAILED");
		assertThat(this.browser.get("/api/v1/accounts?sort=name;drop%20table%20account").status()).isEqualTo(400);
		assertThat(this.browser.get("/api/v1/accounts/nao-e-uuid").code()).isEqualTo("MALFORMED_REQUEST");
	}

	private Browser.Response patchSettings(Map<String, Object> body) {
		return this.browser.patchJson("/api/v1/family/settings", body);
	}

}
