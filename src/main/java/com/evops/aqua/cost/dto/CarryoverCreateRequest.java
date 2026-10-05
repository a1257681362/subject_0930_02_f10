package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 出池结转请求：批次出池后执行结转，形成不可变快照。
 * 数量取该批出池记录，期末生物量（kg）由现场提供用于单位成本与历史留痕。
 */
public class CarryoverCreateRequest {

    /** 期末生物量（kg），可空（不影响金额结转，仅影响快照展示）。 */
    private BigDecimal biomassKg;

    @NotBlank(message = "结转人不能为空")
    private String carriedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public String getCarriedBy() { return carriedBy; }
    public void setCarriedBy(String carriedBy) { this.carriedBy = carriedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
