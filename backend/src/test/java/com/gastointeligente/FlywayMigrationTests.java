package com.gastointeligente;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Migrações executam do zero em PostgreSQL real e nenhuma fica pendente. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Flyway flyway;

	@Test
	void runsAgainstRealPostgresWithExpectedMajorVersion() {
		String product = this.jdbc.queryForObject("select version()", String.class);
		Integer versionNum = this.jdbc.queryForObject("select current_setting('server_version_num')::int",
				Integer.class);

		assertThat(product).startsWith("PostgreSQL");
		assertThat(versionNum / 10000).isEqualTo(18);
	}

	@Test
	void appliesAllMigrationsSuccessfullyFromEmptyDatabase() {
		List<Map<String, Object>> history = this.jdbc
			.queryForList("select version, success from flyway_schema_history where version is not null order by installed_rank");

		assertThat(history).isNotEmpty();
		assertThat(history.get(0)).containsEntry("version", "1");
		assertThat(history).allSatisfy((row) -> assertThat(row).containsEntry("success", true));
		assertThat(this.flyway.info().pending()).isEmpty();
		assertThat(this.flyway.info().current()).extracting(MigrationInfo::getVersion)
			.hasToString(String.valueOf(history.get(history.size() - 1).get("version")));
	}

}
