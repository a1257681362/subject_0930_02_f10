package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 结转快照明细：按 类别×池塘 冻结金额，出池后历史成本仍可按池塘追溯。
 */
@TableName("t_cost_carryover_line")
public class CostCarryoverLine extends BaseEntity {

    private Long carryoverId;
    private String costCategory;
    private Long pondId;
    private BigDecimal amount;

    public Long getCarryoverId() { return carryoverId; }
    public void setCarryoverId(Long carryoverId) { this.carryoverId = carryoverId; }
    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
