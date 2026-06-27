package com.omplatform.trade.filter;

import com.omplatform.trade.sharding.BusinessContext;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Dubbo 提供者端过滤器 — 从 RPC 隐式参数中恢复 business_type。
 * <p>
 * 消费者端在调用前设置隐式参数，提供者端在收到请求后恢复上下文。
 * 确保 Dubbo 调用链中 ShardingSphere 能正确路由。
 */
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class DubboBusinessContextFilter implements Filter {

    private static final String BUSINESS_TYPE_KEY = "X-Business-Type";
    private static final String BUYER_ID_KEY = "X-Buyer-Id";
    private static final String ORDER_NO_KEY = "X-Order-No";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            // 从 RPC 隐式参数恢复上下文
            String businessType = invocation.getAttachment(BUSINESS_TYPE_KEY);
            String buyerId = invocation.getAttachment(BUYER_ID_KEY);
            String orderNo = invocation.getAttachment(ORDER_NO_KEY);

            if (businessType != null || buyerId != null) {
                BusinessContext.setAll(
                        businessType != null ? businessType : "ecommerce",
                        buyerId,
                        orderNo);
            }

            return invoker.invoke(invocation);
        } finally {
            BusinessContext.clear();
        }
    }
}
