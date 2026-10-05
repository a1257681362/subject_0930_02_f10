package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.VoucherCreateRequest;
import com.evops.aqua.cost.dto.VoucherRevokeRequest;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostVoucherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 成本凭证：登记（幂等）、作废、有效性校验。
 * 任何成本金额都必须追溯到一条 ACTIVE 凭证；凭证不做物理删除，只能作废。
 */
@Service
public class CostVoucherService {

    private static final List<String> CATEGORIES = Arrays.asList(
            CostVoucher.CATEGORY_FEED, CostVoucher.CATEGORY_LABOR,
            CostVoucher.CATEGORY_MEDICINE, CostVoucher.CATEGORY_ENERGY);

    private final CostVoucherMapper voucherMapper;
    private final CostEntryMapper entryMapper;
    private final CostEvidenceService evidenceService;

    public CostVoucherService(CostVoucherMapper voucherMapper,
                              CostEntryMapper entryMapper,
                              CostEvidenceService evidenceService) {
        this.voucherMapper = voucherMapper;
        this.entryMapper = entryMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVoucher create(VoucherCreateRequest request) {
        CostVoucher duplicated = voucherMapper.selectOne(new QueryWrapper<CostVoucher>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        requireCategory(request.getCostCategory());
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("凭证金额必须大于0");
        }
        CostVoucher voucher = new CostVoucher();
        voucher.setVoucherNo(request.getVoucherNo());
        voucher.setCostCategory(request.getCostCategory().trim().toUpperCase());
        voucher.setSourceDoc(request.getSourceDoc());
        voucher.setAmount(request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        voucher.setVoucherDate(request.getVoucherDate());
        voucher.setStatus(CostVoucher.STATUS_ACTIVE);
        voucher.setRecordedBy(request.getRecordedBy());
        voucher.setRequestId(request.getRequestId());
        voucher.setRemark(request.getRemark());
        voucherMapper.insert(voucher);
        evidenceService.record(CostChangeEvidence.ENTITY_VOUCHER, voucher.getId(),
                CostChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getRecordedBy(),
                null, voucher, "登记成本凭证：" + voucher.getVoucherNo()
                        + "，类别=" + voucher.getCostCategory() + "，金额=" + voucher.getAmount());
        return voucher;
    }

    /** 作废：已被成本归集引用的凭证仍允许作废（历史引用保留），但此后不能再被新单据引用。 */
    @Transactional(rollbackFor = Exception.class)
    public CostVoucher revoke(Long id, VoucherRevokeRequest request) {
        CostVoucher voucher = getOrThrow(id);
        if (CostVoucher.STATUS_REVOKED.equals(voucher.getStatus())
                || alreadyDone(CostChangeEvidence.ACTION_REVOKE, id, request.getRequestId())) {
            return voucher;
        }
        CostVoucher before = copyOf(voucher);
        voucher.setStatus(CostVoucher.STATUS_REVOKED);
        voucher.setRevokedBy(request.getRevokedBy());
        voucher.setRevokeReason(request.getRevokeReason());
        voucher.setRevokedTime(LocalDateTime.now());
        voucherMapper.updateById(voucher);
        evidenceService.record(CostChangeEvidence.ENTITY_VOUCHER, id,
                CostChangeEvidence.ACTION_REVOKE, request.getRequestId(), request.getRevokedBy(),
                before, voucher, "作废成本凭证：" + voucher.getVoucherNo()
                        + "，原因=" + request.getRevokeReason());
        return voucher;
    }

    public CostVoucher getOrThrow(Long id) {
        CostVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new IllegalArgumentException("成本凭证不存在，id=" + id);
        }
        return voucher;
    }

    /**
     * 成本单据引用凭证的统一校验：必须存在、ACTIVE；补录/归集类别须与凭证类别一致；
     * 补录金额不得超过凭证金额（防止一笔小额凭证反复被引用凑出大额成本）。
     */
    public CostVoucher requireUsable(Long voucherId, String category, BigDecimal amount, boolean backfill) {
        CostVoucher voucher = getOrThrow(voucherId);
        if (!CostVoucher.STATUS_ACTIVE.equals(voucher.getStatus())) {
            throw new IllegalArgumentException("凭证" + voucher.getVoucherNo() + "已作废，不能作为成本归集依据");
        }
        if (!voucher.getCostCategory().equals(normalize(category))) {
            throw new IllegalArgumentException("凭证" + voucher.getVoucherNo() + "类别为"
                    + voucher.getCostCategory() + "，与成本类别" + normalize(category) + "不一致");
        }
        if (backfill && amount != null && amount.compareTo(voucher.getAmount()) > 0) {
            throw new IllegalArgumentException("人工补录金额" + amount + "超过凭证"
                    + voucher.getVoucherNo() + "金额" + voucher.getAmount());
        }
        return voucher;
    }

    public List<CostVoucher> list(String category, String status) {
        QueryWrapper<CostVoucher> wrapper = new QueryWrapper<>();
        wrapper.eq(category != null && !category.trim().isEmpty(), "cost_category", normalize(category));
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByDesc("id");
        return voucherMapper.selectList(wrapper);
    }

    public static String normalize(String category) {
        return category == null ? null : category.trim().toUpperCase();
    }

    public static void requireCategory(String category) {
        String normalized = normalize(category);
        if (!CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("成本类别必须是 FEED/LABOR/MEDICINE/ENERGY 之一：" + category);
        }
    }

    private boolean alreadyDone(String action, Long id, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        return evidenceService.list(CostChangeEvidence.ENTITY_VOUCHER, id, requestId)
                .stream().anyMatch(e -> action.equals(e.getAction()));
    }

    private CostVoucher copyOf(CostVoucher source) {
        CostVoucher copy = new CostVoucher();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
