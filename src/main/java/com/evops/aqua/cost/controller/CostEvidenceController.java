package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.service.CostEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成本模块变更证据查询：按对象或 requestId 追溯。
 */
@RestController
@RequestMapping("/api/aqua/cost/evidence")
public class CostEvidenceController {

    private final CostEvidenceService evidenceService;

    public CostEvidenceController(CostEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<CostChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                      @RequestParam(required = false) Long entityId,
                                                      @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
