package com.omplatform.trade.controller.distributed_transaction.tcc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * TCC 事务上下文 —— 在各参与者间共享的订单数据。
 */
public class TccTransactionContext {
    private final String txId;       // 全局事务 ID
    private final String orderNo;
    private final String buyerId;
    private final List<ItemLine> items;
    private final BigDecimal totalAmount;

    private TccTransactionContext(Builder b) {
        this.txId = b.txId;
        this.orderNo = b.orderNo;
        this.buyerId = b.buyerId;
        this.items = b.items;
        this.totalAmount = b.totalAmount;
    }

    public static Builder builder() { return new Builder(); }

    public String getTxId() { return txId; }
    public String getOrderNo() { return orderNo; }
    public String getBuyerId() { return buyerId; }
    public List<ItemLine> getItems() { return items; }
    public BigDecimal getTotalAmount() { return totalAmount; }

    public static class Builder {
        private String txId;
        private String orderNo;
        private String buyerId;
        private List<ItemLine> items;
        private BigDecimal totalAmount;

        public Builder txId(String txId) { this.txId = txId; return this; }
        public Builder orderNo(String orderNo) { this.orderNo = orderNo; return this; }
        public Builder buyerId(String buyerId) { this.buyerId = buyerId; return this; }
        public Builder items(List<ItemLine> items) { this.items = items; return this; }
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }

        public TccTransactionContext build() {
            Objects.requireNonNull(txId, "txId required");
            return new TccTransactionContext(this);
        }
    }

    public static class ItemLine {
        private final String skuId;
        private final Integer quantity;
        private final BigDecimal unitPrice;

        public ItemLine(String skuId, Integer quantity, BigDecimal unitPrice) {
            this.skuId = skuId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getSkuId() { return skuId; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }
}

