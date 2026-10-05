package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.CostTransferAllocateRequest;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.service.CostTransferService;
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
 * 转池成本分摊入口：针对台账转池记录，按数量+生物量把未结成本分摊到落点池塘。
 */
@RestController
@RequestMapping("/api/aqua/cost/transfers")
public class CostTransferController {

    private final CostTransferService transferService;

    public CostTransferController(CostTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/allocate")
    public ApiResponse<CostTransfer> allocate(@Validated @RequestBody CostTransferAllocateRequest request) {
        return ApiResponse.ok(transferService.allocate(request));
    }

    @GetMapping
    public ApiResponse<List<CostTransfer>> list(@RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(transferService.list(batchId));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<CostTransferLine>> lines(@PathVariable Long id) {
        return ApiResponse.ok(transferService.linesOf(id));
    }
}
