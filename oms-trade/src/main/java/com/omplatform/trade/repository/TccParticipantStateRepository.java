package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.trade.repository.entity.TccParticipantStateEntity;
import org.springframework.stereotype.Repository;

@Repository
public class TccParticipantStateRepository extends ServiceImpl<TccParticipantStateMapper, TccParticipantStateEntity> {
    // convenience wrapper for MyBatis-Plus operations

    /**
     * Upsert participant state by txId+participantId. If a row exists update it; otherwise insert.
     */
    public void upsertState(TccParticipantStateEntity s) {
        // Use single-statement upsert for atomicity and better performance.
        // Mapper method returns affected rows.
        try {
            int affected = ((TccParticipantStateMapper) this.getBaseMapper()).upsertParticipantState(s);
            // affected may be 1 (insert) or 2 (update with MySQL ON DUPLICATE KEY UPDATE returns 2 when row updated)
        } catch (Exception ex) {
            // Fallback to previous two-step logic in case the database/driver does not support the annotated upsert.
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TccParticipantStateEntity> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq("tx_id", s.getTxId()).eq("participant_id", s.getParticipantId());
            int updated = baseMapper.update(s, uw);
            if (updated == 0) {
                s.setId(null);
                try {
                    this.save(s);
                } catch (org.springframework.dao.DuplicateKeyException dex) {
                    baseMapper.update(s, uw);
                }
            }
        }
    }
}

