package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 成本模块变更证据：凭证/归集/转池分摊/结转/差异/审核所有写操作留痕，只追加，操作者不丢。
 */
@TableName("t_cost_change_evidence")
public class CostChangeEvidence extends BaseEntity {

    public static final String ENTITY_VOUCHER = "COST_VOUCHER";
    public static final String ENTITY_ENTRY = "COST_ENTRY";
    public static final String ENTITY_TRANSFER = "COST_TRANSFER";
    public static final String ENTITY_CARRYOVER = "COST_CARRYOVER";
    public static final String ENTITY_VARIANCE = "COST_VARIANCE";
    public static final String ENTITY_POND = "POND";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_BACKFILL = "BACKFILL";
    public static final String ACTION_REVOKE = "REVOKE";
    public static final String ACTION_ALLOCATE = "ALLOCATE";
    public static final String ACTION_CARRYOVER = "CARRYOVER";
    public static final String ACTION_RAISE = "RAISE";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_DISABLE = "DISABLE";
    public static final String ACTION_ENABLE = "ENABLE";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
