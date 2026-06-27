package com.omplatform.marketing.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * 积分账户实体（member_points_account）。
 */
@TableName("member_points_account")
public class PointsAccountEntity {

    /** 账户 ID */
    private String accountId;

    /** 用户 ID */
    private String userId;

    /** 可用积分 */
    private Long balance;

    /** 累计获得 */
    private Long totalEarned;

    /** 累计消费 */
    private Long totalSpent;

    /** 乐观锁 */
    @Version
    private Long version;

    /** 创建时间 */
    private LocalDateTime gmtCreate;

    /** 修改时间 */
    private LocalDateTime gmtModified;

    // ====== Getters & Setters ======

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }

    public Long getTotalEarned() { return totalEarned; }
    public void setTotalEarned(Long totalEarned) { this.totalEarned = totalEarned; }

    public Long getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Long totalSpent) { this.totalSpent = totalSpent; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }

    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }
}
