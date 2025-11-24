package com.unravel.challenge.concurrency.model;

import lombok.Getter;

public enum LogPriority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4);

    @Getter
    private final int value;

    LogPriority(int value) {
        this.value = value;
    }
}
