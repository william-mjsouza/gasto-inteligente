package com.gastointeligente.shared.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro imutável de alteração relevante. Nunca contém senha, token, cookie ou dado bruto importado. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

	@Id
	private UUID id;

	@Column(name = "family_id")
	private UUID familyId;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private String action;

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String details;

	protected AuditEvent() {
	}

	AuditEvent(UUID familyId, UUID actorUserId, Instant occurredAt, String action, String entityType,
			UUID entityId, String details) {
		this.id = UUID.randomUUID();
		this.familyId = familyId;
		this.actorUserId = actorUserId;
		this.occurredAt = occurredAt;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.details = details;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public UUID getActorUserId() {
		return this.actorUserId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getAction() {
		return this.action;
	}

	public String getEntityType() {
		return this.entityType;
	}

	public UUID getEntityId() {
		return this.entityId;
	}

	public String getDetails() {
		return this.details;
	}

}
