package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.trade.repository.entity.TccParticipantStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface TccParticipantStateMapper extends BaseMapper<TccParticipantStateEntity> {

	/**
	 * Upsert participant state using a single SQL statement. Relies on
	 * UNIQUE(tx_id, participant_id) to resolve conflicts.
	 *
	 * Uses MySQL syntax: INSERT ... ON DUPLICATE KEY UPDATE.
	 */
	@Insert("INSERT INTO tcc_participant_state (tx_id, participant_id, status, try_data, last_attempt, gmt_create, gmt_modified, version, deleted) " +
			"VALUES (#{txId}, #{participantId}, #{status}, #{tryData}, #{lastAttempt}, #{gmtCreate}, #{gmtModified}, #{version}, #{deleted}) " +
			"ON DUPLICATE KEY UPDATE status=VALUES(status), try_data=VALUES(try_data), last_attempt=VALUES(last_attempt), gmt_modified=VALUES(gmt_modified), version=VALUES(version), deleted=VALUES(deleted)")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int upsertParticipantState(TccParticipantStateEntity s);

}
