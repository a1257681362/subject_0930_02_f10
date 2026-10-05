package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;

/**
 * 差异单审核请求（通过/驳回共用）：审核人与审核意见缺一不可。
 * 审核只改变差异单状态，绝不回改结转快照与归集行。
 */
public class VarianceReviewRequest {

    @NotBlank(message = "审核人不能为空")
    private String reviewedBy;

    @NotBlank(message = "审核意见不能为空")
    private String reviewRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
