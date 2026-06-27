package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * TCC 参与者状态表。
 */
@TableName("tcc_participant_state")
public class TccParticipantStateEntity extends BaseEntity {

    @TableId
    private Long id;

    private String txId;

    private String participantId;

    private String status; // INIT / TRIED / CONFIRMED / CANCELLED

    private String tryData; // 可选：JSON 文本，存 Try 阶段需要的数据（例如 orderNo 等）

    private LocalDateTime lastAttempt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTryData() { return tryData; }
    public void setTryData(String tryData) { this.tryData = tryData; }

    public LocalDateTime getLastAttempt() { return lastAttempt; }
    public void setLastAttempt(LocalDateTime lastAttempt) { this.lastAttempt = lastAttempt; }
}

