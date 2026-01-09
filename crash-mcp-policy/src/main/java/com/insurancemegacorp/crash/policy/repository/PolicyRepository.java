package com.insurancemegacorp.crash.policy.repository;

import com.insurancemegacorp.crash.policy.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyRepository extends JpaRepository<PolicyEntity, Integer> {
}
