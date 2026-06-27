package com.omplatform.trade.es.document;

import com.omplatform.common.constant.OrderStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 订单文档（CQRS 读模型 — 扁平宽表设计）。
 * <p>
 * 针对查询场景优化，去掉了不需要搜索的字段（addressId 等），
 * 扁平化嵌套商品行用于展示和关键词搜索。
 */
@Data
@Document(indexName = "#{@esIndexNameProvider.indexName}")
@Setting(shards = 1, replicas = 0)
public class OrderDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private String orderNo;

    @Field(type = FieldType.Keyword)
    private String parentOrderNo;

    @Field(type = FieldType.Keyword)
    private String buyerId;

    @Field(type = FieldType.Keyword)
    private String shopId;

    /** 业务线标识（ADR-017 ES 索引路由用） */
    @Field(type = FieldType.Keyword)
    private String businessType;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword, index = false)
    private String previousStatus;

    @Field(type = FieldType.Double, index = false)
    private BigDecimal totalAmount;

    @Field(type = FieldType.Double, index = false)
    private BigDecimal payAmount;

    @Field(type = FieldType.Double, index = false)
    private BigDecimal freightAmount;

    @Field(type = FieldType.Double, index = false)
    private BigDecimal discountAmount;

    @Field(type = FieldType.Text, index = false)
    private String remark;

    @Field(type = FieldType.Keyword, index = false)
    private String channelSource;

    @Field(type = FieldType.Date)
    private LocalDateTime statusChangedAt;

    @Field(type = FieldType.Date)
    private LocalDateTime statusExpiresAt;

    @Field(type = FieldType.Date)
    private LocalDateTime gmtCreate;

    @Field(type = FieldType.Date, index = false)
    private LocalDateTime gmtModified;

    /** 复合搜索字段：orderNo + 商品名拼接，供 keyword 模糊搜索 */
    @Field(type = FieldType.Text)
    private String searchText;

    /** 嵌套商品行 */
    @Field(type = FieldType.Nested)
    private List<OrderItemDoc> items;

    @Data
    public static class OrderItemDoc {
        @Field(type = FieldType.Keyword)
        private String itemId;

        @Field(type = FieldType.Keyword)
        private String skuId;

        @Field(type = FieldType.Text)
        private String skuName;

        @Field(type = FieldType.Integer, index = false)
        private Integer quantity;

        @Field(type = FieldType.Double, index = false)
        private BigDecimal unitPrice;

        @Field(type = FieldType.Double, index = false)
        private BigDecimal subtotal;

        @Field(type = FieldType.Double, index = false)
        private BigDecimal discountAmount;
    }
}
