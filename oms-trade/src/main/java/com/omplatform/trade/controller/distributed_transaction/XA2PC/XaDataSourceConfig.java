package com.omplatform.trade.controller.distributed_transaction.XA2PC;

import org.springframework.context.annotation.Configuration;

/**
 * Example XA configuration placeholder.
 *
 * NOTE: This class intentionally does not instantiate Atomikos or other XA
 * resources. If you want to enable XA in a real deployment, add the JTA/XA
 * dependency (e.g. Atomikos) and implement DataSource / TransactionManager beans.
 */
@Configuration
public class XaDataSourceConfig {
    // Example only: real XA beans are omitted to avoid bringing runtime
    // dependencies into the main build. See project docs for enabling XA.
}

