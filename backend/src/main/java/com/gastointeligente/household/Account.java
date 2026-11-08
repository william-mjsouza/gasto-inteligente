package com.gastointeligente.household;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Conta de caixa ou cartão. Dados bancários sensíveis (agência, número) não são necessários. */
@Entity
@Table(name = "account")
public class Account {

	@Id
	private UUID id;

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AccountKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected Account() {
	}

	public Account(UUID familyId, String name, AccountKind kind, Instant now) {
		this.id = UUID.randomUUID();
		this.familyId = familyId;
		this.name = name;
		this.kind = kind;
		this.status = AccountStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void rename(String name, Instant now) {
		this.name = name;
		this.updatedAt = now;
	}

	public void changeStatus(AccountStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public String getName() {
		return this.name;
	}

	public AccountKind getKind() {
		return this.kind;
	}

	public AccountStatus getStatus() {
		return this.status;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Long getVersion() {
		return this.version;
	}

}
