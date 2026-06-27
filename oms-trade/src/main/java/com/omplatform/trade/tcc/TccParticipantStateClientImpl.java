package com.omplatform.trade.tcc;

import com.omplatform.tcc.TccParticipantStateClient;
import org.springframework.beans.factory.annotation.Autowired;
import com.omplatform.trade.repository.TccParticipantStateRepository;
import com.omplatform.trade.repository.entity.TccParticipantStateEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TccParticipantStateClientImpl implements TccParticipantStateClient {

    @Autowired
    private TccParticipantStateRepository repo;

    /* public TccParticipantStateClientImpl(TccParticipantStateRepository repo) {
        this.repo = repo;
    } */

    @Override
    public String getStatus(String txId, String participantId) {
        TccParticipantStateEntity q = new TccParticipantStateEntity();
        q.setTxId(txId);
        q.setParticipantId(participantId);
        // use MyBatis-Plus lambdaQuery for clarity
        TccParticipantStateEntity found = repo.lambdaQuery()
                .eq(TccParticipantStateEntity::getTxId, txId)
                .eq(TccParticipantStateEntity::getParticipantId, participantId)
                .one();
        return found == null ? null : found.getStatus();
    }

    @Override
    public void upsertStatus(String txId, String participantId, String status, String tryData, LocalDateTime lastAttempt) {
        TccParticipantStateEntity s = new TccParticipantStateEntity();
        s.setTxId(txId);
        s.setParticipantId(participantId);
        s.setStatus(status);
        s.setTryData(tryData);
        s.setLastAttempt(lastAttempt == null ? LocalDateTime.now() : lastAttempt);
        repo.upsertState(s);
    }
}

