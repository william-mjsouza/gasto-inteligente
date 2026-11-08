package com.gastointeligente.household;

import java.util.List;
import java.util.Map;

import com.gastointeligente.auth.LoginAttemptService;
import com.gastointeligente.support.Browser;
import com.gastointeligente.support.HttpIntegrationTest;
import com.gastointeligente.support.TestFamilies;
import com.gastointeligente.support.TestFamilies.TestFamily;
import com.gastointeligente.support.TestFamilies.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento entre famílias: um usuário da família A não lê, altera, exclui nem administra objetos
 * da família B trocando IDs, e o familyId nunca vem do cliente.
 */
@HttpIntegrationTest
class FamilyIsolationIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestFamilies families;

	@Autowired
	private LoginAttemptService attempts;

	@Autowired
	private JdbcTemplate jdbc;

	private TestFamily familyA;

	private TestFamily familyB;

	private Browser adminA;

	private Browser adminB;

	@BeforeEach
	void setUp() {
		this.attempts.reset();
		this.familyA = this.families.createFamily("Família A");
		this.familyB = this.families.createFamily("Família B");
		this.adminA = new Browser(this.port);
		this.adminA.loginOrFail(this.familyA.admin().email(), TestFamilies.PASSWORD);
		this.adminB = new Browser(this.port);
		this.adminB.loginOrFail(this.familyB.admin().email(), TestFamilies.PASSWORD);
	}

	@Test
	void accountsOfAnotherFamilyAreInvisibleAndImmutable() {
		JsonNode accountB = this.adminB.postJson("/api/v1/accounts", Map.of("name", "Cartão B", "kind", "CARD"))
			.json();
		String id = accountB.path("id").asString();
		long version = accountB.path("version").asLong();

		for (Browser.Response response : List.of(this.adminA.get("/api/v1/accounts/" + id),
				this.adminA.patchJson("/api/v1/accounts/" + id, Map.of("name", "Invadida", "version", version)),
				this.adminA.patchJson("/api/v1/accounts/" + id, Map.of("status", "ARCHIVED", "version", version)),
				this.adminA.delete("/api/v1/accounts/" + id + "?version=" + version))) {
			assertThat(response.status()).isEqualTo(404);
			assertThat(response.code()).isEqualTo("NOT_FOUND");
		}

		JsonNode listA = this.adminA.get("/api/v1/accounts").json();
		assertThat(listA.path("items").toString()).doesNotContain(id, "Cartão B");

		JsonNode stillB = this.adminB.get("/api/v1/accounts/" + id).json();
		assertThat(stillB.path("name").asString()).isEqualTo("Cartão B");
		assertThat(stillB.path("status").asString()).isEqualTo("ACTIVE");
		assertThat(stillB.path("version").asLong()).isEqualTo(version);
	}

	@Test
	void familyIdSentByClientIsIgnored() {
		Browser.Response created = this.adminA.postJson("/api/v1/accounts",
				Map.of("name", "Conta forjada", "kind", "CASH", "familyId", this.familyB.familyId().toString()));

		// Propriedades desconhecidas são rejeitadas; o familyId vem apenas da sessão.
		assertThat(created.status()).isEqualTo(400);
		assertThat(created.code()).isEqualTo("MALFORMED_REQUEST");
		Integer inB = this.jdbc.queryForObject("select count(*) from account where family_id = ?", Integer.class,
				this.familyB.familyId());
		assertThat(inB).isZero();
	}

	@Test
	void settingsAreScopedToOwnFamily() {
		JsonNode settingsA = this.adminA.get("/api/v1/family/settings").json();
		Browser.Response updated = this.adminA.patchJson("/api/v1/family/settings",
				Map.of("prudentMarginPercent", "15.50", "version", settingsA.path("version").asLong()));
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.json().path("prudentMarginPercent").asString()).isEqualTo("15.50");

		JsonNode settingsB = this.adminB.get("/api/v1/family/settings").json();
		assertThat(settingsB.path("prudentMarginPercent").asString()).isEqualTo("10.00");
	}

	@Test
	void adminCannotManageMembersOfAnotherFamily() {
		TestUser memberB = this.families.addMember(this.familyB, MembershipRole.MEMBER);
		String path = "/api/v1/members/" + memberB.membershipId();

		for (Browser.Response response : List.of(this.adminA.get(path),
				this.adminA.patchJson(path, Map.of("role", "ADMIN", "version", 0)),
				this.adminA.patchJson(path, Map.of("status", "DISABLED", "version", 0)),
				this.adminA.postJson(path + "/password", Map.of("newPassword", "senha-sequestrada-1")))) {
			assertThat(response.status()).isEqualTo(404);
		}
		assertThat(this.adminA.get("/api/v1/members").json().path("items").toString())
			.doesNotContain(memberB.email());

		Browser memberBrowser = new Browser(this.port);
		assertThat(memberBrowser.login(memberB.email(), TestFamilies.PASSWORD).status()).isEqualTo(200);
		assertThat(memberBrowser.get("/api/v1/auth/me").json().path("role").asString()).isEqualTo("MEMBER");
	}

	@Test
	void meReturnsOnlyOwnFamily() {
		assertThat(this.adminA.get("/api/v1/auth/me").json().path("family").path("id").asString())
			.isEqualTo(this.familyA.familyId().toString());
		assertThat(this.adminB.get("/api/v1/auth/me").json().path("family").path("id").asString())
			.isEqualTo(this.familyB.familyId().toString());
	}

	@Test
	void writesWithoutCsrfAreRejectedEvenWhenAuthenticated() {
		JsonNode account = this.adminA.postJson("/api/v1/accounts", Map.of("name", "Conta CSRF", "kind", "CASH"))
			.json();
		String path = "/api/v1/accounts/" + account.path("id").asString();
		long version = account.path("version").asLong();

		for (Browser.Response response : List.of(
				this.adminA.postJsonWithoutCsrf("/api/v1/accounts", Map.of("name", "Sem token", "kind", "CASH")),
				this.adminA.patchJsonWithoutCsrf(path, Map.of("name", "Sem token", "version", version)),
				this.adminA.deleteWithoutCsrf(path + "?version=" + version),
				this.adminA.patchJsonWithoutCsrf("/api/v1/family/settings", Map.of("version", 0)))) {
			assertThat(response.status()).isEqualTo(403);
			assertThat(response.code()).isEqualTo("CSRF_INVALID");
		}
		assertThat(this.adminA.get(path).json().path("name").asString()).isEqualTo("Conta CSRF");
	}

}
