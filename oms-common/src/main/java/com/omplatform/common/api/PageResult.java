package com.omplatform.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private int pageNo;
    private int pageSize;

    /** 总页数 */
    public long getPages() {
        if (pageSize <= 0) return 0;
        return (total + pageSize - 1) / pageSize;
    }

    /** 是否还有下一页 */
    public boolean hasMore() {
        return pageNo * pageSize < total;
    }

    public static <T> PageResult<T> empty() {
        return PageResult.<T>builder()
                .records(Collections.emptyList())
                .total(0)
                .pageNo(1)
                .pageSize(20)
                .build();
    }
}
