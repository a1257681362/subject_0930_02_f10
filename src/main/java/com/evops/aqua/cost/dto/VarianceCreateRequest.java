package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 成本差异单创建请求：实盘金额与账面金额不一致即开单。
 * 账面金额由系统按批次（优先取结转快照，无快照则取未结归集）计算，调用方只给实盘金额，差异待审核。
 */
public class VarianceCreateRequest {

    @NotNull(message = "养殖批次不能为空")
    private Long batchId;

    /** 可空：指定类别则按该类别账面核对；为空按批次总额核对。 */
    private String costCategory;

    @NotNull(message = "实盘金额不能为空")
    private BigDecimal actualAmount;

    @NotBlank(message = "差异原因不能为空")
    private String reason;

    @NotBlank(message = "提报人不能为空")
    private String raisedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
