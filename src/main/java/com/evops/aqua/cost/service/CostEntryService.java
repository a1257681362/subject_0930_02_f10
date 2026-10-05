package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.CostEntryCreateRequest;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 成本归集：饲料/人工/药品/能源按池塘+批次一行一笔。
 * DIRECT 当期归集与 BACKFILL 人工补录都必须引用 ACTIVE 凭证（人工补录强校验类别与额度）；
 * 只允许对在养、未结转、当前就在该（有效）池的批次归集——已结转批次不能再被任何新成本单据改写。
 */
@Service
public class CostEntryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CostEntryMapper entryMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final CostVoucherService voucherService;
    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final CostEvidenceService evidenceService;

    public CostEntryService(CostEntryMapper entryMapper,
                            CostCarryoverMapper carryoverMapper,
                            CostVoucherService voucherService,
                            SeedlingBatchService batchService,
                            PondService pondService,
                            CostEvidenceService evidenceService) {
        this.entryMapper = entryMapper;
        this.carryoverMapper = carryoverMapper;
        this.voucherService = voucherService;
        this.batchService = batchService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostEntry create(CostEntryCreateRequest request) {
        CostEntry duplicated = entryMapper.selectOne(new QueryWrapper<CostEntry>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }

        String category = CostVoucherService.normalize(request.getCostCategory());
        CostVoucherService.requireCategory(category);
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("成本金额必须大于0");
        }
        boolean backfill = CostEntry.ENTRY_BACKFILL.equals(
                request.getEntryType() == null ? "" : request.getEntryType().trim().toUpperCase());
        String entryType = backfill ? CostEntry.ENTRY_BACKFILL : CostEntry.ENTRY_DIRECT;

        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        // 已出池/已结转：新成本单据一律拒绝，不能悄悄改写已结转批次。
        if (!SeedlingBatch.STATUS_STOCKED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已出池结转，不能再归集成本");
        }
        if (carryoverMapper.selectCount(new QueryWrapper<CostCarryover>()
                .eq("batch_id", batch.getId())) > 0) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已形成结转快照，不能再归集成本");
        }
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "当前不在池塘id="
                    + request.getPondId() + "，请核对池号或先登记换池与成本分摊");
        }
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);

        // 人工补录必须引用凭证；DIRECT 同样必须引用——凭证缺失一律拒绝。
        CostVoucher voucher = voucherService.requireUsable(
                request.getVoucherId(), category, request.getAmount(), backfill);

        CostEntry entry = new CostEntry();
        entry.setEntryNo(nextEntryNo(batch.getId(), request.getCostDate()));
        entry.setPondId(pond.getId());
        entry.setBatchId(batch.getId());
        entry.setCostCategory(category);
        entry.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setCostDate(request.getCostDate());
        entry.setEntryType(entryType);
        entry.setVoucherId(voucher.getId());
        entry.setClosedFlag(false);
        entry.setRecordedBy(request.getRecordedBy());
        entry.setRequestId(request.getRequestId());
        entry.setRemark(request.getRemark());
        entryMapper.insert(entry);

        evidenceService.record(CostChangeEvidence.ENTITY_ENTRY, entry.getId(),
                backfill ? CostChangeEvidence.ACTION_BACKFILL : CostChangeEvidence.ACTION_CREATE,
                request.getRequestId(), request.getRecordedBy(), null, entry,
                (backfill ? "人工补录" : "当期归集") + "成本：批次" + batch.getBatchNo()
                        + "，类别=" + category + "，金额=" + entry.getAmount()
                        + "，引用凭证=" + voucher.getVoucherNo());
        return entry;
    }

    public List<CostEntry> list(Long pondId, Long batchId, String category, Boolean closed) {
        QueryWrapper<CostEntry> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(category != null && !category.trim().isEmpty(),
                "cost_category", CostVoucherService.normalize(category));
        wrapper.eq(closed != null, "closed_flag", closed);
        wrapper.orderByAsc("cost_date", "id");
        return entryMapper.selectList(wrapper);
    }

    private String nextEntryNo(Long batchId, java.time.LocalDate costDate) {
        Long count = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                .eq("batch_id", batchId).eq("cost_date", costDate));
        return "CE-" + batchId + "-" + costDate.format(DAY) + "-" + (count + 1);
    }
}
