package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.VarianceCreateRequest;
import com.evops.aqua.cost.dto.VarianceReviewRequest;
import com.evops.aqua.cost.entity.CostVariance;
import com.evops.aqua.cost.service.CostVarianceService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成本差异入口：提报（一律待审核）/审核通过/驳回。审核只改变差异单状态，不回改快照与归集。
 */
@RestController
@RequestMapping("/api/aqua/cost/variances")
public class CostVarianceController {

    private final CostVarianceService varianceService;

    public CostVarianceController(CostVarianceService varianceService) {
        this.varianceService = varianceService;
    }

    @PostMapping
    public ApiResponse<CostVariance> raise(@Validated @RequestBody VarianceCreateRequest request) {
        return ApiResponse.ok(varianceService.raise(request));
    }

    @GetMapping
    public ApiResponse<List<CostVariance>> list(@RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(varianceService.list(batchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CostVariance> detail(@PathVariable Long id) {
        return ApiResponse.ok(varianceService.getOrThrow(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<CostVariance> approve(@PathVariable Long id,
                                             @Validated @RequestBody VarianceReviewRequest request) {
        return ApiResponse.ok(varianceService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CostVariance> reject(@PathVariable Long id,
                                            @Validated @RequestBody VarianceReviewRequest request) {
        return ApiResponse.ok(varianceService.reject(id, request));
    }
}
