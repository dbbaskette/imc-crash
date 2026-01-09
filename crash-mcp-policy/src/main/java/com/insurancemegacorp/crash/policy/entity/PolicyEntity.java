package com.insurancemegacorp.crash.policy.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
public class PolicyEntity {

    @Id
    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    private String status;

    @Column(name = "has_comprehensive")
    private Boolean hasComprehensive;

    @Column(name = "has_collision")
    private Boolean hasCollision;

    @Column(name = "has_liability")
    private Boolean hasLiability;

    @Column(name = "has_medical")
    private Boolean hasMedical;

    @Column(name = "has_uninsured")
    private Boolean hasUninsured;

    @Column(name = "has_roadside")
    private Boolean hasRoadside;

    @Column(name = "has_rental")
    private Boolean hasRental;

    @Column(name = "deductible")
    private Integer deductible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private CustomerEntity customer;

    // Getters
    public Integer getPolicyId() { return policyId; }
    public Integer getCustomerId() { return customerId; }
    public String getPolicyNumber() { return policyNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public Boolean getHasComprehensive() { return hasComprehensive; }
    public Boolean getHasCollision() { return hasCollision; }
    public Boolean getHasLiability() { return hasLiability; }
    public Boolean getHasMedical() { return hasMedical; }
    public Boolean getHasUninsured() { return hasUninsured; }
    public Boolean getHasRoadside() { return hasRoadside; }
    public Boolean getHasRental() { return hasRental; }
    public Integer getDeductible() { return deductible; }
    public CustomerEntity getCustomer() { return customer; }
}
