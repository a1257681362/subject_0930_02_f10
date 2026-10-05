package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.service.CostReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 养殖成本分摊与批次结转——页面（HTML）。
 * 与后台 JSON、CSV 导出共用 CostReportService：行集合、停用池塘过滤、财务/生产字段口径完全一致。
 */
@Controller
@RequestMapping("/aqua/cost")
public class CostReportPageController {

    private final CostReportService reportService;

    public CostReportPageController(CostReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/carryover")
    public String carryover(@RequestParam(defaultValue = "FINANCE") String role,
                            @RequestParam(required = false) Long pondId,
                            @RequestParam(required = false) Long batchId,
                            @RequestParam(defaultValue = "false") boolean includeDisabled,
                            Model model) {
        boolean finance = CostReportService.isFinance(role);
        List<Map<String, Object>> batchRows = reportService.batchRows(role, pondId, batchId, includeDisabled);
        List<Map<String, Object>> pondRows = reportService.pondSummary(role, includeDisabled);
        model.addAttribute("finance", finance);
        model.addAttribute("role", finance ? CostReportService.ROLE_FINANCE : CostReportService.ROLE_PRODUCTION);
        model.addAttribute("includeDisabled", includeDisabled);
        model.addAttribute("pondId", pondId);
        model.addAttribute("batchId", batchId);
        model.addAttribute("batchRows", batchRows);
        model.addAttribute("pondRows", pondRows);
        return "aqua/cost/carryover";
    }
}
