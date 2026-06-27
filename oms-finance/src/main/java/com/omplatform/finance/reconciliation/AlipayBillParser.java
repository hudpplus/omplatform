package com.omplatform.finance.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付宝账单 CSV 解析器（ADR-043 §4.2）。
 * <p>
 * 支付宝对账单格式（简化）：
 * <pre>
 * 支付宝交易明细
 * ================================
 * #################################
 * 商品名称,支付宝交易号,商户订单号,交易状态,订单金额,商家实收,交易时间,...
 * 商品A,202606140000100001,ORD20260614001,TRADE_SUCCESS,100.00,100.00,2026-06-14 10:00:05,...
 * 商品B,202606140000100002,ORD20260614002,TRADE_SUCCESS,200.00,200.00,2026-06-14 10:01:03,...
 * --------------------------------
 * # 合计
 * </pre>
 * <p>
 * 解析策略：跳过头部说明行 → 定位表头行 → 逐行解析数据行 → 遇到分隔线结束。
 */
@Slf4j
@Component
public class AlipayBillParser implements BillFileParser {

    @Override
    public String channelType() {
        return "ALIPAY";
    }

    @Override
    public Iterable<BillRecord> parse(String rawContent) {
        List<BillRecord> records = new ArrayList<>();
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("[支付宝账单] 内容为空");
            return records;
        }

        String[] lines = rawContent.split("\\r?\\n");
        boolean inDataSection = false;
        // 列索引（从表头 CSV 解析）
        int idxTradeNo = -1, idxOrderNo = -1, idxAmount = -1, idxStatus = -1, idxTime = -1, idxFee = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();

            // 跳过空行和分隔线
            if (line.isEmpty() || line.startsWith("--") || line.startsWith("==")
                    || line.startsWith("#") || line.startsWith("支付宝") || line.startsWith("宝贝")) {
                inDataSection = false;
                continue;
            }

            // 定位表头行：包含"交易号"或"商户订单号"
            if (line.contains("交易号") && line.contains("商户订单号")) {
                String[] headers = parseCsvLine(line);
                for (int j = 0; j < headers.length; j++) {
                    String h = headers[j].strip();
                    if (h.contains("支付宝交易号") || h.contains("交易号")) idxTradeNo = j;
                    if (h.contains("商户订单号") || h.contains("商户订单号")) idxOrderNo = j;
                    if (h.contains("订单金额") || h.contains("金额")) idxAmount = j;
                    if (h.contains("交易状态") || h.contains("状态")) idxStatus = j;
                    if (h.contains("交易时间") || h.contains("完成时间")) idxTime = j;
                    if (h.contains("服务费") || h.contains("费率")) idxFee = j;
                }
                inDataSection = true;
                log.debug("[支付宝账单] 表头解析: tradeNo={}, orderNo={}, amount={}, status={}, time={}",
                        idxTradeNo, idxOrderNo, idxAmount, idxStatus, idxTime);
                continue;
            }

            if (!inDataSection) continue;

            // 解析数据行
            try {
                String[] cols = parseCsvLine(line);
                if (cols.length <= Math.max(idxOrderNo, idxTradeNo)) continue;

                String tradeNo = idxTradeNo >= 0 && idxTradeNo < cols.length ? cols[idxTradeNo].strip() : "";
                String orderNo = idxOrderNo >= 0 && idxOrderNo < cols.length ? cols[idxOrderNo].strip() : "";
                String amountStr = idxAmount >= 0 && idxAmount < cols.length ? cols[idxAmount].strip() : "0";
                String status = idxStatus >= 0 && idxStatus < cols.length ? cols[idxStatus].strip() : "";
                String time = idxTime >= 0 && idxTime < cols.length ? cols[idxTime].strip() : "";
                String feeStr = idxFee >= 0 && idxFee < cols.length ? cols[idxFee].strip() : "0";

                // 跳过空记录和合计行
                if (tradeNo.isEmpty() && orderNo.isEmpty()) continue;
                if (tradeNo.startsWith("#") || orderNo.startsWith("#")) continue;

                BigDecimal amount = parseAmount(amountStr);
                BigDecimal fee = parseAmount(feeStr);

                // 只保留交易成功的记录
                if (isSuccessStatus(status)) {
                    records.add(new BillRecord(tradeNo, orderNo, amount, fee, time, "SUCCESS"));
                }
            } catch (Exception e) {
                log.warn("[支付宝账单] 第{}行解析异常, line={}: {}", i + 1, line, e.getMessage());
            }
        }

        log.info("[支付宝账单] 解析完成: 共 {} 条记录", records.size());
        return records;
    }

    // ========== 辅助方法 ==========

    private boolean isSuccessStatus(String status) {
        if (status == null) return false;
        return status.contains("TRADE_SUCCESS") || status.contains("交易成功")
                || status.contains("TRADE_FINISHED");
    }

    private BigDecimal parseAmount(String str) {
        if (str == null || str.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(str.strip());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析 CSV 行（支持引号包裹的字段）。
     */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
