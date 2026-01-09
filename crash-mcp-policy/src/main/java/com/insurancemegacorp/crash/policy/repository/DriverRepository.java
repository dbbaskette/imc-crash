package com.insurancemegacorp.crash.policy.repository;

import com.insurancemegacorp.crash.policy.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<DriverEntity, Integer> {

    List<DriverEntity> findByPolicyId(Integer policyId);

    Optional<DriverEntity> findByPolicyIdAndIsPrimaryTrue(Integer policyId);
}
