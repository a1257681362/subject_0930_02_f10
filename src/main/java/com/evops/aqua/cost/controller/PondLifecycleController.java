package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.service.CostEvidenceService;
import com.evops.aqua.cost.service.PondLifecycleService;
import com.evops.aqua.dto.PondStatusRequest;
import com.evops.aqua.entity.Pond;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 海水养殖管理对象（养殖池）置无效/恢复有效入口。
 * 停用后历史台账与结转快照保留，成本分摊与批次结转的页面/导出/后台默认一致地不再显示该对象。
 */
@RestController
@RequestMapping("/api/aqua/cost/ponds")
public class PondLifecycleController {

    private final PondLifecycleService lifecycleService;
    private final CostEvidenceService evidenceService;

    public PondLifecycleController(PondLifecycleService lifecycleService,
                                   CostEvidenceService evidenceService) {
        this.lifecycleService = lifecycleService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{id}")
    public ApiResponse<Pond> detail(@PathVariable Long id) {
        return ApiResponse.ok(lifecycleService.getOrThrow(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Pond> disable(@PathVariable Long id,
                                     @Validated @RequestBody PondStatusRequest request) {
        return ApiResponse.ok(lifecycleService.disable(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Pond> enable(@PathVariable Long id,
                                    @Validated @RequestBody PondStatusRequest request) {
        return ApiResponse.ok(lifecycleService.enable(id, request));
    }

    @GetMapping("/{id}/evidence")
    public ApiResponse<java.util.List<CostChangeEvidence>> evidence(@PathVariable Long id) {
        return ApiResponse.ok(evidenceService.list(CostChangeEvidence.ENTITY_POND, id, null));
    }
}
