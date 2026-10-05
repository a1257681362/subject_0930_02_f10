package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.CarryoverCreateRequest;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.service.CostCarryoverService;
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
 * 批次结转入口：出池时同事务自动形成不可变快照；此处提供快照查询与（特殊情况下的）手工结转。
 */
@RestController
@RequestMapping("/api/aqua/cost/carryovers")
public class CostCarryoverController {

    private final CostCarryoverService carryoverService;

    public CostCarryoverController(CostCarryoverService carryoverService) {
        this.carryoverService = carryoverService;
    }

    @GetMapping
    public ApiResponse<List<CostCarryover>> list(@RequestParam(required = false) Long pondId,
                                                 @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(carryoverService.list(pondId, batchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CostCarryover> detail(@PathVariable Long id) {
        return ApiResponse.ok(carryoverService.getOrThrow(id));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<CostCarryoverLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(carryoverService.linesOf(id));
    }

    @PostMapping("/batch/{batchId}")
    public ApiResponse<CostCarryover> manualCarryover(@PathVariable Long batchId,
                                                      @Validated @RequestBody CarryoverCreateRequest request) {
        return ApiResponse.ok(carryoverService.manualCarryover(batchId, request));
    }
}
