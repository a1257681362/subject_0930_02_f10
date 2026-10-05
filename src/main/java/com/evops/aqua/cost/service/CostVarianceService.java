package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.VarianceCreateRequest;
import com.evops.aqua.cost.dto.VarianceReviewRequest;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostVariance;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.cost.mapper.CostVarianceMapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 成本差异：实盘与账面不一致即开差异单，一律 PENDING 待审核。
 * 账面金额优先取已结转快照（按类别取冻结明细），未结转则取未结成本台账；
 * 审核（通过/驳回）必须有审核人与意见，且只改变差异单状态，绝不回改快照与归集行。
 */
@Service
public class CostVarianceService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CostVarianceMapper varianceMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final CostCarryoverLineMapper carryoverLineMapper;
    private final CostEntryMapper entryMapper;
    private final CostTransferMapper transferMapper;
    private final CostTransferLineMapper lineMapper;
    private final SeedlingBatchService batchService;
    private final CostEvidenceService evidenceService;

    public CostVarianceService(CostVarianceMapper varianceMapper,
                               CostCarryoverMapper carryoverMapper,
                               CostCarryoverLineMapper carryoverLineMapper,
                               CostEntryMapper entryMapper,
                               CostTransferMapper transferMapper,
                               CostTransferLineMapper lineMapper,
                               SeedlingBatchService batchService,
                               CostEvidenceService evidenceService) {
        this.varianceMapper = varianceMapper;
        this.carryoverMapper = carryoverMapper;
        this.carryoverLineMapper = carryoverLineMapper;
        this.entryMapper = entryMapper;
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.batchService = batchService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVariance raise(VarianceCreateRequest request) {
        CostVariance duplicated = varianceMapper.selectOne(new QueryWrapper<CostVariance>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        if (request.getActualAmount() == null || request.getActualAmount().signum() < 0) {
            throw new IllegalArgumentException("实盘金额不能为负");
        }
        String category = request.getCostCategory() == null || request.getCostCategory().trim().isEmpty()
                ? null : CostVoucherService.normalize(request.getCostCategory());
        if (category != null) {
            CostVoucherService.requireCategory(category);
        }

        BigDecimal book = bookAmount(batch.getId(), category);
        BigDecimal actual = request.getActualAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = actual.subtract(book).setScale(2, RoundingMode.HALF_UP);
        if (diff.signum() == 0) {
            throw new IllegalArgumentException("实盘与账面一致（均为" + book + "），无需开差异单");
        }

        CostVariance variance = new CostVariance();
        variance.setVarianceNo(nextVarianceNo(batch.getId()));
        CostCarryover carryover = carryoverMapper.selectOne(new QueryWrapper<CostCarryover>()
                .eq("batch_id", batch.getId()).last("limit 1"));
        if (carryover != null) {
            variance.setCarryoverId(carryover.getId());
        }
        variance.setBatchId(batch.getId());
        variance.setCostCategory(category);
        variance.setBookAmount(book);
        variance.setActualAmount(actual);
        variance.setDiffAmount(diff);
        variance.setReason(request.getReason());
        variance.setStatus(CostVariance.STATUS_PENDING);
        variance.setRaisedBy(request.getRaisedBy());
        variance.setRequestId(request.getRequestId());
        variance.setRemark(request.getRemark());
        varianceMapper.insert(variance);

        evidenceService.record(CostChangeEvidence.ENTITY_VARIANCE, variance.getId(),
                CostChangeEvidence.ACTION_RAISE, request.getRequestId(), request.getRaisedBy(),
                null, variance, "提报成本差异：批次" + batch.getBatchNo()
                        + "，账面=" + book + "，实盘=" + actual + "，差异=" + diff + "，待审核");
        return variance;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVariance approve(Long id, VarianceReviewRequest request) {
        return review(id, request, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVariance reject(Long id, VarianceReviewRequest request) {
        return review(id, request, false);
    }

    private CostVariance review(Long id, VarianceReviewRequest request, boolean approve) {
        CostVariance variance = getOrThrow(id);
        String action = approve ? CostChangeEvidence.ACTION_APPROVE : CostChangeEvidence.ACTION_REJECT;
        if ((approve && CostVariance.STATUS_APPROVED.equals(variance.getStatus()))
                || (!approve && CostVariance.STATUS_REJECTED.equals(variance.getStatus()))
                || alreadyDone(action, id, request.getRequestId())) {
            return variance;
        }
        if (!CostVariance.STATUS_PENDING.equals(variance.getStatus())) {
            throw new IllegalArgumentException("仅待审核差异单可审核，当前状态：" + variance.getStatus());
        }
        CostVariance before = copyOf(variance);
        variance.setStatus(approve ? CostVariance.STATUS_APPROVED : CostVariance.STATUS_REJECTED);
        variance.setReviewedBy(request.getReviewedBy());
        variance.setReviewRemark(request.getReviewRemark());
        variance.setReviewedTime(LocalDateTime.now());
        varianceMapper.updateById(variance);
        evidenceService.record(CostChangeEvidence.ENTITY_VARIANCE, id, action,
                request.getRequestId(), request.getReviewedBy(), before, variance,
                (approve ? "审核通过差异单" : "驳回差异单") + "：" + request.getReviewRemark()
                        + "（只改变差异单状态，不回改结转快照与归集行）");
        return variance;
    }

    public CostVariance getOrThrow(Long id) {
        CostVariance variance = varianceMapper.selectById(id);
        if (variance == null) {
            throw new IllegalArgumentException("差异单不存在，id=" + id);
        }
        return variance;
    }

    public List<CostVariance> list(Long batchId, String status) {
        QueryWrapper<CostVariance> wrapper = new QueryWrapper<>();
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByDesc("id");
        return varianceMapper.selectList(wrapper);
    }

    /** 账面金额：已结转取快照（按类别冻结明细汇总），未结转取未结台账。 */
    private BigDecimal bookAmount(Long batchId, String category) {
        CostCarryover carryover = carryoverMapper.selectOne(new QueryWrapper<CostCarryover>()
                .eq("batch_id", batchId).last("limit 1"));
        if (carryover != null) {
            if (category == null) {
                return nz(carryover.getTotalAmount());
            }
            List<CostCarryoverLine> lines = carryoverLineMapper.selectList(
                    new QueryWrapper<CostCarryoverLine>()
                            .eq("carryover_id", carryover.getId())
                            .eq("cost_category", category));
            BigDecimal sum = BigDecimal.ZERO;
            for (CostCarryoverLine line : lines) {
                sum = sum.add(nz(line.getAmount()));
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        }
        OpenCostLedger ledger = OpenCostLedger.load(batchId, entryMapper, transferMapper, lineMapper);
        return category == null ? ledger.grandTotal() : ledger.totalByCategory(category);
    }

    private boolean alreadyDone(String action, Long id, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        return evidenceService.list(CostChangeEvidence.ENTITY_VARIANCE, id, requestId)
                .stream().anyMatch(e -> action.equals(e.getAction()));
    }

    private String nextVarianceNo(Long batchId) {
        Long count = varianceMapper.selectCount(new QueryWrapper<CostVariance>());
        return "CV-" + batchId + "-" + java.time.LocalDate.now().format(DAY) + "-" + (count + 1);
    }

    private CostVariance copyOf(CostVariance source) {
        CostVariance copy = new CostVariance();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
