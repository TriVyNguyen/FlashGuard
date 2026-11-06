package com.flashguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Định kỳ quét các đơn PENDING đã quá hạn để trả hàng về kho Flash Sale. */
@Component
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderService orderService;

    public OrderExpirationJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.expire-scan-ms:2000}")
    public void run() {
        int expired = orderService.expireOverdueOrders();
        if (expired > 0) {
            log.info("Đã hết hạn {} đơn và trả hàng về Flash Sale", expired);
        }
    }
}
