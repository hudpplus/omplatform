package com.omplatform.api.order.dto;

import com.omplatform.common.api.PageParam;
import com.omplatform.common.constant.OrderStatus;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单查询请求（CQRS 查询端）。
 */
public class OrderQueryRequest extends PageParam {

    @Serial
    private static final long serialVersionUID = 1L;

    private String buyerId;
    private String shopId;
    private List<OrderStatus> statusList;
    private LocalDateTime createTimeFrom;
    private LocalDateTime createTimeTo;
    private String keyword;       // 模糊搜索（订单号 / 商品名）

    public OrderQueryRequest() {}

    public OrderQueryRequest(String buyerId, String shopId, List<OrderStatus> statusList,
                             LocalDateTime createTimeFrom, LocalDateTime createTimeTo, String keyword) {
        this.buyerId = buyerId;
        this.shopId = shopId;
        this.statusList = statusList;
        this.createTimeFrom = createTimeFrom;
        this.createTimeTo = createTimeTo;
        this.keyword = keyword;
    }

    // ========== getter/setter ==========

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public List<OrderStatus> getStatusList() { return statusList; }
    public void setStatusList(List<OrderStatus> statusList) { this.statusList = statusList; }

    public LocalDateTime getCreateTimeFrom() { return createTimeFrom; }
    public void setCreateTimeFrom(LocalDateTime createTimeFrom) { this.createTimeFrom = createTimeFrom; }

    public LocalDateTime getCreateTimeTo() { return createTimeTo; }
    public void setCreateTimeTo(LocalDateTime createTimeTo) { this.createTimeTo = createTimeTo; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
