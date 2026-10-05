package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成本凭证：饲料发票、工资单、药品发票、能源（电费/油费）单据等财务凭据。
 * 任何成本金额都必须能追溯到一条 ACTIVE 凭证；凭证不做物理删除，只能作废（REVOKED）。
 */
@TableName("t_cost_voucher")
public class CostVoucher extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    /** 四类成本：饲料 / 人工 / 药品 / 能源。 */
    public static final String CATEGORY_FEED = "FEED";
    public static final String CATEGORY_LABOR = "LABOR";
    public static final String CATEGORY_MEDICINE = "MEDICINE";
    public static final String CATEGORY_ENERGY = "ENERGY";

    private String voucherNo;
    private String costCategory;
    private String sourceDoc;
    private BigDecimal amount;
    private LocalDate voucherDate;
    private String status;
    private String recordedBy;
    private String revokedBy;
    private String revokeReason;
    private LocalDateTime revokedTime;
    private String requestId;
    private String remark;

    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public LocalDateTime getRevokedTime() { return revokedTime; }
    public void setRevokedTime(LocalDateTime revokedTime) { this.revokedTime = revokedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
