package com.gastointeligente.household;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "family")
public class Family {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected Family() {
	}

	public Family(String name, Instant now) {
		this.id = UUID.randomUUID();
		this.name = name.strip();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

}
