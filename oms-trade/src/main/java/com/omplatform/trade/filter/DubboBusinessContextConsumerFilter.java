package com.omplatform.trade.filter;

import com.omplatform.trade.sharding.BusinessContext;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Dubbo 消费者端过滤器 — 将 business_type 注入 RPC 隐式参数。
 * <p>
 * 在发起 Dubbo 调用前，将当前 {@link BusinessContext} 的值设置到 RPC
 * 附件的隐式参数中，使提供者端能恢复上下文。
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class DubboBusinessContextConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String businessType = BusinessContext.getBusinessType();
        String buyerId = BusinessContext.getBuyerId();
        String orderNo = BusinessContext.getOrderNo();

        if (businessType != null) {
            invocation.setAttachment("X-Business-Type", businessType);
        }
        if (buyerId != null) {
            invocation.setAttachment("X-Buyer-Id", buyerId);
        }
        if (orderNo != null) {
            invocation.setAttachment("X-Order-No", orderNo);
        }

        return invoker.invoke(invocation);
    }
}
