package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.CarryoverCreateRequest;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.service.SeedlingBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出池结转：批次出池同事务形成不可变结转快照，关闭全部未结点（归集行/分配腿），按池塘×类别冻结金额。
 * 快照一经形成永不回改；已结转批次不能再被任何新成本单据/转池单改写（归集、转池服务均拦截）。
 */
@Service
public class CostCarryoverService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final String SYSTEM_OPERATOR = "SYSTEM_HARVEST";

    private final CostCarryoverMapper carryoverMapper;
    private final CostCarryoverLineMapper carryoverLineMapper;
    private final CostEntryMapper entryMapper;
    private final CostTransferMapper transferMapper;
    private final CostTransferLineMapper lineMapper;
    private final HarvestRecordMapper harvestRecordMapper;
    private final SeedlingBatchService batchService;
    private final CostEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public CostCarryoverService(CostCarryoverMapper carryoverMapper,
                                CostCarryoverLineMapper carryoverLineMapper,
                                CostEntryMapper entryMapper,
                                CostTransferMapper transferMapper,
                                CostTransferLineMapper lineMapper,
                                HarvestRecordMapper harvestRecordMapper,
                                SeedlingBatchService batchService,
                                CostEvidenceService evidenceService,
                                ObjectMapper objectMapper) {
        this.carryoverMapper = carryoverMapper;
        this.carryoverLineMapper = carryoverLineMapper;
        this.entryMapper = entryMapper;
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.harvestRecordMapper = harvestRecordMapper;
        this.batchService = batchService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 出池事务内自动结转（由出池事件监听在同一事务调用）。幂等：已存在快照直接返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public CostCarryover carryoverOnHarvest(Long batchId, Long harvestRecordId, Long pondId) {
        CostCarryover existing = findByBatch(batchId);
        if (existing != null) {
            return existing;
        }
        HarvestRecord harvest = harvestRecordMapper.selectById(harvestRecordId);
        int quantity = harvest != null && harvest.getQuantity() != null ? harvest.getQuantity() : 0;

        OpenCostLedger ledger = OpenCostLedger.load(batchId, entryMapper, transferMapper, lineMapper);
        BigDecimal biomass = sumOpenBiomass(batchId);
        String requestId = "AUTO-CARRYOVER-" + harvestRecordId;
        return buildSnapshot(batchId, harvestRecordId, pondId, harvest.getHarvestDate(),
                quantity, biomass, ledger, SYSTEM_OPERATOR, requestId, "出池自动结转");
    }

    /**
     * 手工结转入口：仅当批次已出池但因特殊原因尚无快照时使用；正常出池已自动结转，重复请求幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public CostCarryover manualCarryover(Long batchId, CarryoverCreateRequest request) {
        CostCarryover existing = findByBatch(batchId);
        if (existing != null) {
            return existing;
        }
        SeedlingBatch batch = batchService.getOrThrow(batchId);
        HarvestRecord harvest = harvestRecordMapper.selectOne(new QueryWrapper<HarvestRecord>()
                .eq("batch_id", batchId).orderByDesc("id").last("limit 1"));
        if (harvest == null) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "尚无出池记录，不能结转");
        }
        OpenCostLedger ledger = OpenCostLedger.load(batchId, entryMapper, transferMapper, lineMapper);
        BigDecimal biomass = request.getBiomassKg() != null ? request.getBiomassKg() : sumOpenBiomass(batchId);
        return buildSnapshot(batchId, harvest.getId(), harvest.getPondId(), harvest.getHarvestDate(),
                harvest.getQuantity(), biomass, ledger, request.getCarriedBy(),
                request.getRequestId(), request.getRemark() == null ? "手工结转" : request.getRemark());
    }

    public CostCarryover findByBatch(Long batchId) {
        return carryoverMapper.selectOne(new QueryWrapper<CostCarryover>()
                .eq("batch_id", batchId).last("limit 1"));
    }

    public CostCarryover getOrThrow(Long id) {
        CostCarryover carryover = carryoverMapper.selectById(id);
        if (carryover == null) {
            throw new IllegalArgumentException("结转快照不存在，id=" + id);
        }
        return carryover;
    }

    public List<CostCarryover> list(Long pondId, Long batchId) {
        QueryWrapper<CostCarryover> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByDesc("id");
        return carryoverMapper.selectList(wrapper);
    }

    public List<CostCarryoverLine> linesOf(Long carryoverId) {
        return carryoverLineMapper.selectList(new QueryWrapper<CostCarryoverLine>()
                .eq("carryover_id", carryoverId).orderByAsc("cost_category", "pond_id"));
    }

    private CostCarryover buildSnapshot(Long batchId, Long harvestRecordId, Long pondId,
                                        java.time.LocalDate carryoverDate, int quantity, BigDecimal biomass,
                                        OpenCostLedger ledger, String operator, String requestId, String remark) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (String category : OpenCostLedger.CATEGORIES) {
            byCategory.put(category, ledger.totalByCategory(category));
        }
        BigDecimal total = ledger.grandTotal();
        BigDecimal unitCost = quantity > 0
                ? total.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP)
                : null;

        CostCarryover carryover = new CostCarryover();
        carryover.setCarryoverNo(nextCarryoverNo(batchId));
        carryover.setBatchId(batchId);
        carryover.setPondId(pondId);
        carryover.setHarvestRecordId(harvestRecordId);
        carryover.setCarryoverDate(carryoverDate);
        carryover.setQuantity(quantity);
        carryover.setBiomassKg(biomass == null ? null : biomass.setScale(3, RoundingMode.HALF_UP));
        carryover.setFeedAmount(byCategory.get("FEED"));
        carryover.setLaborAmount(byCategory.get("LABOR"));
        carryover.setMedicineAmount(byCategory.get("MEDICINE"));
        carryover.setEnergyAmount(byCategory.get("ENERGY"));
        carryover.setTotalAmount(total);
        carryover.setUnitCost(unitCost);
        carryover.setStatus(CostCarryover.STATUS_CONFIRMED);
        carryover.setCarriedBy(operator);
        carryover.setRequestId(requestId);
        carryover.setRemark(remark);
        carryoverMapper.insert(carryover);

        // 冻结明细：按 池塘×类别。
        List<Map<String, Object>> snapshotLines = new ArrayList<>();
        for (OpenCostLedger.OpenPoint point : ledger.getPoints()) {
            if (point.getAmount().signum() == 0) {
                continue;
            }
            CostCarryoverLine line = new CostCarryoverLine();
            line.setCarryoverId(carryover.getId());
            line.setCostCategory(point.getCategory());
            line.setPondId(point.getPondId());
            line.setAmount(point.getAmount().setScale(2, RoundingMode.HALF_UP));
            carryoverLineMapper.insert(line);
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("pondId", point.getPondId());
            snap.put("category", point.getCategory());
            snap.put("amount", line.getAmount());
            snapshotLines.add(snap);
        }
        carryover.setSnapshotJson(toJson(snapshotLines));
        carryoverMapper.updateById(carryover);

        // 关闭全部未结点：归集行与分配腿都标记进入本快照，此后不再计入未结，也不能再改写。
        for (Long entryId : ledger.allEntryIds()) {
            CostEntry entry = entryMapper.selectById(entryId);
            entry.setClosedFlag(true);
            entry.setCarryoverId(carryover.getId());
            entryMapper.updateById(entry);
        }
        for (Long lineId : ledger.allLineIds()) {
            CostTransferLine line = lineMapper.selectById(lineId);
            line.setCarriedFlag(true);
            line.setCarriedCarryoverId(carryover.getId());
            lineMapper.updateById(line);
        }

        evidenceService.record(CostChangeEvidence.ENTITY_CARRYOVER, carryover.getId(),
                CostChangeEvidence.ACTION_CARRYOVER, requestId, operator, null, carryover,
                "出池结转快照：批次id=" + batchId + "，冻结总额=" + total
                        + "，单位成本=" + unitCost + "，按池塘×类别冻结" + snapshotLines.size() + "行，快照不可改");
        return carryover;
    }

    /** 出池时的当前生物量：取该批未结分配腿（即当前分布）生物量之和；无转池腿则为空。 */
    private BigDecimal sumOpenBiomass(Long batchId) {
        List<com.evops.aqua.cost.entity.CostTransfer> transfers = transferMapper.selectList(
                new QueryWrapper<com.evops.aqua.cost.entity.CostTransfer>()
                        .eq("batch_id", batchId));
        if (transfers.isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (com.evops.aqua.cost.entity.CostTransfer t : transfers) {
            ids.add(t.getId());
        }
        List<CostTransferLine> openLines = lineMapper.selectList(new QueryWrapper<CostTransferLine>()
                .in("transfer_id", ids).eq("carried_flag", false).isNull("consumed_by_transfer_id"));
        if (openLines.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (CostTransferLine line : openLines) {
            sum = sum.add(line.getBiomassKg() == null ? BigDecimal.ZERO : line.getBiomassKg());
        }
        return sum;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String nextCarryoverNo(Long batchId) {
        return "CO-" + batchId + "-" + java.time.LocalDate.now().format(DAY) + "-"
                + (carryoverMapper.selectCount(new QueryWrapper<CostCarryover>()) + 1);
    }
}
