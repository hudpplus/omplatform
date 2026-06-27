package com.omplatform.finance.reconciliation;

import java.math.BigDecimal;

/**
 * 渠道账单解析器 SPI 接口（ADR-043 §4 对账模型）。
 * <p>
 * 每个支付渠道实现此接口，将从渠道下载的原始账单文件解析为结构化记录。
 */
public interface BillFileParser {

    /**
     * 解析渠道账单原始内容。
     *
     * @param rawContent 账单原始文本（CSV / TSV）
     * @return 解析后的账单记录迭代器（可能很大，用 Iterable 避免 OOM）
     */
    Iterable<BillRecord> parse(String rawContent);

    /**
     * 支持的渠道类型。
     *
     * @return ALIPAY / WECHAT
     */
    String channelType();

    // ========== 结构化记录 ==========

    /**
     * 单笔渠道账单记录。
     */
    record BillRecord(
            String channelTradeNo,
            String orderNo,
            BigDecimal amount,
            BigDecimal fee,
            String paidAt,
            String status
    ) {}
}
