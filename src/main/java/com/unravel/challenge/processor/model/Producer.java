package com.unravel.challenge.processor.model;

import com.unravel.challenge.processor.LogProcessor;

public class Producer extends Thread {
    private LogProcessor processor;

    public Producer(LogProcessor processor) {
        this.processor = processor;
    }

    public void run() {
        for (int i = 0; i < 100; i++) {
            processor.produceLog("Log " + i);
        }
    }
}
