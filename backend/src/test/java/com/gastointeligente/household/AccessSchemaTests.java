package com.gastointeligente.household;

import java.util.UUID;

import com.gastointeligente.TestcontainersConfiguration;
import com.gastointeligente.support.TestFamilies;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restrições do banco (V2) verificadas diretamente no PostgreSQL, independentemente da API. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestFamilies.class })
class AccessSchemaTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TestFamilies families;

	private void insertUser(UUID id, String email) {
		this.jdbc.update("""
				insert into app_user (id, email, display_name, password_hash, status, security_stamp,
				                      created_at, updated_at, version)
				values (?, ?, 'Pessoa', '{bcrypt}x', 'ACTIVE', gen_random_uuid(), now(), now(), 0)
				""", id, email);
	}

	@Test
	void emailMustBeNormalizedAndUnique() {
		String email = TestFamilies.uniqueEmail("schema");
		insertUser(UUID.randomUUID(), email);

		assertThatThrownBy(() -> insertUser(UUID.randomUUID(), email)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertUser(UUID.randomUUID(), "Maiusculo@Exemplo.test"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertUser(UUID.randomUUID(), " espaco@exemplo.test"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void userBelongsToAtMostOneFamilyAndRolesAreConstrained() {
		TestFamilies.TestFamily a = this.families.createFamily("Schema A");
		TestFamilies.TestFamily b = this.families.createFamily("Schema B");

		assertThatThrownBy(() -> this.jdbc.update("""
				insert into family_membership (id, family_id, user_id, role, status, created_at, updated_at, version)
				values (gen_random_uuid(), ?, ?, 'MEMBER', 'ACTIVE', now(), now(), 0)
				""", b.familyId(), a.admin().userId())).isInstanceOf(DataIntegrityViolationException.class);

		UUID user = UUID.randomUUID();
		insertUser(user, TestFamilies.uniqueEmail("role"));
		assertThatThrownBy(() -> this.jdbc.update("""
				insert into family_membership (id, family_id, user_id, role, status, created_at, updated_at, version)
				values (gen_random_uuid(), ?, ?, 'OWNER', 'ACTIVE', now(), now(), 0)
				""", a.familyId(), user)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void settingsAndAccountConstraints() {
		TestFamilies.TestFamily f = this.families.createFamily("Schema C");

		assertThatThrownBy(() -> this.jdbc.update("update family_settings set currency = 'USD' where family_id = ?",
				f.familyId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update(
				"update family_settings set prudent_margin_percent = 100.01 where family_id = ?", f.familyId()))
			.isInstanceOf(DataIntegrityViolationException.class);
		String column = this.jdbc.queryForObject("""
				select data_type || '(' || numeric_precision || ',' || numeric_scale || ')'
				from information_schema.columns
				where table_name = 'family_settings' and column_name = 'prudent_margin_percent'
				""", String.class);
		assertThat(column).isEqualTo("numeric(5,2)");

		String insertAccount = """
				insert into account (id, family_id, name, kind, status, created_at, updated_at, version)
				values (gen_random_uuid(), ?, ?, ?, 'ACTIVE', now(), now(), 0)
				""";
		this.jdbc.update(insertAccount, f.familyId(), "Conta", "CASH");
		assertThatThrownBy(() -> this.jdbc.update(insertAccount, f.familyId(), "CONTA", "CARD"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update(insertAccount, f.familyId(), "Poupança", "SAVINGS"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update(insertAccount, UUID.randomUUID(), "Órfã", "CASH"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
