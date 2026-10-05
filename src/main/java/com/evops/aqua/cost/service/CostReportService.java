package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖成本分摊与批次结转报表：页面（HTML）、导出（CSV）、后台（JSON）三端唯一口径来源。
 *
 * 口径固定在此处，三端不得各写各的查询：
 *   1) 行集合：每个养殖批次一行；金额已结转取冻结快照，未结转取未结成本台账（OpenCostLedger）；
 *   2) 停用池塘过滤：默认（includeDisabled=false）剔除落点池塘已置无效（DISABLED）的行，
 *      页面/导出/后台默认值一致——停用"海水养殖管理对象"后三端同时看不到该对象；
 *   3) 角色视图：FINANCE 财务看四类金额/总额/单位成本/差异；PRODUCTION 生产只看数量/生物量/笔数等汇总，
 *      响应中根本不出现任何金额字段（不是显示 0 或打码，而是字段缺省），行集合两角色一致。
 */
@Service
public class CostReportService {

    public static final String ROLE_FINANCE = "FINANCE";
    public static final String ROLE_PRODUCTION = "PRODUCTION";

    private final SeedlingBatchMapper batchMapper;
    private final PondMapper pondMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final CostCarryoverLineMapper carryoverLineMapper;
    private final CostEntryMapper entryMapper;
    private final CostTransferMapper transferMapper;
    private final CostTransferLineMapper lineMapper;

    public CostReportService(SeedlingBatchMapper batchMapper,
                             PondMapper pondMapper,
                             CostCarryoverMapper carryoverMapper,
                             CostCarryoverLineMapper carryoverLineMapper,
                             CostEntryMapper entryMapper,
                             CostTransferMapper transferMapper,
                             CostTransferLineMapper lineMapper) {
        this.batchMapper = batchMapper;
        this.pondMapper = pondMapper;
        this.carryoverMapper = carryoverMapper;
        this.carryoverLineMapper = carryoverLineMapper;
        this.entryMapper = entryMapper;
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
    }

    public static boolean isFinance(String role) {
        return ROLE_FINANCE.equalsIgnoreCase(role == null ? "" : role.trim());
    }

    /**
     * 批次成本结转行。三端共用。
     * @param role            FINANCE / PRODUCTION
     * @param pondId          可空，按落点池塘过滤
     * @param batchId         可空，按批次过滤
     * @param includeDisabled 是否包含已停用池塘，默认 false（三端一致）
     */
    public List<Map<String, Object>> batchRows(String role, Long pondId, Long batchId, boolean includeDisabled) {
        boolean finance = isFinance(role);
        Map<Long, Pond> ponds = loadPonds();

        QueryWrapper<SeedlingBatch> wrapper = new QueryWrapper<>();
        wrapper.eq(batchId != null, "id", batchId);
        wrapper.orderByAsc("id");
        List<SeedlingBatch> batches = batchMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SeedlingBatch batch : batches) {
            CostCarryover carryover = carryoverMapper.selectOne(new QueryWrapper<CostCarryover>()
                    .eq("batch_id", batch.getId()).last("limit 1"));

            Long rowPondId = carryover != null ? carryover.getPondId() : batch.getPondId();
            if (pondId != null && !pondId.equals(rowPondId)) {
                continue;
            }
            Pond pond = ponds.get(rowPondId);
            if (!includeDisabled && pond != null && Pond.STATUS_DISABLED.equals(pond.getStatus())) {
                continue;
            }

            Map<String, BigDecimal> amounts = new LinkedHashMap<>();
            BigDecimal total;
            BigDecimal unitCost = null;
            BigDecimal biomass = null;
            Integer quantity;
            String costStatus;
            String carryoverDate = null;

            long entryCount = countEntries(batch.getId());
            long transferCount = countTransfers(batch.getId());

            if (carryover != null) {
                costStatus = "CARRIED";
                amounts.put(CostVoucher.CATEGORY_FEED, nz(carryover.getFeedAmount()));
                amounts.put(CostVoucher.CATEGORY_LABOR, nz(carryover.getLaborAmount()));
                amounts.put(CostVoucher.CATEGORY_MEDICINE, nz(carryover.getMedicineAmount()));
                amounts.put(CostVoucher.CATEGORY_ENERGY, nz(carryover.getEnergyAmount()));
                total = nz(carryover.getTotalAmount());
                unitCost = carryover.getUnitCost();
                quantity = carryover.getQuantity();
                biomass = carryover.getBiomassKg();
                carryoverDate = carryover.getCarryoverDate() == null ? null : carryover.getCarryoverDate().toString();
            } else {
                costStatus = SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus()) ? "HARVESTED_NO_COST" : "OPEN";
                OpenCostLedger ledger = OpenCostLedger.load(batch.getId(), entryMapper, transferMapper, lineMapper);
                for (String category : OpenCostLedger.CATEGORIES) {
                    amounts.put(category, ledger.totalByCategory(category));
                }
                total = ledger.grandTotal();
                quantity = batch.getQuantity();
                biomass = openBiomass(batch.getId());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchId", batch.getId());
            row.put("batchNo", batch.getBatchNo());
            row.put("species", batch.getSpecies());
            row.put("pondId", rowPondId);
            row.put("pondCode", pond == null ? null : pond.getPondCode());
            row.put("pondName", pond == null ? null : pond.getPondName());
            row.put("pondStatus", pond == null ? null : pond.getStatus());
            row.put("batchStatus", batch.getStatus());
            row.put("costStatus", costStatus);
            row.put("quantity", quantity);
            row.put("biomassKg", biomass);
            row.put("entryCount", entryCount);
            row.put("transferCount", transferCount);
            row.put("carryoverDate", carryoverDate);

            // 生产角色只看汇总：金额类字段一律不放入响应（三端同此，页面/导出同结构缺省）。
            if (finance) {
                row.put("feedAmount", amounts.get(CostVoucher.CATEGORY_FEED));
                row.put("laborAmount", amounts.get(CostVoucher.CATEGORY_LABOR));
                row.put("medicineAmount", amounts.get(CostVoucher.CATEGORY_MEDICINE));
                row.put("energyAmount", amounts.get(CostVoucher.CATEGORY_ENERGY));
                row.put("totalAmount", total);
                row.put("unitCost", unitCost);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 池塘成本汇总（落点池塘口径）：已结转取快照冻结明细，未结转取未结台账；同样默认剔除停用池塘。
     */
    public List<Map<String, Object>> pondSummary(String role, boolean includeDisabled) {
        boolean finance = isFinance(role);
        Map<Long, Pond> ponds = loadPonds();

        Map<Long, Map<String, BigDecimal>> byPond = new HashMap<>();
        accumulateOpen(byPond);
        accumulateCarried(byPond);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long pondId : sortedKeys(byPond, ponds)) {
            Pond pond = ponds.get(pondId);
            if (!includeDisabled && pond != null && Pond.STATUS_DISABLED.equals(pond.getStatus())) {
                continue;
            }
            Map<String, BigDecimal> cat = byPond.get(pondId);
            BigDecimal total = BigDecimal.ZERO;
            for (String c : OpenCostLedger.CATEGORIES) {
                total = total.add(nz(cat.get(c)));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pondId", pondId);
            row.put("pondCode", pond == null ? null : pond.getPondCode());
            row.put("pondName", pond == null ? null : pond.getPondName());
            row.put("pondStatus", pond == null ? null : pond.getStatus());
            if (finance) {
                row.put("feedAmount", nz(cat.get(CostVoucher.CATEGORY_FEED)));
                row.put("laborAmount", nz(cat.get(CostVoucher.CATEGORY_LABOR)));
                row.put("medicineAmount", nz(cat.get(CostVoucher.CATEGORY_MEDICINE)));
                row.put("energyAmount", nz(cat.get(CostVoucher.CATEGORY_ENERGY)));
                row.put("totalAmount", total.setScale(2, java.math.RoundingMode.HALF_UP));
            }
            rows.add(row);
        }
        return rows;
    }

    private void accumulateOpen(Map<Long, Map<String, BigDecimal>> byPond) {
        for (SeedlingBatch batch : batchMapper.selectList(new QueryWrapper<SeedlingBatch>().orderByAsc("id"))) {
            if (carryoverMapper.selectCount(new QueryWrapper<CostCarryover>()
                    .eq("batch_id", batch.getId())) > 0) {
                continue;
            }
            OpenCostLedger ledger = OpenCostLedger.load(batch.getId(), entryMapper, transferMapper, lineMapper);
            for (OpenCostLedger.OpenPoint point : ledger.getPoints()) {
                byPond.computeIfAbsent(point.getPondId(), k -> emptyCategory())
                        .merge(point.getCategory(), point.getAmount(), BigDecimal::add);
            }
        }
    }

    private void accumulateCarried(Map<Long, Map<String, BigDecimal>> byPond) {
        for (CostCarryover carryover : carryoverMapper.selectList(new QueryWrapper<CostCarryover>().orderByAsc("id"))) {
            List<com.evops.aqua.cost.entity.CostCarryoverLine> lines = carryoverLineMapper.selectList(
                    new QueryWrapper<com.evops.aqua.cost.entity.CostCarryoverLine>()
                            .eq("carryover_id", carryover.getId()));
            for (com.evops.aqua.cost.entity.CostCarryoverLine line : lines) {
                byPond.computeIfAbsent(line.getPondId(), k -> emptyCategory())
                        .merge(line.getCostCategory(), nz(line.getAmount()), BigDecimal::add);
            }
        }
    }

    private Map<String, BigDecimal> emptyCategory() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String c : OpenCostLedger.CATEGORIES) {
            map.put(c, BigDecimal.ZERO);
        }
        return map;
    }

    private List<Long> sortedKeys(Map<Long, Map<String, BigDecimal>> byPond, Map<Long, Pond> ponds) {
        List<Long> ids = new ArrayList<>(byPond.keySet());
        ids.sort((a, b) -> {
            Pond pa = ponds.get(a);
            Pond pb = ponds.get(b);
            String ca = pa == null ? String.valueOf(a) : pa.getPondCode();
            String cb = pb == null ? String.valueOf(b) : pb.getPondCode();
            return ca.compareTo(cb);
        });
        return ids;
    }

    private long countEntries(Long batchId) {
        Long c = entryMapper.selectCount(new QueryWrapper<CostEntry>().eq("batch_id", batchId));
        return c == null ? 0 : c;
    }

    private long countTransfers(Long batchId) {
        Long c = transferMapper.selectCount(new QueryWrapper<CostTransfer>().eq("batch_id", batchId));
        return c == null ? 0 : c;
    }

    private BigDecimal openBiomass(Long batchId) {
        List<CostTransfer> transfers = transferMapper.selectList(
                new QueryWrapper<CostTransfer>().eq("batch_id", batchId));
        if (transfers.isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (CostTransfer t : transfers) {
            ids.add(t.getId());
        }
        List<CostTransferLine> lines = lineMapper.selectList(new QueryWrapper<CostTransferLine>()
                .in("transfer_id", ids).eq("carried_flag", false).isNull("consumed_by_transfer_id"));
        if (lines.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (CostTransferLine line : lines) {
            sum = sum.add(line.getBiomassKg() == null ? BigDecimal.ZERO : line.getBiomassKg());
        }
        return sum.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private Map<Long, Pond> loadPonds() {
        Map<Long, Pond> map = new HashMap<>();
        for (Pond pond : pondMapper.selectList(new QueryWrapper<Pond>())) {
            map.put(pond.getId(), pond);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
