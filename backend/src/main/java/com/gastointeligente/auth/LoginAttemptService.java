package com.gastointeligente.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limita tentativas de login malsucedidas por e-mail e por endereço IP, em memória (instância
 * única do MVP; reiniciar a aplicação zera os contadores). Ao atingir o limite dentro da janela, o
 * login fica bloqueado até a janela expirar, mesmo com a senha correta.
 */
@Service
public class LoginAttemptService {

	private static final int MAX_TRACKED_KEYS = 10_000;

	private final Clock clock;

	private final int maxFailuresPerEmail;

	private final int maxFailuresPerIp;

	private final Duration window;

	private final Map<String, Deque<Instant>> failures = new LinkedHashMap<>(16, 0.75f, true);

	public LoginAttemptService(Clock clock, @Value("${app.security.login.max-failures-per-email:5}") int maxPerEmail,
			@Value("${app.security.login.max-failures-per-ip:30}") int maxPerIp,
			@Value("${app.security.login.window:15m}") Duration window) {
		this.clock = clock;
		this.maxFailuresPerEmail = maxPerEmail;
		this.maxFailuresPerIp = maxPerIp;
		this.window = window;
	}

	public synchronized boolean isBlocked(String email, String ip) {
		Instant now = this.clock.instant();
		return count(emailKey(email), now) >= this.maxFailuresPerEmail
				|| count(ipKey(ip), now) >= this.maxFailuresPerIp;
	}

	public synchronized void recordFailure(String email, String ip) {
		Instant now = this.clock.instant();
		add(emailKey(email), now);
		add(ipKey(ip), now);
	}

	/** Sucesso zera o contador do e-mail; o do IP continua valendo até expirar. */
	public synchronized void recordSuccess(String email) {
		this.failures.remove(emailKey(email));
	}

	/** Uso administrativo e de testes. */
	public synchronized void reset() {
		this.failures.clear();
	}

	private int count(String key, Instant now) {
		Deque<Instant> entries = this.failures.get(key);
		if (entries == null) {
			return 0;
		}
		prune(entries, now);
		if (entries.isEmpty()) {
			this.failures.remove(key);
			return 0;
		}
		return entries.size();
	}

	private void add(String key, Instant now) {
		Deque<Instant> entries = this.failures.computeIfAbsent(key, (k) -> new ArrayDeque<>());
		prune(entries, now);
		entries.addLast(now);
		evictIfNeeded();
	}

	private void prune(Deque<Instant> entries, Instant now) {
		Instant limit = now.minus(this.window);
		while (!entries.isEmpty() && !entries.peekFirst().isAfter(limit)) {
			entries.pollFirst();
		}
	}

	private void evictIfNeeded() {
		Iterator<String> it = this.failures.keySet().iterator();
		while (this.failures.size() > MAX_TRACKED_KEYS && it.hasNext()) {
			it.next();
			it.remove();
		}
	}

	private static String emailKey(String email) {
		return "e:" + AppUser.normalizeEmail(email);
	}

	private static String ipKey(String ip) {
		return "i:" + ((ip == null) ? "" : ip);
	}

}
