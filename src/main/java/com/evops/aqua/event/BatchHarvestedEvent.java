package com.evops.aqua.event;

/**
 * 批次出池事件：出池事务提交前（BEFORE_COMMIT）发布。
 * 成本模块监听本事件，在同一事务内形成不可变结转快照——快照失败则出池一并回滚，保证出池必有快照、口径一致。
 * 放在核心包，成本模块单向依赖核心，避免模块间循环依赖。
 */
public class BatchHarvestedEvent {

    private final Long batchId;
    private final Long harvestRecordId;
    private final Long pondId;

    public BatchHarvestedEvent(Long batchId, Long harvestRecordId, Long pondId) {
        this.batchId = batchId;
        this.harvestRecordId = harvestRecordId;
        this.pondId = pondId;
    }

    public Long getBatchId() { return batchId; }
    public Long getHarvestRecordId() { return harvestRecordId; }
    public Long getPondId() { return pondId; }
}
