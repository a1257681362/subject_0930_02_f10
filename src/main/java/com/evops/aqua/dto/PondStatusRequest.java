package com.evops.aqua.dto;

import javax.validation.constraints.NotBlank;

/**
 * 养殖池停用（置无效）/启用请求：必须登记操作人与原因，全程留证。
 */
public class PondStatusRequest {

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "原因不能为空")
    private String reason;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
