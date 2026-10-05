package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;

/**
 * 凭证作废请求：凭证不做物理删除，只能作废并登记作废人与原因。
 */
public class VoucherRevokeRequest {

    @NotBlank(message = "作废人不能为空")
    private String revokedBy;

    @NotBlank(message = "作废原因不能为空")
    private String revokeReason;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
