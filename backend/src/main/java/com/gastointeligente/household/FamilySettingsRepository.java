package com.gastointeligente.household;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySettingsRepository extends JpaRepository<FamilySettings, UUID> {

}
