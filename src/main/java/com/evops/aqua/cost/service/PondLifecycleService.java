package com.evops.aqua.cost.service;

import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.dto.PondStatusRequest;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 海水养殖管理对象（养殖池）生命周期：置无效（DISABLED）/恢复有效（ACTIVE）。
 * 停用后历史台账与结转快照一律保留不删，只是"养殖成本分摊与批次结转"页面/导出/后台默认不再出现该对象（同口径过滤）。
 */
@Service
public class PondLifecycleService {

    private final PondMapper pondMapper;
    private final CostEvidenceService evidenceService;

    public PondLifecycleService(PondMapper pondMapper, CostEvidenceService evidenceService) {
        this.pondMapper = pondMapper;
        this.evidenceService = evidenceService;
    }

    public Pond getOrThrow(Long id) {
        Pond pond = pondMapper.selectById(id);
        if (pond == null) {
            throw new IllegalArgumentException("养殖池不存在，id=" + id);
        }
        return pond;
    }

    @Transactional(rollbackFor = Exception.class)
    public Pond disable(Long id, PondStatusRequest request) {
        Pond pond = getOrThrow(id);
        if (Pond.STATUS_DISABLED.equals(pond.getStatus())) {
            return pond;
        }
        Pond before = copyOf(pond);
        pond.setStatus(Pond.STATUS_DISABLED);
        pond.setRemark(appendReason(pond.getRemark(), request.getReason()));
        pondMapper.updateById(pond);
        evidenceService.record(CostChangeEvidence.ENTITY_POND, id,
                CostChangeEvidence.ACTION_DISABLE, request.getRequestId(), request.getOperator(),
                before, pond, "养殖池置无效：" + pond.getPondCode() + "，原因=" + request.getReason()
                        + "；历史台账与结转快照保留，成本分摊与批次结转默认不再显示该对象");
        return pond;
    }

    @Transactional(rollbackFor = Exception.class)
    public Pond enable(Long id, PondStatusRequest request) {
        Pond pond = getOrThrow(id);
        if (Pond.STATUS_ACTIVE.equals(pond.getStatus())) {
            return pond;
        }
        Pond before = copyOf(pond);
        pond.setStatus(Pond.STATUS_ACTIVE);
        pondMapper.updateById(pond);
        evidenceService.record(CostChangeEvidence.ENTITY_POND, id,
                CostChangeEvidence.ACTION_ENABLE, request.getRequestId(), request.getOperator(),
                before, pond, "养殖池恢复有效：" + pond.getPondCode() + "，原因=" + request.getReason());
        return pond;
    }

    private String appendReason(String oldRemark, String reason) {
        String tag = "[停用原因]" + reason;
        return oldRemark == null || oldRemark.trim().isEmpty() ? tag : oldRemark + "；" + tag;
    }

    private Pond copyOf(Pond source) {
        Pond copy = new Pond();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
