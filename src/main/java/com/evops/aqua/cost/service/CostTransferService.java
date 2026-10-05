package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.CostTransferAllocateRequest;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.mapper.TransferRecordMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转池成本分配：针对一次台账转池记录，把该批全部未结成本按「数量 + 生物量」加权分摊到各落点池塘。
 * 权重 = 0.5×数量占比 + 0.5×生物量占比（生物量合计为 0 时退化为只按数量）；尾差并入末腿保证各类别合计守恒。
 * 分摊后旧未结点（未关闭归集行、既往未消耗分配腿）一次性置为已消耗，余额由本次新腿承接，不重复计。
 */
@Service
public class CostTransferService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CostTransferMapper transferMapper;
    private final CostTransferLineMapper lineMapper;
    private final CostEntryMapper entryMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final TransferRecordMapper transferRecordMapper;
    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final CostEvidenceService evidenceService;

    public CostTransferService(CostTransferMapper transferMapper,
                               CostTransferLineMapper lineMapper,
                               CostEntryMapper entryMapper,
                               CostCarryoverMapper carryoverMapper,
                               TransferRecordMapper transferRecordMapper,
                               SeedlingBatchService batchService,
                               PondService pondService,
                               CostEvidenceService evidenceService) {
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.entryMapper = entryMapper;
        this.carryoverMapper = carryoverMapper;
        this.transferRecordMapper = transferRecordMapper;
        this.batchService = batchService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostTransfer allocate(CostTransferAllocateRequest request) {
        CostTransfer byRequest = transferMapper.selectOne(new QueryWrapper<CostTransfer>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (byRequest != null) {
            return byRequest;
        }
        CostTransfer byRecord = transferMapper.selectOne(new QueryWrapper<CostTransfer>()
                .eq("transfer_record_id", request.getTransferRecordId()).last("limit 1"));
        if (byRecord != null) {
            return byRecord;
        }

        TransferRecord record = transferRecordMapper.selectById(request.getTransferRecordId());
        if (record == null) {
            throw new IllegalArgumentException("台账转池记录不存在，id=" + request.getTransferRecordId());
        }
        SeedlingBatch batch = batchService.getOrThrow(record.getBatchId());
        if (!SeedlingBatch.STATUS_STOCKED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已出池结转，不能再做转池成本分摊");
        }
        if (carryoverMapper.selectCount(new QueryWrapper<CostCarryover>()
                .eq("batch_id", batch.getId())) > 0) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已结转，不能再做转池成本分摊");
        }

        List<CostTransferAllocateRequest.AllocationLegRequest> legs = request.getLegs();
        validateLegs(legs);

        int totalQty = 0;
        BigDecimal totalBio = BigDecimal.ZERO;
        for (CostTransferAllocateRequest.AllocationLegRequest leg : legs) {
            totalQty += leg.getQuantity();
            totalBio = totalBio.add(nz(leg.getBiomassKg()));
        }
        if (totalQty <= 0) {
            throw new IllegalArgumentException("各落点数量合计必须大于0");
        }
        if (record.getQuantity() != null && totalQty != record.getQuantity()) {
            throw new IllegalArgumentException("各落点数量合计" + totalQty
                    + "必须等于转池记录数量" + record.getQuantity()
                    + "，按数量分摊的基数不能错配");
        }

        // 权重（6 位小数），尾差并入末腿。
        BigDecimal[] weights = new BigDecimal[legs.size()];
        BigDecimal weightSum = BigDecimal.ZERO;
        for (int i = 0; i < legs.size(); i++) {
            BigDecimal qtyPart = BigDecimal.valueOf(legs.get(i).getQuantity())
                    .divide(BigDecimal.valueOf(totalQty), 6, RoundingMode.HALF_UP);
            BigDecimal bioPart = totalBio.signum() == 0 ? qtyPart
                    : nz(legs.get(i).getBiomassKg()).divide(totalBio, 6, RoundingMode.HALF_UP);
            weights[i] = qtyPart.multiply(HALF).add(bioPart.multiply(HALF))
                    .setScale(6, RoundingMode.HALF_UP);
            if (i < legs.size() - 1) {
                weightSum = weightSum.add(weights[i]);
            }
        }
        weights[legs.size() - 1] = BigDecimal.ONE.subtract(weightSum).setScale(6, RoundingMode.HALF_UP);

        // 取该批全部未结成本（唯一账面口径）。
        OpenCostLedger ledger = OpenCostLedger.load(batch.getId(), entryMapper, transferMapper, lineMapper);
        if (ledger.isEmpty()) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "当前无未结成本可分摊");
        }

        CostTransfer transfer = new CostTransfer();
        transfer.setTransferNo(nextTransferNo(batch.getId()));
        transfer.setTransferRecordId(record.getId());
        transfer.setBatchId(batch.getId());
        transfer.setFromPondId(record.getFromPondId());
        transfer.setTotalQuantity(totalQty);
        transfer.setTotalBiomassKg(totalBio.setScale(3, RoundingMode.HALF_UP));
        transfer.setStatus(CostTransfer.STATUS_ALLOCATED);
        transfer.setOperator(request.getOperator());
        transfer.setRequestId(request.getRequestId());
        transfer.setRemark(request.getRemark());
        transferMapper.insert(transfer);

        // 类别总额（全批未结）→ 按权重分到各腿，尾差并入末腿。
        Map<String, BigDecimal[]> matrix = new LinkedHashMap<>();
        for (String category : OpenCostLedger.CATEGORIES) {
            matrix.put(category, distribute(ledger.totalByCategory(category), weights));
        }

        BigDecimal[] legTotals = new BigDecimal[legs.size()];
        for (int i = 0; i < legs.size(); i++) {
            legTotals[i] = BigDecimal.ZERO;
        }
        List<CostTransferLine> created = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            CostTransferAllocateRequest.AllocationLegRequest legReq = legs.get(i);
            CostTransferLine line = new CostTransferLine();
            line.setTransferId(transfer.getId());
            line.setLegPondId(legReq.getPondId());
            line.setQuantity(legReq.getQuantity());
            line.setBiomassKg(nz(legReq.getBiomassKg()).setScale(3, RoundingMode.HALF_UP));
            line.setWeightRatio(weights[i]);
            line.setFeedAmount(matrix.get(CostVoucher.CATEGORY_FEED)[i]);
            line.setLaborAmount(matrix.get(CostVoucher.CATEGORY_LABOR)[i]);
            line.setMedicineAmount(matrix.get(CostVoucher.CATEGORY_MEDICINE)[i]);
            line.setEnergyAmount(matrix.get(CostVoucher.CATEGORY_ENERGY)[i]);
            BigDecimal total = line.getFeedAmount().add(line.getLaborAmount())
                    .add(line.getMedicineAmount()).add(line.getEnergyAmount()).setScale(2, RoundingMode.HALF_UP);
            line.setTotalAmount(total);
            line.setCarriedFlag(false);
            lineMapper.insert(line);
            created.add(line);
            legTotals[i] = total;
        }

        // 关闭旧未结点：未关闭归集行置已分摊，既往未消耗腿置已消耗——余额已由本次新腿承接。
        for (Long entryId : ledger.allEntryIds()) {
            CostEntry entry = entryMapper.selectById(entryId);
            entry.setClosedFlag(true);
            entry.setAllocatedTransferId(transfer.getId());
            entryMapper.updateById(entry);
        }
        for (Long lineId : ledger.allLineIds()) {
            CostTransferLine old = lineMapper.selectById(lineId);
            old.setConsumedByTransferId(transfer.getId());
            lineMapper.updateById(old);
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("transfer", transfer);
        after.put("lines", created);
        after.put("grandTotal", ledger.grandTotal());
        evidenceService.record(CostChangeEvidence.ENTITY_TRANSFER, transfer.getId(),
                CostChangeEvidence.ACTION_ALLOCATE, request.getRequestId(), request.getOperator(),
                null, after, "转池成本分摊：批次" + batch.getBatchNo() + "，未结总额="
                        + ledger.grandTotal() + "，按数量+生物量分摊到" + legs.size() + "个落点池塘");
        return transfer;
    }

    public List<CostTransfer> list(Long batchId) {
        return transferMapper.selectList(new QueryWrapper<CostTransfer>()
                .eq(batchId != null, "batch_id", batchId).orderByAsc("id"));
    }

    public List<CostTransferLine> linesOf(Long transferId) {
        return lineMapper.selectList(new QueryWrapper<CostTransferLine>()
                .eq("transfer_id", transferId).orderByAsc("id"));
    }

    private void validateLegs(List<CostTransferAllocateRequest.AllocationLegRequest> legs) {
        Set<Long> pondIds = new HashSet<>();
        for (CostTransferAllocateRequest.AllocationLegRequest leg : legs) {
            if (leg.getQuantity() == null || leg.getQuantity() < 0) {
                throw new IllegalArgumentException("落点数量不能为负");
            }
            if (leg.getBiomassKg() == null || leg.getBiomassKg().signum() < 0) {
                throw new IllegalArgumentException("落点生物量不能为负");
            }
            Pond pond = pondService.getOrThrow(leg.getPondId());
            pondService.requireActive(pond);
            if (!pondIds.add(leg.getPondId())) {
                throw new IllegalArgumentException("落点池塘重复：" + pond.getPondCode());
            }
        }
    }

    /** 按权重分摊一个类别总额，末腿吸收尾差，保证分配合计等于原总额。 */
    private BigDecimal[] distribute(BigDecimal total, BigDecimal[] weights) {
        BigDecimal[] amounts = new BigDecimal[weights.length];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < weights.length; i++) {
            if (i < weights.length - 1) {
                amounts[i] = total.multiply(weights[i]).setScale(2, RoundingMode.HALF_UP);
                allocated = allocated.add(amounts[i]);
            } else {
                amounts[i] = total.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return amounts;
    }

    private String nextTransferNo(Long batchId) {
        Long count = transferMapper.selectCount(new QueryWrapper<CostTransfer>()
                .eq("batch_id", batchId));
        return "CT-" + batchId + "-" + java.time.LocalDate.now().format(DAY) + "-" + (count + 1);
    }

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
