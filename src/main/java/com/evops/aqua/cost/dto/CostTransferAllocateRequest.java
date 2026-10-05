package com.evops.aqua.cost.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 转池成本分配请求：针对一次台账转池记录，把该批全部未结成本按「数量+生物量」分摊到各腿。
 * 每条腿是一个成本落点池塘（分池时可多条；整池转移时一条），携带该落点的数量与生物量。
 */
public class CostTransferAllocateRequest {

    @NotNull(message = "台账转池记录不能为空")
    private Long transferRecordId;

    @NotEmpty(message = "至少一条分配腿")
    @Valid
    private List<AllocationLegRequest> legs;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public Long getTransferRecordId() { return transferRecordId; }
    public void setTransferRecordId(Long transferRecordId) { this.transferRecordId = transferRecordId; }
    public List<AllocationLegRequest> getLegs() { return legs; }
    public void setLegs(List<AllocationLegRequest> legs) { this.legs = legs; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    /** 分配腿：落点池塘 + 该落点数量 + 该落点生物量（kg）。 */
    public static class AllocationLegRequest {

        @NotNull(message = "落点池塘不能为空")
        private Long pondId;

        @NotNull(message = "落点数量不能为空")
        private Integer quantity;

        @NotNull(message = "落点生物量不能为空")
        private BigDecimal biomassKg;

        public Long getPondId() { return pondId; }
        public void setPondId(Long pondId) { this.pondId = pondId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getBiomassKg() { return biomassKg; }
        public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    }
}
