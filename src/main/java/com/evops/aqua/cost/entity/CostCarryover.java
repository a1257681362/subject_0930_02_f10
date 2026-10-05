package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 出池结转快照（头）：每批至多一条（uk batch_id）。出池同事务生成，冻结金额与单位成本，永不回改。
 */
@TableName("t_cost_carryover")
public class CostCarryover extends BaseEntity {

    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private String carryoverNo;
    private Long batchId;
    private Long pondId;
    private Long harvestRecordId;
    private LocalDate carryoverDate;
    private Integer quantity;
    private BigDecimal biomassKg;
    private BigDecimal feedAmount;
    private BigDecimal laborAmount;
    private BigDecimal medicineAmount;
    private BigDecimal energyAmount;
    private BigDecimal totalAmount;
    private BigDecimal unitCost;
    private String snapshotJson;
    private String status;
    private String carriedBy;
    private String requestId;
    private String remark;

    public String getCarryoverNo() { return carryoverNo; }
    public void setCarryoverNo(String carryoverNo) { this.carryoverNo = carryoverNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getHarvestRecordId() { return harvestRecordId; }
    public void setHarvestRecordId(Long harvestRecordId) { this.harvestRecordId = harvestRecordId; }
    public LocalDate getCarryoverDate() { return carryoverDate; }
    public void setCarryoverDate(LocalDate carryoverDate) { this.carryoverDate = carryoverDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public BigDecimal getFeedAmount() { return feedAmount; }
    public void setFeedAmount(BigDecimal feedAmount) { this.feedAmount = feedAmount; }
    public BigDecimal getLaborAmount() { return laborAmount; }
    public void setLaborAmount(BigDecimal laborAmount) { this.laborAmount = laborAmount; }
    public BigDecimal getMedicineAmount() { return medicineAmount; }
    public void setMedicineAmount(BigDecimal medicineAmount) { this.medicineAmount = medicineAmount; }
    public BigDecimal getEnergyAmount() { return energyAmount; }
    public void setEnergyAmount(BigDecimal energyAmount) { this.energyAmount = energyAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCarriedBy() { return carriedBy; }
    public void setCarriedBy(String carriedBy) { this.carriedBy = carriedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
