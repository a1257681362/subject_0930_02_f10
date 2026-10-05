package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批次未结成本台账：本模块唯一的"账面口径"，转池分摊、出池结转、差异核对、页面/导出/后台报表全部取它。
 *
 * 未结成本的两类来源（同一批可能并存，靠关闭标记避免重复计）：
 *   1) 归集明细 t_cost_entry 中 closed_flag=FALSE 的行（按归集池塘落点，一行一类）；
 *   2) 历史转池分配腿 t_cost_transfer_line 中 carried_flag=FALSE 且 consumed_by_transfer_id IS NULL 的腿
 *      （一行按池塘落点、含四类金额，加载时展开为四个类别点）——已被后续转池再次分走或已随出池结转的腿不计入。
 *
 * 按 (落点池塘, 成本类别) 聚合金额，并保留构成该点的原始归集行/分配腿，供关闭与留痕。
 */
public class OpenCostLedger {

    /** 四类成本固定顺序（报表、快照、分摊均按此序）。 */
    public static final List<String> CATEGORIES;

    static {
        List<String> cats = new ArrayList<>();
        cats.add(CostVoucher.CATEGORY_FEED);
        cats.add(CostVoucher.CATEGORY_LABOR);
        cats.add(CostVoucher.CATEGORY_MEDICINE);
        cats.add(CostVoucher.CATEGORY_ENERGY);
        CATEGORIES = java.util.Collections.unmodifiableList(cats);
    }

    /** 一个未结点：某池塘某类别的未结金额及其构成。 */
    public static class OpenPoint {
        private final Long pondId;
        private final String category;
        private BigDecimal amount = BigDecimal.ZERO;
        private final List<Long> entryIds = new ArrayList<>();
        private final List<Long> lineIds = new ArrayList<>();

        OpenPoint(Long pondId, String category) {
            this.pondId = pondId;
            this.category = category;
        }

        public Long getPondId() { return pondId; }
        public String getCategory() { return category; }
        public BigDecimal getAmount() { return amount; }
        public List<Long> getEntryIds() { return entryIds; }
        public List<Long> getLineIds() { return lineIds; }
    }

    private final List<OpenPoint> points = new ArrayList<>();

    private OpenCostLedger() { }

    public static OpenCostLedger load(Long batchId, CostEntryMapper entryMapper,
                                      CostTransferMapper transferMapper,
                                      CostTransferLineMapper lineMapper) {
        OpenCostLedger ledger = new OpenCostLedger();
        Map<String, OpenPoint> index = new LinkedHashMap<>();

        List<CostEntry> openEntries = entryMapper.selectList(new QueryWrapper<CostEntry>()
                .eq("batch_id", batchId).eq("closed_flag", false).orderByAsc("id"));
        for (CostEntry entry : openEntries) {
            OpenPoint point = index.computeIfAbsent(
                    key(entry.getPondId(), entry.getCostCategory()),
                    k -> new OpenPoint(entry.getPondId(), entry.getCostCategory()));
            point.amount = point.amount.add(nz(entry.getAmount()));
            point.entryIds.add(entry.getId());
        }

        List<CostTransfer> transfers = transferMapper.selectList(new QueryWrapper<CostTransfer>()
                .eq("batch_id", batchId).orderByAsc("id"));
        if (!transfers.isEmpty()) {
            List<Long> transferIds = transfers.stream().map(CostTransfer::getId).collect(Collectors.toList());
            List<CostTransferLine> openLines = lineMapper.selectList(new QueryWrapper<CostTransferLine>()
                    .in("transfer_id", transferIds)
                    .eq("carried_flag", false).isNull("consumed_by_transfer_id")
                    .orderByAsc("id"));
            for (CostTransferLine line : openLines) {
                for (String category : CATEGORIES) {
                    BigDecimal amount = amountOf(line, category);
                    if (amount.signum() == 0) {
                        continue;
                    }
                    OpenPoint point = index.computeIfAbsent(
                            key(line.getLegPondId(), category),
                            k -> new OpenPoint(line.getLegPondId(), category));
                    point.amount = point.amount.add(amount);
                    point.lineIds.add(line.getId());
                }
            }
        }

        ledger.points.addAll(index.values());
        return ledger;
    }

    public List<OpenPoint> getPoints() { return points; }

    public boolean isEmpty() { return points.isEmpty(); }

    /** 涉及的全部落点池塘。 */
    public List<Long> pondIds() {
        List<Long> ids = new ArrayList<>();
        for (OpenPoint point : points) {
            if (!ids.contains(point.getPondId())) {
                ids.add(point.getPondId());
            }
        }
        return ids;
    }

    /** 构成未结成本的全部分配腿 id（去重，结转/再分摊时一次性关闭）。 */
    public List<Long> allLineIds() {
        List<Long> ids = new ArrayList<>();
        for (OpenPoint point : points) {
            for (Long lineId : point.getLineIds()) {
                if (!ids.contains(lineId)) {
                    ids.add(lineId);
                }
            }
        }
        return ids;
    }

    /** 构成未结成本的全部归集行 id（去重）。 */
    public List<Long> allEntryIds() {
        List<Long> ids = new ArrayList<>();
        for (OpenPoint point : points) {
            for (Long entryId : point.getEntryIds()) {
                if (!ids.contains(entryId)) {
                    ids.add(entryId);
                }
            }
        }
        return ids;
    }

    /** 某类别全批未结合计。 */
    public BigDecimal totalByCategory(String category) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OpenPoint point : points) {
            if (point.getCategory().equals(category)) {
                sum = sum.add(point.getAmount());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** 全批未结总额。 */
    public BigDecimal grandTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OpenPoint point : points) {
            sum = sum.add(point.getAmount());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal amountOf(CostTransferLine line, String category) {
        switch (category) {
            case CostVoucher.CATEGORY_FEED: return nz(line.getFeedAmount());
            case CostVoucher.CATEGORY_LABOR: return nz(line.getLaborAmount());
            case CostVoucher.CATEGORY_MEDICINE: return nz(line.getMedicineAmount());
            default: return nz(line.getEnergyAmount());
        }
    }

    private static String key(Long pondId, String category) {
        return pondId + "#" + category;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
