package com.gastointeligente.shared.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava eventos de auditoria na mesma transação da alteração auditada. Os detalhes devem conter
 * apenas metadados (por exemplo, papel anterior e novo), nunca senhas, tokens ou conteúdo importado.
 */
@Service
public class AuditService {

	private final AuditEventRepository repository;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public AuditService(AuditEventRepository repository, JsonMapper jsonMapper, Clock clock) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID familyId, UUID actorUserId, String action, String entityType, UUID entityId,
			Map<String, ?> details) {
		String json = this.jsonMapper.writeValueAsString((details == null) ? Map.of() : details);
		this.repository.save(new AuditEvent(familyId, actorUserId, this.clock.instant(), action, entityType,
				entityId, json));
	}

	/** Para eventos sem transação de negócio associada (login e logout). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordStandalone(UUID familyId, UUID actorUserId, String action, String entityType, UUID entityId) {
		record(familyId, actorUserId, action, entityType, entityId, Map.of());
	}

}
