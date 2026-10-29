package com.smartelectro.smartelectro;

import java.util.List;

public enum OrderStatus {
    ORDER_PLACED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    public static List<OrderStatus> trackingProgress() {
        return List.of(ORDER_PLACED, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED);
    }

    public static OrderStatus fromStoredValue(String value) {
        return "PLACED".equals(value) ? ORDER_PLACED : OrderStatus.valueOf(value);
    }

    public static boolean canTransition(String currentValue, OrderStatus next) {
        OrderStatus current = fromStoredValue(currentValue);
        if (next == CANCELLED) {
            return current == ORDER_PLACED;
        }
        List<OrderStatus> progress = trackingProgress();
        int currentIndex = progress.indexOf(current);
        return currentIndex >= 0 && currentIndex + 1 < progress.size()
                && progress.get(currentIndex + 1) == next;
    }
}
