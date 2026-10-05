package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成本差异单：实盘金额与账面金额不一致即开单，一律 PENDING 待审核。
 * 审核（通过/驳回）只改本单状态，绝不回改结转快照与归集行。
 */
@TableName("t_cost_variance")
public class CostVariance extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String varianceNo;
    private Long carryoverId;
    private Long batchId;
    private String costCategory;
    private BigDecimal bookAmount;
    private BigDecimal actualAmount;
    private BigDecimal diffAmount;
    private String reason;
    private String status;
    private String raisedBy;
    private String reviewedBy;
    private String reviewRemark;
    private LocalDateTime reviewedTime;
    private String requestId;
    private String remark;

    public String getVarianceNo() { return varianceNo; }
    public void setVarianceNo(String varianceNo) { this.varianceNo = varianceNo; }
    public Long getCarryoverId() { return carryoverId; }
    public void setCarryoverId(Long carryoverId) { this.carryoverId = carryoverId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
    public BigDecimal getBookAmount() { return bookAmount; }
    public void setBookAmount(BigDecimal bookAmount) { this.bookAmount = bookAmount; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
    public BigDecimal getDiffAmount() { return diffAmount; }
    public void setDiffAmount(BigDecimal diffAmount) { this.diffAmount = diffAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
