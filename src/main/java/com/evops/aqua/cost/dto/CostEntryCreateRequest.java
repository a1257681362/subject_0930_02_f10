package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本归集请求：四类成本按池塘+批次归集。
 * entryType=DIRECT 当期归集 / BACKFILL 人工补录；两种都必须带有效凭证（voucherId），
 * 人工补录额外强校验凭证与类别一致且处于 ACTIVE。
 */
public class CostEntryCreateRequest {

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    @NotNull(message = "养殖批次不能为空")
    private Long batchId;

    @NotBlank(message = "成本类别不能为空")
    private String costCategory;

    @NotNull(message = "成本金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "成本发生日期不能为空")
    private LocalDate costDate;

    /** DIRECT / BACKFILL，缺省为 DIRECT。 */
    private String entryType;

    @NotNull(message = "成本必须引用凭证（凭证id不能为空）")
    private Long voucherId;

    @NotBlank(message = "登记人不能为空")
    private String recordedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

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
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
