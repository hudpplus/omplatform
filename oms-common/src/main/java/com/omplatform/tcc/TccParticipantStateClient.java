package com.omplatform.tcc;

import java.time.LocalDateTime;

/**
 * Lightweight interface for other modules to interact with TCC participant state
 * without depending on the concrete repository/entity types.
 */
public interface TccParticipantStateClient {

    /**
     * Get participant state status for a given txId+participantId. Returns null if not found.
     */
    String getStatus(String txId, String participantId);

    /**
     * Upsert participant state with provided status and tryData (optional).
     */
    void upsertStatus(String txId,
                      String participantId,
                      String status,
                      String tryData,
                      LocalDateTime lastAttempt);
}

