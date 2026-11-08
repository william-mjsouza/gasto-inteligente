package com.gastointeligente.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.gastointeligente.household.MembershipRole;
import com.gastointeligente.support.Browser;
import com.gastointeligente.support.HttpIntegrationTest;
import com.gastointeligente.support.MutableClock;
import com.gastointeligente.support.TestFamilies;
import com.gastointeligente.support.TestFamilies.TestFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Login com sessão no servidor, CSRF, logout, limite de tentativas e troca de senha. */
@HttpIntegrationTest
class AuthFlowIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestFamilies families;

	@Autowired
	private LoginAttemptService attempts;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JdbcTemplate jdbc;

	private TestFamily family;

	@BeforeEach
	void setUp() {
		this.attempts.reset();
		this.family = this.families.createFamily("Família Login");
	}

	private Browser browser() {
		return new Browser(this.port);
	}

	@Test
	void csrfEndpointIsPublicAndReturnsHeaderAndToken() {
		Browser browser = browser();
		Browser.Response response = browser.get("/api/v1/auth/csrf");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.json().path("headerName").asString()).isEqualTo("X-CSRF-TOKEN");
		assertThat(response.json().path("token").asString()).isNotBlank();
		// Token CSRF e cookie de sessão são elementos diferentes.
		assertThat(response.json().path("token").asString()).isNotEqualTo(browser.sessionId());
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() {
		Browser browser = browser();
		Browser.Response response = browser.postForm("/api/v1/auth/login",
				Map.of("email", this.family.admin().email(), "password", TestFamilies.PASSWORD), false);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.code()).isEqualTo("CSRF_INVALID");
		assertThat(browser.get("/api/v1/auth/me").status()).isEqualTo(401);
	}

	@Test
	void successfulLoginRotatesSessionAndCsrfTokenAndReturnsUser() {
		Browser browser = browser();
		String oldToken = browser.refreshCsrf();
		String anonymousSession = browser.sessionId();

		Browser.Response login = browser.login(this.family.admin().email(), TestFamilies.PASSWORD);

		assertThat(login.status()).isEqualTo(200);
		assertThat(login.json().path("user").path("email").asString()).isEqualTo(this.family.admin().email());
		assertThat(login.json().path("user").path("role").asString()).isEqualTo("ADMIN");
		assertThat(login.json().path("user").path("family").path("id").asString())
			.isEqualTo(this.family.familyId().toString());
		assertThat(login.body()).doesNotContain("password", "passwordHash", "securityStamp");
		assertThat(browser.sessionId()).isNotNull().isNotEqualTo(anonymousSession);
		String newToken = browser.csrfToken();
		assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);

		Browser.Response me = browser.get("/api/v1/auth/me");
		assertThat(me.status()).isEqualTo(200);
		assertThat(me.json().path("family").path("name").asString()).isEqualTo("Família Login");

		// O token anterior ao login deixou de valer para operações mutáveis.
		browser.useCsrfToken(oldToken);
		assertThat(browser.postJson("/api/v1/accounts", Map.of("name", "Conta", "kind", "CASH")).code())
			.isEqualTo("CSRF_INVALID");
		browser.useCsrfToken(newToken);
		assertThat(browser.postJson("/api/v1/accounts", Map.of("name", "Conta", "kind", "CASH")).status())
			.isEqualTo(201);
	}

	@Test
	void sessionCookieIsHttpOnlyWithSameSite() {
		Browser browser = browser();
		Browser.Response response = browser.get("/api/v1/auth/csrf");
		String setCookie = response.raw().headers().allValues("Set-Cookie")
			.stream()
			.filter((v) -> v.startsWith(Browser.SESSION_COOKIE + "="))
			.findFirst()
			.orElseThrow();

		assertThat(setCookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
	}

	@Test
	void loginIsCaseInsensitiveOnEmail() {
		Browser browser = browser();
		assertThat(browser.login("  " + this.family.admin().email().toUpperCase() + " ", TestFamilies.PASSWORD)
			.status()).isEqualTo(200);
	}

	@Test
	void failuresAreGenericAndIndistinguishable() {
		TestFamilies.TestUser disabled = this.families.addMember(this.family, MembershipRole.MEMBER);
		this.jdbc.update("update family_membership set status = 'DISABLED' where id = ?", disabled.membershipId());

		Browser.Response wrongPassword = browser().login(this.family.admin().email(), "senha-errada-123456");
		Browser.Response unknownEmail = browser().login("ninguem@exemplo.test", TestFamilies.PASSWORD);
		Browser.Response disabledMember = browser().login(disabled.email(), TestFamilies.PASSWORD);

		for (Browser.Response response : List.of(wrongPassword, unknownEmail, disabledMember)) {
			assertThat(response.status()).isEqualTo(401);
			assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
			assertThat(response.json().path("message").asString()).isEqualTo("E-mail ou senha inválidos.");
		}
	}

	@Test
	void repeatedFailuresBlockLoginEvenWithCorrectPasswordUntilWindowExpires() {
		String email = this.family.admin().email();
		for (int i = 0; i < 5; i++) {
			assertThat(browser().login(email, "senha-errada-123456").status()).isEqualTo(401);
		}

		Browser.Response blocked = browser().login(email, TestFamilies.PASSWORD);
		assertThat(blocked.status()).isEqualTo(429);
		assertThat(blocked.code()).isEqualTo("TOO_MANY_ATTEMPTS");

		this.clock.advance(Duration.ofMinutes(16));
		assertThat(browser().login(email, TestFamilies.PASSWORD).status()).isEqualTo(200);
	}

	@Test
	void logoutRequiresCsrfAndInvalidatesSession() {
		Browser browser = browser();
		browser.loginOrFail(this.family.admin().email(), TestFamilies.PASSWORD);
		String authenticatedSession = browser.sessionId();

		Browser.Response withoutCsrf = browser.postForm("/api/v1/auth/logout", Map.of(), false);
		assertThat(withoutCsrf.status()).isEqualTo(403);
		assertThat(browser.get("/api/v1/auth/me").status()).isEqualTo(200);

		assertThat(browser.logout().status()).isEqualTo(204);
		assertThat(browser.get("/api/v1/auth/me").status()).isEqualTo(401);

		// Reutilizar o identificador da sessão encerrada não devolve acesso.
		Browser replay = browser();
		replay.forceSessionId(authenticatedSession);
		Browser.Response replayed = replay.get("/api/v1/accounts");
		assertThat(replayed.status()).isEqualTo(401);
		assertThat(replayed.code()).isEqualTo("UNAUTHENTICATED");

		// Após o logout, o cliente obtém um novo token CSRF para a nova sessão anônima.
		String afterLogout = browser.refreshCsrf();
		assertThat(afterLogout).isNotBlank();
		assertThat(browser.login(this.family.admin().email(), TestFamilies.PASSWORD).status()).isEqualTo(200);
	}

	@Test
	void unauthenticatedApiAccessIsDenied() {
		Browser browser = browser();
		for (String path : List.of("/api/v1/auth/me", "/api/v1/accounts", "/api/v1/family/settings",
				"/api/v1/members")) {
			Browser.Response response = browser.get(path);
			assertThat(response.status()).as(path).isEqualTo(401);
			assertThat(response.code()).as(path).isEqualTo("UNAUTHENTICATED");
		}
	}

	@Test
	void changingOwnPasswordKeepsCurrentSessionAndRevokesOthers() {
		String email = this.family.admin().email();
		Browser current = browser();
		current.loginOrFail(email, TestFamilies.PASSWORD);
		Browser other = browser();
		other.loginOrFail(email, TestFamilies.PASSWORD);

		Browser.Response wrong = current.postJson("/api/v1/auth/password",
				Map.of("currentPassword", "nao-e-a-senha-1", "newPassword", "nova-senha-segura-1"));
		assertThat(wrong.status()).isEqualTo(400);
		assertThat(wrong.json().path("details").get(0).path("field").asString()).isEqualTo("currentPassword");

		Browser.Response weak = current.postJson("/api/v1/auth/password",
				Map.of("currentPassword", TestFamilies.PASSWORD, "newPassword", "curta"));
		assertThat(weak.status()).isEqualTo(400);

		Browser.Response changed = current.postJson("/api/v1/auth/password",
				Map.of("currentPassword", TestFamilies.PASSWORD, "newPassword", "nova-senha-segura-1"));
		assertThat(changed.status()).isEqualTo(204);

		assertThat(current.get("/api/v1/auth/me").status()).isEqualTo(200);
		assertThat(other.get("/api/v1/auth/me").status()).isEqualTo(401);
		assertThat(browser().login(email, TestFamilies.PASSWORD).status()).isEqualTo(401);
		assertThat(browser().login(email, "nova-senha-segura-1").status()).isEqualTo(200);
	}

	@Test
	void passwordIsStoredHashedNeverInPlainText() {
		String hash = this.jdbc.queryForObject("select password_hash from app_user where id = ?", String.class,
				this.family.admin().userId());

		assertThat(hash).startsWith("{bcrypt}").doesNotContain(TestFamilies.PASSWORD);
	}

	@Test
	void loginAndLogoutAreAudited() {
		Browser browser = browser();
		browser.loginOrFail(this.family.admin().email(), TestFamilies.PASSWORD);
		browser.logout();

		List<String> actions = this.jdbc.queryForList(
				"select action from audit_event where family_id = ? order by occurred_at, action", String.class,
				this.family.familyId());
		assertThat(actions).contains("FAMILY_CREATED", "LOGIN_SUCCEEDED", "LOGOUT");
		List<String> details = this.jdbc.queryForList("select details::text from audit_event where family_id = ?",
				String.class, this.family.familyId());
		assertThat(details).noneMatch((d) -> d.contains(TestFamilies.PASSWORD));
	}

}
