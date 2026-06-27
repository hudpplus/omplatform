package com.omplatform.finance.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 微信支付账单 CSV 解析器（ADR-043 §4.2）。
 * <p>
 * 微信对账单格式（简化，V3 API）：
 * <pre>
 * 交易时间,公众账号ID,商户号,子商户号,设备号,微信订单号,商户订单号,用户标识,交易类型,交易状态,付款银行,货币种类,应结订单金额,代金券金额,退款金额,充值券退款金额,手续费,费率,订单金额,申请退款金额,...
 * `2026-06-14 10:00:00,wx0000,10000000,0,,4200000001,ORD20260614001,o0000,NATIVE,SUCCESS,OTHERS,CNY,100.00,0.00,0.00,0.00,0.60,0.60%,100.00,0.00,` ...
 * </pre>
 * <p>
 * 微信 CSV 以反引号包裹整行，行内以逗号分隔。
 * 第一行为表头，后续为数据行。
 */
@Slf4j
@Component
public class WechatBillParser implements BillFileParser {

    @Override
    public String channelType() {
        return "WECHAT";
    }

    @Override
    public Iterable<BillRecord> parse(String rawContent) {
        List<BillRecord> records = new ArrayList<>();
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("[微信账单] 内容为空");
            return records;
        }

        String[] lines = rawContent.split("\\r?\\n");
        // 列索引
        int idxTradeNo = -1, idxOrderNo = -1, idxAmount = -1, idxFee = -1;
        boolean hasHeader = false;

        for (int i = 0; i < lines.length; i++) {
            String line = stripBacktick(lines[i]).strip();

            // 跳过空行、分隔线、汇总行
            if (line.isEmpty() || line.startsWith("--") || line.startsWith("总")
                    || line.startsWith("`--") || line.startsWith("`总")) {
                continue;
            }

            // 定位表头
            if (!hasHeader && (line.contains("微信订单号") || line.contains("商户订单号"))) {
                String[] headers = parseCsvLine(line);
                for (int j = 0; j < headers.length; j++) {
                    String h = headers[j].strip();
                    if (h.contains("微信订单号") || h.contains("交易号")) idxTradeNo = j;
                    if (h.contains("商户订单号") || h.contains("商户订单号")) idxOrderNo = j;
                    if (h.contains("应结订单金额") || h.contains("订单金额")) idxAmount = j;
                    if (h.contains("手续费") || h.contains("费率")) idxFee = j;
                }
                hasHeader = true;
                log.debug("[微信账单] 表头解析: tradeNo={}, orderNo={}, amount={}, fee={}",
                        idxTradeNo, idxOrderNo, idxAmount, idxFee);
                continue;
            }

            if (!hasHeader) continue;

            // 解析数据行
            try {
                String[] cols = parseCsvLine(line);
                if (cols.length <= Math.max(idxOrderNo, idxTradeNo)) continue;

                String tradeNo = idxTradeNo >= 0 && idxTradeNo < cols.length ? cols[idxTradeNo].strip() : "";
                String orderNo = idxOrderNo >= 0 && idxOrderNo < cols.length ? cols[idxOrderNo].strip() : "";

                // 跳过空记录、汇总行
                if (tradeNo.isEmpty() && orderNo.isEmpty()) continue;
                if (tradeNo.equals("`") || orderNo.equals("`")) continue;

                // 金额（微信以分为单位，需转为元）
                String amountStr = idxAmount >= 0 && idxAmount < cols.length ? cols[idxAmount].strip() : "0";
                BigDecimal amount = parseFen(amountStr);

                String feeStr = idxFee >= 0 && idxFee < cols.length ? cols[idxFee].strip() : "0";
                BigDecimal fee = parseFen(feeStr);

                // 微信 CSV 中交易状态在第 9 列（索引 9）
                String status = cols.length > 9 ? cols[9].strip() : "";

                records.add(new BillRecord(tradeNo, orderNo, amount, fee, "", status));
            } catch (Exception e) {
                log.warn("[微信账单] 第{}行解析异常: {}", i + 1, e.getMessage());
            }
        }

        log.info("[微信账单] 解析完成: 共 {} 条记录", records.size());
        return records;
    }

    // ========== 辅助方法 ==========

    /**
     * 去除微信 CSV 行首尾的反引号包裹。
     */
    private String stripBacktick(String line) {
        if (line == null) return "";
        String s = line.strip();
        if (s.startsWith("`")) s = s.substring(1);
        if (s.endsWith("`")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * 将微信的分值转为元（BigDecimal）。
     */
    private BigDecimal parseFen(String str) {
        if (str == null || str.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(str.strip()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析 CSV 行。
     */
    private String[] parseCsvLine(String line) {
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
