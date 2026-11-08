package com.gastointeligente.household;

import java.io.Console;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Comandos administrativos executados pela linha de comando (não há registro público):
 *
 * <pre>
 * --spring.main.web-application-type=none --admin.command=create-family \
 *     --admin.family-name="Família Exemplo" --admin.email=admin@exemplo.test --admin.display-name="Admin"
 *
 * --spring.main.web-application-type=none --admin.command=recover-access --admin.email=admin@exemplo.test
 * </pre>
 *
 * A senha nunca é passada como argumento: vem da variável de ambiente {@code ADMIN_PASSWORD} ou é
 * digitada no console. Nada é impresso sobre a senha.
 */
@Component
@ConditionalOnProperty(name = "admin.command")
public class AdminCommandRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminCommandRunner.class);

	private final FamilyBootstrapService bootstrap;

	private final Environment env;

	public AdminCommandRunner(FamilyBootstrapService bootstrap, Environment env) {
		this.bootstrap = bootstrap;
		this.env = env;
	}

	@Override
	public void run(ApplicationArguments args) {
		String command = this.env.getRequiredProperty("admin.command");
		switch (command) {
			case "create-family" -> {
				var created = this.bootstrap.createFamilyWithAdmin(this.env.getProperty("admin.family-name"),
						this.env.getProperty("admin.email"), this.env.getProperty("admin.display-name"), password());
				log.info("Família criada: id={} administrador userId={}", created.family().getId(),
						created.admin().getId());
			}
			case "recover-access" -> {
				this.bootstrap.recoverAdminAccess(this.env.getProperty("admin.email"), password());
				log.info("Acesso administrativo recuperado; sessões anteriores do usuário foram revogadas.");
			}
			default -> throw new IllegalArgumentException(
					"Comando desconhecido: use create-family ou recover-access.");
		}
	}

	private String password() {
		String fromEnv = this.env.getProperty("ADMIN_PASSWORD");
		if (fromEnv != null && !fromEnv.isEmpty()) {
			return fromEnv;
		}
		Console console = System.console();
		if (console == null) {
			throw new IllegalStateException("Defina ADMIN_PASSWORD ou execute em um terminal interativo.");
		}
		char[] first = console.readPassword("Senha do administrador: ");
		char[] second = console.readPassword("Confirme a senha: ");
		try {
			if (first == null || !Arrays.equals(first, second)) {
				throw new IllegalArgumentException("As senhas não conferem.");
			}
			return new String(first);
		}
		finally {
			if (first != null) {
				Arrays.fill(first, '\0');
			}
			if (second != null) {
				Arrays.fill(second, '\0');
			}
		}
	}

}
