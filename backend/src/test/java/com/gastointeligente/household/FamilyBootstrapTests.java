package com.gastointeligente.household;

import java.util.Map;
import java.util.UUID;

import com.gastointeligente.GastoInteligenteBackendApplication;
import com.gastointeligente.TestcontainersConfiguration;
import com.gastointeligente.auth.AppUserRepository;
import com.gastointeligente.support.TestFamilies;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Criação controlada da família e recuperação administrativa, inclusive pela linha de comando. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestFamilies.class })
class FamilyBootstrapTests {

	@Autowired
	private FamilyBootstrapService bootstrap;

	@Autowired
	private FamilySettingsRepository settings;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PostgreSQLContainer postgres;

	@Test
	void createsFamilyWithAdminAndDefaultSettings() {
		String email = TestFamilies.uniqueEmail("boot");
		var created = this.bootstrap.createFamilyWithAdmin(" Família Nova ", email.toUpperCase(), "Admin",
				"senha-forte-do-admin");

		assertThat(created.family().getName()).isEqualTo("Família Nova");
		assertThat(created.admin().getEmail()).isEqualTo(email);
		assertThat(created.membership().getRole()).isEqualTo(MembershipRole.ADMIN);
		FamilySettings s = this.settings.findById(created.family().getId()).orElseThrow();
		assertThat(s.getCurrency()).isEqualTo("BRL");
		assertThat(s.getTimezone()).isEqualTo("America/Fortaleza");
		assertThat(s.getPrudentMarginPercent()).isEqualByComparingTo("10.00");
	}

	@Test
	void rejectsDuplicateEmailAndWeakPasswordWithoutPartialData() {
		String email = TestFamilies.uniqueEmail("dup");
		this.bootstrap.createFamilyWithAdmin("Primeira", email, "Admin", "senha-forte-do-admin");
		Integer before = this.jdbc.queryForObject("select count(*) from family", Integer.class);

		assertThatThrownBy(() -> this.bootstrap.createFamilyWithAdmin("Segunda", email, "Admin",
				"senha-forte-do-admin")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> this.bootstrap.createFamilyWithAdmin("Terceira", TestFamilies.uniqueEmail("w"),
				"Admin", "curta")).isInstanceOf(IllegalArgumentException.class);

		assertThat(this.jdbc.queryForObject("select count(*) from family", Integer.class)).isEqualTo(before);
	}

	@Test
	void recoverAccessReactivatesPromotesAndRotatesSecurityStamp() {
		TestFamilies.TestFamily f = createFamily();
		UUID stampBefore = this.users.findById(f.admin().userId()).orElseThrow().getSecurityStamp();
		this.jdbc.update("update app_user set status = 'DISABLED' where id = ?", f.admin().userId());
		this.jdbc.update("update family_membership set status = 'DISABLED', role = 'MEMBER' where id = ?",
				f.admin().membershipId());

		this.bootstrap.recoverAdminAccess(f.admin().email(), "senha-recuperada-1");

		var user = this.users.findById(f.admin().userId()).orElseThrow();
		var membership = this.memberships.findById(f.admin().membershipId()).orElseThrow();
		assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
		assertThat(user.getSecurityStamp()).isNotEqualTo(stampBefore);
		assertThat(membership.isActiveAdmin()).isTrue();
	}

	@Test
	void commandLineCreatesFamilyWithPasswordFromEnvironmentOnly() {
		String email = TestFamilies.uniqueEmail("cli");
		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(GastoInteligenteBackendApplication.class)
			.web(WebApplicationType.NONE)
			// A senha vem de ADMIN_PASSWORD (aqui como propriedade, equivalente à variável de ambiente).
			.properties(Map.of("ADMIN_PASSWORD", "senha-pela-variavel"))
			.run("--spring.datasource.url=" + this.postgres.getJdbcUrl(),
					"--spring.datasource.username=" + this.postgres.getUsername(),
					"--spring.datasource.password=" + this.postgres.getPassword(), "--admin.command=create-family",
					"--admin.family-name=Família CLI", "--admin.email=" + email, "--admin.display-name=Admin CLI")) {
			assertThat(ctx.isActive()).isTrue();
		}

		String hash = this.jdbc.queryForObject("select password_hash from app_user where email = ?", String.class,
				email);
		assertThat(hash).startsWith("{bcrypt}").doesNotContain("senha-pela-variavel");
		String role = this.jdbc.queryForObject("""
				select m.role from family_membership m join app_user u on u.id = m.user_id where u.email = ?
				""", String.class, email);
		assertThat(role).isEqualTo("ADMIN");
	}

	private TestFamilies.TestFamily createFamily() {
		String email = TestFamilies.uniqueEmail("rec");
		var created = this.bootstrap.createFamilyWithAdmin("Recuperação", email, "Admin", "senha-forte-do-admin");
		return new TestFamilies.TestFamily(created.family().getId(),
				new TestFamilies.TestUser(email, created.admin().getId(), created.membership().getId()));
	}

}
