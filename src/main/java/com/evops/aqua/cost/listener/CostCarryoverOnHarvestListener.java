package com.evops.aqua.cost.listener;

import com.evops.aqua.cost.service.CostCarryoverService;
import com.evops.aqua.event.BatchHarvestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听批次出池：在出池事务提交前（BEFORE_COMMIT）于同一事务内形成成本结转快照。
 * 快照形成失败会让出池事务一并回滚——保证"出池必有结转快照、台账与成本同口径"。
 */
@Component
public class CostCarryoverOnHarvestListener {

    private final CostCarryoverService carryoverService;

    public CostCarryoverOnHarvestListener(CostCarryoverService carryoverService) {
        this.carryoverService = carryoverService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onHarvested(BatchHarvestedEvent event) {
        carryoverService.carryoverOnHarvest(
                event.getBatchId(), event.getHarvestRecordId(), event.getPondId());
    }
}
