package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.service.CostReportService;
import com.evops.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 养殖成本分摊与批次结转——后台（JSON）与导出（CSV）。
 * 与页面（HTML）共用 CostReportService 同一查询：行集合、停用池塘过滤、财务/生产字段口径完全一致。
 */
@RestController
@RequestMapping("/api/aqua/cost/report")
public class CostReportApiController {

    private final CostReportService reportService;

    public CostReportApiController(CostReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/batches")
    public ApiResponse<List<Map<String, Object>>> batches(@RequestParam(defaultValue = "FINANCE") String role,
                                                          @RequestParam(required = false) Long pondId,
                                                          @RequestParam(required = false) Long batchId,
                                                          @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(reportService.batchRows(role, pondId, batchId, includeDisabled));
    }

    @GetMapping("/ponds")
    public ApiResponse<List<Map<String, Object>>> ponds(@RequestParam(defaultValue = "FINANCE") String role,
                                                        @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(reportService.pondSummary(role, includeDisabled));
    }

    @GetMapping(value = "/batches.csv")
    public ResponseEntity<byte[]> batchesCsv(@RequestParam(defaultValue = "FINANCE") String role,
                                             @RequestParam(required = false) Long pondId,
                                             @RequestParam(required = false) Long batchId,
                                             @RequestParam(defaultValue = "false") boolean includeDisabled) {
        List<Map<String, Object>> rows = reportService.batchRows(role, pondId, batchId, includeDisabled);
        boolean finance = CostReportService.isFinance(role);
        StringBuilder sb = new StringBuilder();
        if (finance) {
            sb.append("批次编号,品种,池塘编号,池塘名称,池塘状态,批次状态,成本状态,数量,生物量kg,饲料金额,人工金额,药品金额,能源金额,总成本,单位成本,结转日期\r\n");
        } else {
            sb.append("批次编号,品种,池塘编号,池塘名称,池塘状态,批次状态,成本状态,数量,生物量kg,归集笔数,分摊次数,结转日期\r\n");
        }
        for (Map<String, Object> r : rows) {
            sb.append(cell(r.get("batchNo"))).append(',')
              .append(cell(r.get("species"))).append(',')
              .append(cell(r.get("pondCode"))).append(',')
              .append(cell(r.get("pondName"))).append(',')
              .append(cell(r.get("pondStatus"))).append(',')
              .append(cell(r.get("batchStatus"))).append(',')
              .append(cell(r.get("costStatus"))).append(',')
              .append(cell(r.get("quantity"))).append(',')
              .append(cell(r.get("biomassKg")));
            if (finance) {
                sb.append(',').append(cell(r.get("feedAmount")))
                  .append(',').append(cell(r.get("laborAmount")))
                  .append(',').append(cell(r.get("medicineAmount")))
                  .append(',').append(cell(r.get("energyAmount")))
                  .append(',').append(cell(r.get("totalAmount")))
                  .append(',').append(cell(r.get("unitCost")))
                  .append(',').append(cell(r.get("carryoverDate")));
            } else {
                sb.append(',').append(cell(r.get("entryCount")))
                  .append(',').append(cell(r.get("transferCount")))
                  .append(',').append(cell(r.get("carryoverDate")));
            }
            sb.append("\r\n");
        }
        return csv("cost-batch-carryover-" + role.toLowerCase() + ".csv", sb.toString());
    }

    @GetMapping(value = "/ponds.csv")
    public ResponseEntity<byte[]> pondsCsv(@RequestParam(defaultValue = "FINANCE") String role,
                                           @RequestParam(defaultValue = "false") boolean includeDisabled) {
        List<Map<String, Object>> rows = reportService.pondSummary(role, includeDisabled);
        boolean finance = CostReportService.isFinance(role);
        StringBuilder sb = new StringBuilder();
        if (finance) {
            sb.append("池塘编号,池塘名称,池塘状态,饲料金额,人工金额,药品金额,能源金额,总成本\r\n");
        } else {
            sb.append("池塘编号,池塘名称,池塘状态\r\n");
        }
        for (Map<String, Object> r : rows) {
            sb.append(cell(r.get("pondCode"))).append(',')
              .append(cell(r.get("pondName"))).append(',')
              .append(cell(r.get("pondStatus")));
            if (finance) {
                sb.append(',').append(cell(r.get("feedAmount")))
                  .append(',').append(cell(r.get("laborAmount")))
                  .append(',').append(cell(r.get("medicineAmount")))
                  .append(',').append(cell(r.get("energyAmount")))
                  .append(',').append(cell(r.get("totalAmount")));
            }
            sb.append("\r\n");
        }
        return csv("cost-pond-summary-" + role.toLowerCase() + ".csv", sb.toString());
    }

    private ResponseEntity<byte[]> csv(String filename, String content) {
        // 加 UTF-8 BOM，Excel 直接打开中文不乱码。
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(headers).body(all);
    }

    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
