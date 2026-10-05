package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.VoucherCreateRequest;
import com.evops.aqua.cost.dto.VoucherRevokeRequest;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.service.CostVoucherService;
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
 * 成本凭证入口：登记（幂等）/作废/查询。任何成本金额都必须追溯到 ACTIVE 凭证。
 */
@RestController
@RequestMapping("/api/aqua/cost/vouchers")
public class CostVoucherController {

    private final CostVoucherService voucherService;

    public CostVoucherController(CostVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping
    public ApiResponse<CostVoucher> create(@Validated @RequestBody VoucherCreateRequest request) {
        return ApiResponse.ok(voucherService.create(request));
    }

    @GetMapping
    public ApiResponse<List<CostVoucher>> list(@RequestParam(required = false) String category,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(voucherService.list(category, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CostVoucher> detail(@PathVariable Long id) {
        return ApiResponse.ok(voucherService.getOrThrow(id));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<CostVoucher> revoke(@PathVariable Long id,
                                           @Validated @RequestBody VoucherRevokeRequest request) {
        return ApiResponse.ok(voucherService.revoke(id, request));
    }
}
