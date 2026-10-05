package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.CostEntryCreateRequest;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.service.CostEntryService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成本归集入口：饲料/人工/药品/能源按池塘+批次归集；当期归集与人工补录都必须引用有效凭证。
 */
@RestController
@RequestMapping("/api/aqua/cost/entries")
public class CostEntryController {

    private final CostEntryService entryService;

    public CostEntryController(CostEntryService entryService) {
        this.entryService = entryService;
    }

    @PostMapping
    public ApiResponse<CostEntry> create(@Validated @RequestBody CostEntryCreateRequest request) {
        return ApiResponse.ok(entryService.create(request));
    }

    @GetMapping
    public ApiResponse<List<CostEntry>> list(@RequestParam(required = false) Long pondId,
                                             @RequestParam(required = false) Long batchId,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) Boolean closed) {
        return ApiResponse.ok(entryService.list(pondId, batchId, category, closed));
    }
}
