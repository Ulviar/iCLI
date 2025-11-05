package com.github.ulviar.icli.fixture;

final class Sleeper {
    private Sleeper() {}

    static void sleepMillis(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
