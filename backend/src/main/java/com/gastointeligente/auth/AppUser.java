package com.gastointeligente.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Column(name = "security_stamp", nullable = false)
	private UUID securityStamp;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected AppUser() {
	}

	public AppUser(String email, String displayName, String passwordHash, Instant now) {
		this.id = UUID.randomUUID();
		this.email = normalizeEmail(email);
		this.displayName = displayName.strip();
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
		this.securityStamp = UUID.randomUUID();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static String normalizeEmail(String email) {
		return (email == null) ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	/** Troca a senha e invalida todas as sessões existentes do usuário. */
	public void changePassword(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.securityStamp = UUID.randomUUID();
		this.updatedAt = now;
	}

	/** Reativa o usuário (recuperação administrativa). */
	public void activate(Instant now) {
		this.status = UserStatus.ACTIVE;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public UserStatus getStatus() {
		return this.status;
	}

	public UUID getSecurityStamp() {
		return this.securityStamp;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Long getVersion() {
		return this.version;
	}

}
