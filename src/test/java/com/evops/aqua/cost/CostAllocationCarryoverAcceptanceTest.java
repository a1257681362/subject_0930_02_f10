package com.evops.aqua.cost;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.entity.CostVariance;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostChangeEvidenceMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.cost.mapper.CostVarianceMapper;
import com.evops.aqua.cost.mapper.CostVoucherMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 养殖成本分摊与批次结转验收（aqua-10）。
 * 主线：四类成本凭证 → 按池按批归集（人工补录必须引用凭证）→ 转池按数量+生物量分摊未结成本（守恒、关闭旧点）
 * → 出池同事务形成不可变结转快照 → 已结转批次不能被新单据改写 → 财务看金额/生产只看汇总
 * → 差异待审核且审核不回改快照 → 停用养殖对象后页面/导出/后台同口径隐藏、显式包含同口径恢复 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CostAllocationCarryoverAcceptanceTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PondMapper pondMapper;
    @Autowired private CostVoucherMapper voucherMapper;
    @Autowired private CostEntryMapper entryMapper;
    @Autowired private CostTransferMapper transferMapper;
    @Autowired private CostTransferLineMapper transferLineMapper;
    @Autowired private CostCarryoverMapper carryoverMapper;
    @Autowired private CostCarryoverLineMapper carryoverLineMapper;
    @Autowired private CostVarianceMapper varianceMapper;
    @Autowired private CostChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondA;
    private Long pondB;
    private Long batchId;
    private final String batchNo = "COST-B20261005-001";
    private Long vFeed;
    private Long vLabor;
    private Long vMed;
    private Long vEnergy;
    private Long transferRecordId;
    private Long costTransferId;
    private Long carryoverId;
    private Long batch2Id;
    private Long variance1;
    private Long variance2;

    @Test
    void acceptanceCostAllocationAndCarryover() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建两口池并投苗一个在养批次", () -> {
            pondA = createPond("COST-PA", "成本甲池");
            pondB = createPond("COST-PB", "成本乙池");
            Map<String, Object> body = new HashMap<>();
            body.put("batchNo", batchNo);
            body.put("pondId", pondA);
            body.put("species", "南美白对虾");
            body.put("seedSource", "漳州东山育苗场");
            body.put("quantity", 10000);
            body.put("stockDate", "2026-10-01");
            body.put("plannedHarvestDate", "2027-01-31");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
            assertSuccess(resp, "投苗");
            batchId = resp.path("data").path("id").asLong();
        });

        checkpoint("CP2", "登记四类成本凭证（饲料/人工/药品/能源），requestId幂等", () -> {
            vFeed = createVoucher("COST-V-FEED", "FEED", "1000", "vc-feed");
            vLabor = createVoucher("COST-V-LABOR", "LABOR", "500", "vc-labor");
            vMed = createVoucher("COST-V-MED", "MEDICINE", "200", "vc-med");
            vEnergy = createVoucher("COST-V-ENERGY", "ENERGY", "300", "vc-energy");
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/cost/vouchers",
                    voucherBody("COST-V-FEED", "FEED", "1000", "vc-feed")));
            assertSuccess(again, "凭证幂等");
            check(again.path("data").path("id").asLong() == vFeed, "同requestId重复登记凭证应返回同一条");
        });

        checkpoint("CP3", "按池按批归集：无凭证/补录引用作废或类别不符凭证一律拒绝，补录成功留痕", () -> {
            // DIRECT 不引用凭证：@NotNull 校验拒绝。
            JsonNode noVoucher = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "1000", "DIRECT", null, "ce-novoucher")));
            check(!noVoucher.path("success").asBoolean(true), "不引用凭证的成本归集必须被拒绝");

            // 人工补录不引用凭证：拒绝。
            JsonNode backfillNoVoucher = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "100", "BACKFILL", null, "ce-bf-novoucher")));
            check(!backfillNoVoucher.path("success").asBoolean(true), "人工补录不引用凭证必须被拒绝");

            // 人工补录引用类别不符凭证（FEED 补录引用人工凭证）：拒绝。
            JsonNode wrongCat = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "100", "BACKFILL", vLabor, "ce-bf-wrongcat")));
            check(!wrongCat.path("success").asBoolean(true)
                            && wrongCat.path("message").asText().contains("不一致"),
                    "人工补录引用类别不符凭证应被拒绝：" + wrongCat.path("message").asText());

            // 作废饲料凭证后，补录引用它：拒绝。
            json(call(HttpMethod.POST, "/api/aqua/cost/vouchers/" + vFeed + "/revoke",
                    revokeBody("财务孙", "凭证开错", "vc-feed-revoke")));
            // 重新登记一张饲料凭证供正式使用（旧凭证已作废）。
            vFeed = createVoucher("COST-V-FEED2", "FEED", "1000", "vc-feed2");
            JsonNode revoked = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "100", "BACKFILL",
                            voucherMapper.selectOne(new QueryWrapper<CostVoucher>()
                                    .eq("voucher_no", "COST-V-FEED")).getId(), "ce-bf-revoked")));
            check(!revoked.path("success").asBoolean(true)
                            && revoked.path("message").asText().contains("已作废"),
                    "人工补录引用已作废凭证应被拒绝");

            // 正常四类当期归集。
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "1000", "DIRECT", vFeed, "ce-feed"))), "归集饲料");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "LABOR", "500", "DIRECT", vLabor, "ce-labor"))), "归集人工");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "MEDICINE", "200", "DIRECT", vMed, "ce-med"))), "归集药品");
            assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "ENERGY", "300", "DIRECT", vEnergy, "ce-energy"))), "归集能源");
            // 人工补录一笔饲料 100（引用有效、类别一致、额度内凭证）。
            JsonNode bfOk = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondA, "FEED", "100", "BACKFILL", vFeed, "ce-bf-ok")));
            assertSuccess(bfOk, "人工补录饲料");
            check("BACKFILL".equals(bfOk.path("data").path("entryType").asText())
                            && bfOk.path("data").path("voucherId").asLong() == vFeed,
                    "补录行必须落 BACKFILL 并绑定凭证");

            List<CostEntry> rows = entryMapper.selectList(new QueryWrapper<CostEntry>()
                    .eq("batch_id", batchId).eq("closed_flag", false));
            BigDecimal sum = BigDecimal.ZERO;
            for (CostEntry e : rows) {
                check(e.getVoucherId() != null, "每笔归集都必须引用凭证");
                sum = sum.add(e.getAmount());
            }
            check(rows.size() == 5, "未结归集应为5笔（4类当期+1笔补录），实际=" + rows.size());
            check(sum.compareTo(new BigDecimal("2100")) == 0, "未结归集总额应为2100，实际=" + sum);
        });

        checkpoint("CP4", "台账换池后按数量+生物量分摊未结成本（权重0.5数量+0.5生物量，金额守恒）", () -> {
            Map<String, Object> transfer = new HashMap<>();
            transfer.put("toPondId", pondB);
            transfer.put("transferDate", "2026-11-01");
            transfer.put("reason", "分池稀养");
            JsonNode tr = json(call(HttpMethod.POST,
                    "/api/aqua/batches/" + batchId + "/transfer", transfer));
            assertSuccess(tr, "台账换池");
            transferRecordId = tr.path("data").path("id").asLong();

            List<Map<String, Object>> legs = new ArrayList<>();
            legs.add(leg(pondA, 4000, "300"));   // 数量占比0.4 生物量占比0.3 → 权重0.35
            legs.add(leg(pondB, 6000, "700"));   // 末腿权重0.65
            Map<String, Object> body = new HashMap<>();
            body.put("transferRecordId", transferRecordId);
            body.put("legs", legs);
            body.put("operator", "成本李");
            body.put("requestId", "ct-alloc-1");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/cost/transfers/allocate", body));
            assertSuccess(resp, "转池成本分摊");
            costTransferId = resp.path("data").path("id").asLong();

            List<CostTransferLine> lines = transferLineMapper.selectList(
                    new QueryWrapper<CostTransferLine>().eq("transfer_id", costTransferId)
                            .orderByAsc("id"));
            check(lines.size() == 2, "应生成2条分摊腿，实际=" + lines.size());
            CostTransferLine la = lines.get(0);
            CostTransferLine lb = lines.get(1);
            check(la.getLegPondId().equals(pondA) && lb.getLegPondId().equals(pondB), "分摊腿落点池塘不符");
            check(la.getWeightRatio().compareTo(new BigDecimal("0.350000")) == 0,
                    "甲池权重应为0.35，实际=" + la.getWeightRatio());
            check(lb.getWeightRatio().compareTo(new BigDecimal("0.650000")) == 0,
                    "乙池权重应为0.65，实际=" + lb.getWeightRatio());
            check(la.getFeedAmount().compareTo(new BigDecimal("385.00")) == 0, "甲池饲料应385，实际=" + la.getFeedAmount());
            check(lb.getFeedAmount().compareTo(new BigDecimal("715.00")) == 0, "乙池饲料应715（尾差），实际=" + lb.getFeedAmount());
            check(la.getLaborAmount().compareTo(new BigDecimal("175.00")) == 0, "甲池人工应175");
            check(la.getMedicineAmount().compareTo(new BigDecimal("70.00")) == 0, "甲池药品应70");
            check(la.getEnergyAmount().compareTo(new BigDecimal("105.00")) == 0, "甲池能源应105");
            check(la.getTotalAmount().compareTo(new BigDecimal("735.00")) == 0, "甲池合计应735，实际=" + la.getTotalAmount());
            check(lb.getTotalAmount().compareTo(new BigDecimal("1365.00")) == 0, "乙池合计应1365，实际=" + lb.getTotalAmount());
            check(la.getTotalAmount().add(lb.getTotalAmount()).compareTo(new BigDecimal("2100.00")) == 0,
                    "分配合计必须守恒=2100，实际=" + la.getTotalAmount().add(lb.getTotalAmount()));

            // 旧归集行全部关闭，余额由新腿承接。
            List<CostEntry> stillOpen = entryMapper.selectList(new QueryWrapper<CostEntry>()
                    .eq("batch_id", batchId).eq("closed_flag", false));
            check(stillOpen.isEmpty(), "分摊后原归集行应全部关闭，未关闭数=" + stillOpen.size());
        });

        checkpoint("CP5", "同一转池记录重复分摊幂等，不产生第二张单、不重复加账", () -> {
            List<Map<String, Object>> legs = new ArrayList<>();
            legs.add(leg(pondA, 4000, "300"));
            legs.add(leg(pondB, 6000, "700"));
            Map<String, Object> body = new HashMap<>();
            body.put("transferRecordId", transferRecordId);
            body.put("legs", legs);
            body.put("operator", "成本李");
            body.put("requestId", "ct-alloc-1-retry");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/cost/transfers/allocate", body));
            assertSuccess(resp, "重复分摊幂等");
            check(resp.path("data").path("id").asLong() == costTransferId, "同转池记录重复分摊应返回同一张单");
            Long count = transferMapper.selectCount(new QueryWrapper<CostTransfer>()
                    .eq("transfer_record_id", transferRecordId));
            check(count == 1L, "同一转池记录的分摊单应为1张，实际=" + count);
            Long lineCount = transferLineMapper.selectCount(new QueryWrapper<CostTransferLine>()
                    .eq("transfer_id", costTransferId));
            check(lineCount == 2L, "重复分摊不得多写分摊腿，实际=" + lineCount);
        });

        checkpoint("CP6", "出池同事务形成不可变结转快照（按类别、按池塘冻结，单位成本落账）", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("harvestDate", "2027-01-20");
            body.put("quantity", 9500);
            body.put("remark", "达到规格出池");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches/" + batchId + "/harvest", body));
            assertSuccess(resp, "出池（同事务结转）");

            List<CostCarryover> cos = carryoverMapper.selectList(
                    new QueryWrapper<CostCarryover>().eq("batch_id", batchId));
            check(cos.size() == 1, "出池后应恰好形成1张结转快照，实际=" + cos.size());
            CostCarryover co = cos.get(0);
            carryoverId = co.getId();
            check(co.getPondId().equals(pondB), "快照落点应为出池时所在乙池：" + co.getPondId());
            check(co.getFeedAmount().compareTo(new BigDecimal("1100.00")) == 0, "快照饲料应1100，实际=" + co.getFeedAmount());
            check(co.getLaborAmount().compareTo(new BigDecimal("500.00")) == 0, "快照人工应500");
            check(co.getMedicineAmount().compareTo(new BigDecimal("200.00")) == 0, "快照药品应200");
            check(co.getEnergyAmount().compareTo(new BigDecimal("300.00")) == 0, "快照能源应300");
            check(co.getTotalAmount().compareTo(new BigDecimal("2100.00")) == 0, "快照总额应2100，实际=" + co.getTotalAmount());
            check(co.getUnitCost().compareTo(new BigDecimal("0.2211")) == 0,
                    "单位成本应为2100/9500=0.2211，实际=" + co.getUnitCost());
            check(co.getBiomassKg().compareTo(new BigDecimal("1000.000")) == 0,
                    "快照生物量应为分摊腿合计1000kg，实际=" + co.getBiomassKg());
            check("CONFIRMED".equals(co.getStatus()) && co.getSnapshotJson() != null,
                    "快照应为CONFIRMED且冻结快照JSON");

            List<CostCarryoverLine> snapLines = carryoverLineMapper.selectList(
                    new QueryWrapper<CostCarryoverLine>().eq("carryover_id", carryoverId));
            check(snapLines.size() == 8, "应按2池塘×4类别冻结8行，实际=" + snapLines.size());
            BigDecimal snapTotal = BigDecimal.ZERO;
            for (CostCarryoverLine l : snapLines) {
                snapTotal = snapTotal.add(l.getAmount());
            }
            check(snapTotal.compareTo(new BigDecimal("2100.00")) == 0, "冻结明细合计应2100，实际=" + snapTotal);

            // 分配腿与归集行都已随结转关闭，不再有未结成本。
            Long openLines = transferLineMapper.selectCount(new QueryWrapper<CostTransferLine>()
                    .eq("transfer_id", costTransferId).eq("carried_flag", false));
            check(openLines == 0L, "结转后分摊腿应全部置已结转");
            Long openEntries = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                    .eq("batch_id", batchId).eq("closed_flag", false));
            check(openEntries == 0L, "结转后不应再有未关闭归集行");
        });

        checkpoint("CP7", "已结转批次不能被新单据悄悄改写（新归集拒绝；漏分摊出池后补分摊拒绝；快照不变）", () -> {
            // 主批次已结转：再归集成本必须拒绝。
            JsonNode moreEntry = json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                    entryBody(pondB, "FEED", "999", "DIRECT", vFeed, "ce-after-carried")));
            check(!moreEntry.path("success").asBoolean(true)
                            && moreEntry.path("message").asText().contains("结转"),
                    "已结转批次再归集必须被拒绝：" + moreEntry.path("message").asText());

            // 构造边界：第二批换池但"漏做成本分摊"就出池（自动结转），事后再补分摊单必须拒绝。
            Long pondC = createPond("COST-PC", "成本丙池");
            Long pondD = createPond("COST-PD", "成本丁池");
            batch2Id = null;
            {
                Map<String, Object> stock = new HashMap<>();
                stock.put("batchNo", "COST-B20261005-002");
                stock.put("pondId", pondC);
                stock.put("species", "南美白对虾");
                stock.put("seedSource", "漳州东山育苗场");
                stock.put("quantity", 3000);
                stock.put("stockDate", "2026-10-08");
                stock.put("plannedHarvestDate", "2027-02-28");
                JsonNode s = json(call(HttpMethod.POST, "/api/aqua/batches", stock));
                assertSuccess(s, "第二批投苗");
                batch2Id = s.path("data").path("id").asLong();

                assertSuccess(json(call(HttpMethod.POST, "/api/aqua/cost/entries",
                        entryBody(batch2Id, pondC, "FEED", "200", "DIRECT", vFeed, "ce-b2-feed"))), "第二批归集饲料");

                Map<String, Object> t = new HashMap<>();
                t.put("toPondId", pondD);
                t.put("transferDate", "2026-11-05");
                t.put("reason", "分池");
                JsonNode tr2 = json(call(HttpMethod.POST, "/api/aqua/batches/" + batch2Id + "/transfer", t));
                assertSuccess(tr2, "第二批换池（暂不分摊）");
                Long trRecord2 = tr2.path("data").path("id").asLong();

                Map<String, Object> h = new HashMap<>();
                h.put("harvestDate", "2027-01-25");
                h.put("quantity", 2800);
                assertSuccess(json(call(HttpMethod.POST, "/api/aqua/batches/" + batch2Id + "/harvest", h)),
                        "第二批直接出池（漏分摊，出池自动结转）");

                // 事后对那条从未分摊的转池记录补分摊单：批次已结转，必须拒绝。
                Map<String, Object> body = new HashMap<>();
                body.put("transferRecordId", trRecord2);
                List<Map<String, Object>> legs = new ArrayList<>();
                legs.add(leg(pondC, 1000, "80"));
                legs.add(leg(pondD, 1800, "160"));
                body.put("legs", legs);
                body.put("operator", "成本李");
                body.put("requestId", "ct-b2-after-carried");
                JsonNode lateAlloc = json(call(HttpMethod.POST, "/api/aqua/cost/transfers/allocate", body));
                check(!lateAlloc.path("success").asBoolean(true)
                                && lateAlloc.path("message").asText().contains("结转"),
                        "已结转批次事后补分摊单必须被拒绝：" + lateAlloc.path("message").asText());
                Long allocCount = transferMapper.selectCount(new QueryWrapper<CostTransfer>()
                        .eq("batch_id", batch2Id));
                check(allocCount == 0L, "被拒补分摊不得落单，实际=" + allocCount);
            }

            // 主批次：同一转池记录重复分摊只是幂等返回首单，不新增金额、快照不变（不算改写）。
            Map<String, Object> body = new HashMap<>();
            body.put("transferRecordId", transferRecordId);
            List<Map<String, Object>> legs = new ArrayList<>();
            legs.add(leg(pondB, 10000, "1000"));
            body.put("legs", legs);
            body.put("operator", "成本李");
            body.put("requestId", "ct-after-carried");
            JsonNode idem = json(call(HttpMethod.POST, "/api/aqua/cost/transfers/allocate", body));
            check(idem.path("data").path("id").asLong() == costTransferId,
                    "同一转池记录重复分摊应幂等返回首单而非改写");

            Long entryCount = entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("batch_id", batchId));
            check(entryCount == 5L, "被拒单据不得落归集行，实际=" + entryCount);
            CostCarryover co = carryoverMapper.selectById(carryoverId);
            check(co.getTotalAmount().compareTo(new BigDecimal("2100.00")) == 0,
                    "结转快照金额不得被改写，实际=" + co.getTotalAmount());
        });

        checkpoint("CP8", "财务看金额、生产只看汇总：行集合一致，生产响应不含任何金额字段", () -> {
            JsonNode fin = json(call(HttpMethod.GET,
                    "/api/aqua/cost/report/batches?role=FINANCE&batchId=" + batchId, null));
            assertSuccess(fin, "财务后台报表");
            JsonNode finRow = findRow(fin, batchNo);
            check(finRow != null, "财务报表应含该批");
            check(amt(finRow, "totalAmount").compareTo(new BigDecimal("2100.00")) == 0, "财务应见总额2100");
            check(amt(finRow, "feedAmount").compareTo(new BigDecimal("1100.00")) == 0, "财务应见饲料1100");
            check(amt(finRow, "unitCost").compareTo(new BigDecimal("0.2211")) == 0, "财务应见单位成本");

            JsonNode prod = json(call(HttpMethod.GET,
                    "/api/aqua/cost/report/batches?role=PRODUCTION&batchId=" + batchId, null));
            assertSuccess(prod, "生产后台报表");
            JsonNode prodRow = findRow(prod, batchNo);
            check(prodRow != null, "生产报表行集合应与财务一致（同样含该批）");
            check(prodRow.path("totalAmount").isMissingNode()
                            && prodRow.path("feedAmount").isMissingNode()
                            && prodRow.path("laborAmount").isMissingNode()
                            && prodRow.path("medicineAmount").isMissingNode()
                            && prodRow.path("energyAmount").isMissingNode()
                            && prodRow.path("unitCost").isMissingNode(),
                    "生产视图不得出现任何金额字段");
            check(prodRow.path("quantity").asInt() == 9500, "生产应见出池数量");
            check(prodRow.path("biomassKg").asDouble() == 1000.0, "生产应见生物量");
            check(prodRow.path("entryCount").asInt() == 5, "生产应见归集笔数5");
            check(prodRow.path("transferCount").asInt() == 1, "生产应见分摊次数1");
        });

        checkpoint("CP9", "差异实盘vs账面：一律待审核，审批不全停留，审核不回改快照", () -> {
            variance1 = raiseVariance("2000", "盘点短少100", "盘点周", "cv-1");
            CostVariance v1 = varianceMapper.selectById(variance1);
            check("PENDING".equals(v1.getStatus()), "差异单应待审核");
            check(v1.getBookAmount().compareTo(new BigDecimal("2100.00")) == 0, "账面应取快照2100");
            check(v1.getDiffAmount().compareTo(new BigDecimal("-100.00")) == 0, "差异应为-100，实际=" + v1.getDiffAmount());

            // 审批缺审核人：校验失败，停留 PENDING。
            Map<String, Object> badReview = new HashMap<>();
            badReview.put("reviewRemark", "同意");
            badReview.put("requestId", "cv-1-approve-bad");
            JsonNode denied = json(call(HttpMethod.POST,
                    "/api/aqua/cost/variances/" + variance1 + "/approve", badReview));
            check(!denied.path("success").asBoolean(true), "缺审核人必须被拒绝");
            check("PENDING".equals(varianceMapper.selectById(variance1).getStatus()), "缺审批字段应停留待审核");

            // 完整审核通过。
            JsonNode ok = json(call(HttpMethod.POST, "/api/aqua/cost/variances/" + variance1 + "/approve",
                    reviewBody("财务主管吴", "确认短少见闻属实", "cv-1-approve")));
            assertSuccess(ok, "差异审核通过");
            check("APPROVED".equals(ok.path("data").path("status").asText()), "应转为已通过");
            check("财务主管吴".equals(ok.path("data").path("reviewedBy").asText()), "审核人必须落库");
            check(carryoverMapper.selectById(carryoverId).getTotalAmount().compareTo(new BigDecimal("2100.00")) == 0,
                    "审核差异不得回改结转快照，快照仍2100");
            // 重复审核幂等。
            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/cost/variances/" + variance1 + "/approve",
                    reviewBody("财务主管吴", "重复", "cv-1-approve-again")));
            check(again.path("data") == null || "APPROVED".equals(again.path("data").path("status").asText()),
                    "重复审核应幂等");

            // 第二张差异单驳回。
            variance2 = raiseVariance("2200", "盘盈100", "盘点周", "cv-2");
            JsonNode rej = json(call(HttpMethod.POST, "/api/aqua/cost/variances/" + variance2 + "/reject",
                    reviewBody("财务主管吴", "计量误差，驳回", "cv-2-reject")));
            assertSuccess(rej, "差异驳回");
            check("REJECTED".equals(rej.path("data").path("status").asText()), "应转为已驳回");

            // 实盘=账面不允许开差异单。
            JsonNode noDiff = json(call(HttpMethod.POST, "/api/aqua/cost/variances",
                    varianceBody(batchId, null, "2100", "无差异", "盘点周", "cv-none")));
            check(!noDiff.path("success").asBoolean(true)
                            && noDiff.path("message").asText().contains("一致"),
                    "实盘与账面一致不应开差异单");
        });

        checkpoint("CP10", "停用养殖对象后：页面/导出/后台默认同口径隐藏，显式包含三端同口径恢复", () -> {
            JsonNode dis = json(call(HttpMethod.POST, "/api/aqua/cost/ponds/" + pondB + "/disable",
                    pondStatusBody("场长郑", "海区征用，停用乙池", "pond-b-disable")));
            assertSuccess(dis, "养殖池置无效");
            check("DISABLED".equals(pondMapper.selectById(pondB).getStatus()), "乙池应已置无效");
            check("DISABLED".equals(dis.path("data").path("status").asText()), "接口返回停用状态");

            // 后台 JSON 默认不含落点为停用乙池的该批。
            JsonNode apiHidden = json(call(HttpMethod.GET,
                    "/api/aqua/cost/report/batches?role=FINANCE", null));
            assertSuccess(apiHidden, "后台报表默认");
            check(findRow(apiHidden, batchNo) == null, "后台默认应隐藏停用池对象的批次");
            JsonNode apiShown = json(call(HttpMethod.GET,
                    "/api/aqua/cost/report/batches?role=FINANCE&includeDisabled=true", null));
            check(findRow(apiShown, batchNo) != null, "后台显式包含应恢复显示该批");

            // 导出 CSV 默认不含、显式包含则含（与后台同口径）。
            String csvHidden = csv("/api/aqua/cost/report/batches.csv?role=FINANCE");
            check(!csvHidden.contains(batchNo), "导出默认应隐藏停用池批次");
            String csvShown = csv("/api/aqua/cost/report/batches.csv?role=FINANCE&includeDisabled=true");
            check(csvShown.contains(batchNo), "导出显式包含应恢复显示该批");
            check(csvShown.contains("2100.00"), "财务导出应含金额");

            // 页面 HTML 默认不含、显式包含则含（与后台/导出同口径）。
            String pageHidden = page("/aqua/cost/carryover?role=FINANCE");
            check(!pageHidden.contains(batchNo), "页面默认应隐藏停用池批次");
            String pageShown = page("/aqua/cost/carryover?role=FINANCE&includeDisabled=true");
            check(pageShown.contains(batchNo), "页面显式包含应恢复显示该批");

            // 池塘汇总同口径：默认无乙池，包含后有乙池。
            JsonNode pondsHidden = json(call(HttpMethod.GET, "/api/aqua/cost/report/ponds?role=FINANCE", null));
            check(!containsPond(pondsHidden, "COST-PB"), "池塘汇总默认应不含停用乙池");
            JsonNode pondsShown = json(call(HttpMethod.GET,
                    "/api/aqua/cost/report/ponds?role=FINANCE&includeDisabled=true", null));
            check(containsPond(pondsShown, "COST-PB"), "池塘汇总显式包含应含乙池");

            // 历史快照不删除：库中仍可查，金额保持 2100。
            CostCarryover kept = carryoverMapper.selectById(carryoverId);
            check(kept != null && kept.getTotalAmount().compareTo(new BigDecimal("2100.00")) == 0,
                    "停用对象不得删除历史结转快照");

            // 生产导出：同口径隐藏，且生产导出根本没有金额列/金额值。
            String prodCsv = csv("/api/aqua/cost/report/batches.csv?role=PRODUCTION&includeDisabled=true");
            check(prodCsv.contains(batchNo), "生产导出显式包含应见该批");
            check(!prodCsv.contains("总成本") && !prodCsv.contains("2100.00"),
                    "生产导出不得包含金额列与金额值");
        });

        checkpoint("CP11", "全程留证：凭证/归集/补录/分摊/结转/差异/停用均可追溯且带操作者", () -> {
            check(countEvidence(CostChangeEvidence.ACTION_CREATE, CostChangeEvidence.ENTITY_VOUCHER) >= 5,
                    "凭证登记证据缺失（含补开的饲料凭证）");
            check(countEvidence(CostChangeEvidence.ACTION_REVOKE, CostChangeEvidence.ENTITY_VOUCHER) >= 1,
                    "凭证作废证据缺失");
            check(countEvidence(CostChangeEvidence.ACTION_BACKFILL, CostChangeEvidence.ENTITY_ENTRY) >= 1,
                    "人工补录证据缺失");
            check(countEvidence(CostChangeEvidence.ACTION_ALLOCATE, CostChangeEvidence.ENTITY_TRANSFER) == 1,
                    "转池分摊证据应为1条");
            // 结转证据按本测试的两个批次限定（全量套件中其他模块出池也会生成各自快照，属正常）。
            List<Long> myCarryoverIds = new ArrayList<>();
            carryoverMapper.selectList(new QueryWrapper<CostCarryover>()
                    .in("batch_id", batchId, batch2Id)).forEach(c -> myCarryoverIds.add(c.getId()));
            check(myCarryoverIds.size() == 2, "本测试两批应各有1张结转快照，实际=" + myCarryoverIds.size());
            long carryoverEvidence = evidenceMapper.selectCount(new QueryWrapper<CostChangeEvidence>()
                    .eq("entity_type", CostChangeEvidence.ENTITY_CARRYOVER)
                    .eq("action", CostChangeEvidence.ACTION_CARRYOVER)
                    .in("entity_id", myCarryoverIds));
            check(carryoverEvidence == 2, "两批结转证据应为2条，实际=" + carryoverEvidence);
            check(countEvidence(CostChangeEvidence.ACTION_RAISE, CostChangeEvidence.ENTITY_VARIANCE) >= 2,
                    "差异提报证据应≥2");
            check(countEvidence(CostChangeEvidence.ACTION_APPROVE, CostChangeEvidence.ENTITY_VARIANCE) == 1,
                    "差异通过证据应为1");
            check(countEvidence(CostChangeEvidence.ACTION_REJECT, CostChangeEvidence.ENTITY_VARIANCE) == 1,
                    "差异驳回证据应为1");
            check(countEvidence(CostChangeEvidence.ACTION_DISABLE, CostChangeEvidence.ENTITY_POND) >= 1,
                    "停用对象证据缺失");

            List<CostChangeEvidence> all = evidenceMapper.selectList(new QueryWrapper<>());
            List<CostChangeEvidence> mine = new ArrayList<>();
            for (CostChangeEvidence e : all) {
                if (e.getOperator() == null || e.getOperator().trim().isEmpty()) {
                    throw new IllegalStateException("存在无操作者的证据 id=" + e.getId());
                }
                String rid = e.getRequestId() == null ? "" : e.getRequestId();
                if (rid.startsWith("ct-alloc-1") || rid.startsWith("AUTO-CARRYOVER-")
                        || rid.startsWith("cv-1") || rid.startsWith("pond-b-disable")) {
                    mine.add(e);
                }
            }
            check(!mine.isEmpty(), "关键动作证据应可检索");
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/cost/evidence?requestId=ct-alloc-1", null));
            assertSuccess(resp, "按requestId追溯证据");
            check(resp.path("data").size() >= 1
                            && "ALLOCATE".equals(resp.path("data").get(0).path("action").asText()),
                    "应可按requestId追溯到分摊证据");
        });

        report.add("验收结论：全部检查点通过——四类成本按池按批归集且金额追溯凭证、人工补录必须引用凭证、转池按数量与生物量守恒分摊、出池同事务形成不可变结转快照、已结转批次不可改写、财务看金额生产只看汇总、差异待审核且不回改快照、停用对象后页面/导出/后台同口径、全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "东山湾海区");
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long createVoucher(String no, String category, String amount, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/cost/vouchers",
                voucherBody(no, category, amount, requestId)));
        assertSuccess(resp, "登记凭证[" + no + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> voucherBody(String no, String category, String amount, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("voucherNo", no);
        body.put("costCategory", category);
        body.put("sourceDoc", "发票-" + no);
        body.put("amount", amount);
        body.put("voucherDate", "2026-10-02");
        body.put("recordedBy", "财务周");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> revokeBody(String by, String reason, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("revokedBy", by);
        body.put("revokeReason", reason);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> entryBody(Long pondId, String category, String amount,
                                          String entryType, Long voucherId, String requestId) {
        return entryBody(batchId, pondId, category, amount, entryType, voucherId, requestId);
    }

    private Map<String, Object> entryBody(Long bId, Long pondId, String category, String amount,
                                          String entryType, Long voucherId, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("batchId", bId);
        body.put("costCategory", category);
        body.put("amount", amount);
        body.put("costDate", "2026-10-06");
        if (entryType != null) {
            body.put("entryType", entryType);
        }
        if (voucherId != null) {
            body.put("voucherId", voucherId);
        }
        body.put("recordedBy", "成本李");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> leg(Long pondId, int quantity, String biomassKg) {
        Map<String, Object> leg = new HashMap<>();
        leg.put("pondId", pondId);
        leg.put("quantity", quantity);
        leg.put("biomassKg", biomassKg);
        return leg;
    }

    private Long raiseVariance(String actual, String reason, String by, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/cost/variances",
                varianceBody(batchId, null, actual, reason, by, requestId)));
        assertSuccess(resp, "提报差异[" + requestId + "]");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> varianceBody(Long bId, String category, String actual,
                                             String reason, String by, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchId", bId);
        if (category != null) {
            body.put("costCategory", category);
        }
        body.put("actualAmount", actual);
        body.put("reason", reason);
        body.put("raisedBy", by);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> reviewBody(String by, String remark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("reviewedBy", by);
        body.put("reviewRemark", remark);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> pondStatusBody(String operator, String reason, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("reason", reason);
        body.put("requestId", requestId);
        return body;
    }

    private JsonNode findRow(JsonNode rowsResp, String bNo) {
        for (JsonNode row : rowsResp.path("data")) {
            if (bNo.equals(row.path("batchNo").asText())) {
                return row;
            }
        }
        return null;
    }

    private boolean containsPond(JsonNode rowsResp, String pondCode) {
        for (JsonNode row : rowsResp.path("data")) {
            if (pondCode.equals(row.path("pondCode").asText())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal amt(JsonNode row, String field) {
        return new BigDecimal(row.path(field).asText("0"));
    }

    private long countEvidence(String action, String entityType) {
        return evidenceMapper.selectCount(new QueryWrapper<CostChangeEvidence>()
                .eq("action", action).eq("entity_type", entityType));
    }

    private String csv(String url) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        ResponseEntity<byte[]> resp = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        return new String(resp.getBody(), StandardCharsets.UTF_8);
    }

    private String page(String url) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return resp.getBody() == null ? "" : resp.getBody();
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 养殖成本分摊与批次结转验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=====================================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
