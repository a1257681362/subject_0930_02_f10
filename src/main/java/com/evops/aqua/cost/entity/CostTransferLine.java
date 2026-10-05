package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 转池分配腿：每条腿是一个成本落点池塘（可含来源池自身=留存成本）。
 * weight_ratio = 0.5*数量占比 + 0.5*生物量占比；四类金额按权重分摊，尾差并入末腿。
 * consumed_by_transfer_id 非空表示该腿余额已被后续转池再次分走（不重复计未结余额）。
 */
@TableName("t_cost_transfer_line")
public class CostTransferLine extends BaseEntity {

    private Long transferId;
    private Long legPondId;
    private Integer quantity;
    private BigDecimal biomassKg;
    private BigDecimal weightRatio;
    private BigDecimal feedAmount;
    private BigDecimal laborAmount;
    private BigDecimal medicineAmount;
    private BigDecimal energyAmount;
    private BigDecimal totalAmount;
    private Long consumedByTransferId;
    private Boolean carriedFlag;
    private Long carriedCarryoverId;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getLegPondId() { return legPondId; }
    public void setLegPondId(Long legPondId) { this.legPondId = legPondId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public BigDecimal getWeightRatio() { return weightRatio; }
    public void setWeightRatio(BigDecimal weightRatio) { this.weightRatio = weightRatio; }
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
    public Long getConsumedByTransferId() { return consumedByTransferId; }
    public void setConsumedByTransferId(Long consumedByTransferId) { this.consumedByTransferId = consumedByTransferId; }
    public Boolean getCarriedFlag() { return carriedFlag; }
    public void setCarriedFlag(Boolean carriedFlag) { this.carriedFlag = carriedFlag; }
    public Long getCarriedCarryoverId() { return carriedCarryoverId; }
    public void setCarriedCarryoverId(Long carriedCarryoverId) { this.carriedCarryoverId = carriedCarryoverId; }
}
