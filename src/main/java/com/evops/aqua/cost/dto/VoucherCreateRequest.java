package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本凭证登记请求：四类成本凭据。requestId 幂等，重复登记返回首条。
 */
public class VoucherCreateRequest {

    @NotBlank(message = "凭证编号不能为空")
    private String voucherNo;

    /** FEED/LABOR/MEDICINE/ENERGY */
    @NotBlank(message = "成本类别不能为空")
    private String costCategory;

    private String sourceDoc;

    @NotNull(message = "凭证金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "凭证日期不能为空")
    private LocalDate voucherDate;

    @NotBlank(message = "登记人不能为空")
    private String recordedBy;

    @NotBlank(message = "请求号不能为空")
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
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
