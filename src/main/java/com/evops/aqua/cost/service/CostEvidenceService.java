package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.mapper.CostChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 成本模块变更证据：凭证/归集/转池分摊/结转/差异/审核所有写操作统一落 t_cost_change_evidence，只追加。
 * 每条证据必须带操作者（operator）；需要在业务回滚时仍保留的失败事实用 recordStandalone 独立事务写。
 */
@Service
public class CostEvidenceService {

    private final CostChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public CostEvidenceService(CostChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        CostChangeEvidence evidence = new CostChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setOperator(operator);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /** 独立事务写证据：业务回滚但证据必须留下的场景。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 String operator, Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, operator, before, after, detail);
    }

    public List<CostChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<CostChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
