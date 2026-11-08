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

/** Autorização ADMIN/MEMBER e revalidação de sessões após mudanças de acesso. */
@HttpIntegrationTest
class AccessControlIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestFamilies families;

	@Autowired
	private LoginAttemptService attempts;

	@Autowired
	private JdbcTemplate jdbc;

	private TestFamily family;

	@BeforeEach
	void setUp() {
		this.attempts.reset();
		this.family = this.families.createFamily("Família Acesso");
	}

	private Browser loggedIn(String email) {
		Browser browser = new Browser(this.port);
		browser.loginOrFail(email, TestFamilies.PASSWORD);
		return browser;
	}

	@Test
	void memberCanManageFinancesButNotAccess() {
		TestUser member = this.families.addMember(this.family, MembershipRole.MEMBER);
		Browser browser = loggedIn(member.email());

		assertThat(browser.get("/api/v1/family/settings").status()).isEqualTo(200);
		assertThat(browser.postJson("/api/v1/accounts", Map.of("name", "Conta do membro", "kind", "CASH"))
			.status()).isEqualTo(201);

		for (Browser.Response response : List.of(browser.get("/api/v1/members"),
				browser.get("/api/v1/members/" + this.family.admin().membershipId()),
				browser.postJson("/api/v1/members",
						Map.of("email", TestFamilies.uniqueEmail("x"), "displayName", "X", "role", "ADMIN",
								"initialPassword", "frase valida de teste")),
				browser.patchJson("/api/v1/members/" + member.membershipId(), Map.of("role", "ADMIN", "version", 0)),
				browser.postJson("/api/v1/members/" + this.family.admin().membershipId() + "/password",
						Map.of("newPassword", "frase tomada de teste")))) {
			assertThat(response.status()).isEqualTo(403);
			assertThat(response.code()).isEqualTo("FORBIDDEN");
		}
		Integer admins = this.jdbc.queryForObject(
				"select count(*) from family_membership where family_id = ? and role = 'ADMIN'", Integer.class,
				this.family.familyId());
		assertThat(admins).isEqualTo(1);
		// A senha do administrador não foi alterada.
		assertThat(new Browser(this.port).login(this.family.admin().email(), TestFamilies.PASSWORD).status())
			.isEqualTo(200);
	}

	@Test
	void adminCreatesMemberWhoCanLogInWithInitialPassword() {
		Browser admin = loggedIn(this.family.admin().email());
		String email = TestFamilies.uniqueEmail("novo");

		Browser.Response created = admin.postJson("/api/v1/members", Map.of("email", email.toUpperCase(),
				"displayName", "Novo Membro", "role", "MEMBER", "initialPassword", "senha-inicial-123"));

		assertThat(created.status()).isEqualTo(201);
		assertThat(created.json().path("email").asString()).isEqualTo(email);
		assertThat(created.json().path("role").asString()).isEqualTo("MEMBER");
		assertThat(created.body()).doesNotContain("senha-inicial-123", "password");

		Browser member = new Browser(this.port);
		assertThat(member.login(email, "senha-inicial-123").status()).isEqualTo(200);
		assertThat(member.get("/api/v1/auth/me").json().path("role").asString()).isEqualTo("MEMBER");

		JsonNode list = admin.get("/api/v1/members").json();
		assertThat(list.path("totalItems").asLong()).isEqualTo(2);
	}

	@Test
	void creatingMemberValidatesInputAndRejectsExistingEmailGenerically() {
		Browser admin = loggedIn(this.family.admin().email());
		TestFamily other = this.families.createFamily("Outra família");

		Browser.Response weak = admin.postJson("/api/v1/members", Map.of("email", TestFamilies.uniqueEmail("a"),
				"displayName", "A", "role", "MEMBER", "initialPassword", "curta"));
		assertThat(weak.status()).isEqualTo(400);
		assertThat(weak.json().path("details").get(0).path("field").asString()).isEqualTo("initialPassword");

		Browser.Response invalidEmail = admin.postJson("/api/v1/members",
				Map.of("email", "nao-e-email", "displayName", "A", "role", "MEMBER", "initialPassword",
						"frase valida de teste"));
		assertThat(invalidEmail.status()).isEqualTo(400);

		Browser.Response taken = admin.postJson("/api/v1/members", Map.of("email", other.admin().email(),
				"displayName", "A", "role", "MEMBER", "initialPassword", "frase valida de teste"));
		assertThat(taken.status()).isEqualTo(409);
		assertThat(taken.code()).isEqualTo("EMAIL_UNAVAILABLE");
		assertThat(taken.body()).doesNotContain("Outra família", other.familyId().toString());
	}

	@Test
	void disablingMemberRevokesExistingSessionAndBlocksLogin() {
		TestUser member = this.families.addMember(this.family, MembershipRole.MEMBER);
		Browser memberBrowser = loggedIn(member.email());
		Browser admin = loggedIn(this.family.admin().email());
		long version = admin.get("/api/v1/members/" + member.membershipId()).json().path("version").asLong();

		Browser.Response disabled = admin.patchJson("/api/v1/members/" + member.membershipId(),
				Map.of("status", "DISABLED", "version", version));
		assertThat(disabled.status()).isEqualTo(200);

		Browser.Response afterDisable = memberBrowser.get("/api/v1/accounts");
		assertThat(afterDisable.status()).isEqualTo(401);
		assertThat(new Browser(this.port).login(member.email(), TestFamilies.PASSWORD).code())
			.isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void roleChangesApplyToExistingSessions() {
		TestUser member = this.families.addMember(this.family, MembershipRole.MEMBER);
		Browser memberBrowser = loggedIn(member.email());
		Browser admin = loggedIn(this.family.admin().email());
		assertThat(memberBrowser.get("/api/v1/members").status()).isEqualTo(403);

		long version = admin.get("/api/v1/members/" + member.membershipId()).json().path("version").asLong();
		admin.patchJson("/api/v1/members/" + member.membershipId(), Map.of("role", "ADMIN", "version", version));
		assertThat(memberBrowser.get("/api/v1/members").status()).isEqualTo(200);
		assertThat(memberBrowser.get("/api/v1/auth/me").json().path("role").asString()).isEqualTo("ADMIN");

		version = admin.get("/api/v1/members/" + member.membershipId()).json().path("version").asLong();
		admin.patchJson("/api/v1/members/" + member.membershipId(), Map.of("role", "MEMBER", "version", version));
		assertThat(memberBrowser.get("/api/v1/members").status()).isEqualTo(403);
	}

	@Test
	void lastActiveAdminCannotBeDemotedOrDisabled() {
		Browser admin = loggedIn(this.family.admin().email());
		String path = "/api/v1/members/" + this.family.admin().membershipId();
		long version = admin.get(path).json().path("version").asLong();

		Browser.Response demote = admin.patchJson(path, Map.of("role", "MEMBER", "version", version));
		Browser.Response disable = admin.patchJson(path, Map.of("status", "DISABLED", "version", version));

		assertThat(demote.status()).isEqualTo(409);
		assertThat(demote.code()).isEqualTo("LAST_ADMIN");
		assertThat(disable.code()).isEqualTo("LAST_ADMIN");
		assertThat(admin.get(path).json().path("role").asString()).isEqualTo("ADMIN");
		assertThat(admin.get(path).json().path("version").asLong()).isEqualTo(version);

		// Com outro administrador ativo, o rebaixamento é permitido.
		TestUser second = this.families.addMember(this.family, MembershipRole.ADMIN);
		assertThat(admin.patchJson(path, Map.of("role", "MEMBER", "version", version)).status()).isEqualTo(200);
		assertThat(admin.get("/api/v1/members").status()).isEqualTo(403);
		assertThat(loggedIn(second.email()).get("/api/v1/members").status()).isEqualTo(200);
	}

	@Test
	void staleVersionIsRejectedWithConflict() {
		TestUser member = this.families.addMember(this.family, MembershipRole.MEMBER);
		Browser admin = loggedIn(this.family.admin().email());
		String path = "/api/v1/members/" + member.membershipId();
		long version = admin.get(path).json().path("version").asLong();

		assertThat(admin.patchJson(path, Map.of("role", "ADMIN", "version", version)).status()).isEqualTo(200);
		Browser.Response stale = admin.patchJson(path, Map.of("role", "MEMBER", "version", version));

		assertThat(stale.status()).isEqualTo(409);
		assertThat(stale.code()).isEqualTo("VERSION_CONFLICT");
		assertThat(admin.get(path).json().path("role").asString()).isEqualTo("ADMIN");
	}

	@Test
	void adminPasswordResetRevokesMemberSessions() {
		TestUser member = this.families.addMember(this.family, MembershipRole.MEMBER);
		Browser memberBrowser = loggedIn(member.email());
		Browser admin = loggedIn(this.family.admin().email());

		Browser.Response reset = admin.postJson("/api/v1/members/" + member.membershipId() + "/password",
				Map.of("newPassword", "redefinida-pelo-admin"));

		assertThat(reset.status()).isEqualTo(204);
		assertThat(memberBrowser.get("/api/v1/auth/me").status()).isEqualTo(401);
		assertThat(new Browser(this.port).login(member.email(), "redefinida-pelo-admin").status()).isEqualTo(200);
		String details = this.jdbc.queryForObject(
				"select details::text from audit_event where action = 'PASSWORD_RESET' and entity_id = ?",
				String.class, member.userId());
		assertThat(details).doesNotContain("redefinida-pelo-admin");
	}

}
