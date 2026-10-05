package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 转池成本分配单（头）：针对一次台账转池记录，把该批全部未结成本按数量+生物量分摊到各腿。
 */
@TableName("t_cost_transfer")
public class CostTransfer extends BaseEntity {

    public static final String STATUS_ALLOCATED = "ALLOCATED";

    private String transferNo;
    private Long transferRecordId;
    private Long batchId;
    private Long fromPondId;
    private Integer totalQuantity;
    private BigDecimal totalBiomassKg;
    private String status;
    private String operator;
    private String requestId;
    private String remark;

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getTransferRecordId() { return transferRecordId; }
    public void setTransferRecordId(Long transferRecordId) { this.transferRecordId = transferRecordId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getTotalBiomassKg() { return totalBiomassKg; }
    public void setTotalBiomassKg(BigDecimal totalBiomassKg) { this.totalBiomassKg = totalBiomassKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
