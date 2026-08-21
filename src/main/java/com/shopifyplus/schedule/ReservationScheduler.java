package com.shopifyplus.schedule;

import com.shopifyplus.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Periodically releases stock held by pending orders that were never paid.
@Component
public class ReservationScheduler {

    private final OrderService orderService;

    public ReservationScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(
            fixedDelayString = "${app.checkout.release-scan-ms:60000}",
            initialDelayString = "${app.checkout.release-scan-ms:60000}")
    public void releaseExpired() {
        orderService.releaseExpiredReservations();
    }
}
