package com.gastointeligente.household;

import java.time.Instant;
import java.util.UUID;

import com.gastointeligente.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Associação de um usuário a uma família, com papel e status. No MVP há uma por usuário. */
@Entity
@Table(name = "family_membership")
public class Membership {

	@Id
	private UUID id;

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MembershipRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MembershipStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected Membership() {
	}

	public Membership(UUID familyId, AppUser user, MembershipRole role, Instant now) {
		this.id = UUID.randomUUID();
		this.familyId = familyId;
		this.user = user;
		this.role = role;
		this.status = MembershipStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isActiveAdmin() {
		return this.role == MembershipRole.ADMIN && this.status == MembershipStatus.ACTIVE;
	}

	public void changeRole(MembershipRole role, Instant now) {
		this.role = role;
		this.updatedAt = now;
	}

	public void changeStatus(MembershipStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public AppUser getUser() {
		return this.user;
	}

	public MembershipRole getRole() {
		return this.role;
	}

	public MembershipStatus getStatus() {
		return this.status;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Long getVersion() {
		return this.version;
	}

}
