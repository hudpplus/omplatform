package com.omplatform.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用分页请求参数。
 */
public class PageParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    @Max(500)
    private int pageSize = 20;

    /** 排序字段（可选） */
    private String orderBy;

    /** 排序方向：asc / desc */
    private String orderDir = "desc";

    public PageParam() {}

    public PageParam(int pageNo, int pageSize, String orderBy, String orderDir) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.orderBy = orderBy;
        this.orderDir = orderDir;
    }

    // ========== getter/setter ==========

    public int getPageNo() { return pageNo; }
    public void setPageNo(int pageNo) { this.pageNo = pageNo; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public String getOrderDir() { return orderDir; }
    public void setOrderDir(String orderDir) { this.orderDir = orderDir; }
}
