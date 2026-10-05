package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本归集明细：饲料/人工/药品/能源四类成本按池塘+批次一行一笔。
 * entry_type DIRECT 当期归集 / BACKFILL 人工补录——两种都必须引用 ACTIVE 凭证，人工补录强校验。
 * closed_flag=TRUE 表示该笔已被转池分摊消耗或出池结转；未结余额只统计未关闭行。
 */
@TableName("t_cost_entry")
public class CostEntry extends BaseEntity {

    public static final String ENTRY_DIRECT = "DIRECT";
    public static final String ENTRY_BACKFILL = "BACKFILL";

    private String entryNo;
    private Long pondId;
    private Long batchId;
    private String costCategory;
    private BigDecimal amount;
    private LocalDate costDate;
    private String entryType;
    private Long voucherId;
    private Boolean closedFlag;
    private Long allocatedTransferId;
    private Long carryoverId;
    private String recordedBy;
    private String requestId;
    private String remark;

    public String getEntryNo() { return entryNo; }
    public void setEntryNo(String entryNo) { this.entryNo = entryNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getCostDate() { return costDate; }
    public void setCostDate(LocalDate costDate) { this.costDate = costDate; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
    public Boolean getClosedFlag() { return closedFlag; }
    public void setClosedFlag(Boolean closedFlag) { this.closedFlag = closedFlag; }
    public Long getAllocatedTransferId() { return allocatedTransferId; }
    public void setAllocatedTransferId(Long allocatedTransferId) { this.allocatedTransferId = allocatedTransferId; }
    public Long getCarryoverId() { return carryoverId; }
    public void setCarryoverId(Long carryoverId) { this.carryoverId = carryoverId; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
