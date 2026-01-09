package com.insurancemegacorp.crash.policy.repository;

import com.insurancemegacorp.crash.policy.entity.VehicleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<VehicleEntity, Integer> {

    List<VehicleEntity> findByPolicyId(Integer policyId);

    Optional<VehicleEntity> findByVin(String vin);
}
