package com.omplatform.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体基类。
 * <p>
 * 所有数据库实体继承此类，统一乐观锁与审计字段。
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 乐观锁版本号 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    /** 修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;

    /** 逻辑删除标记（0=正常，1=已删） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
