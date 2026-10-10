package com.finance.common;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class CustomUncaughtExceptionHandlerTest {

    @Test
    public void delegatesToOriginalHandlerWithoutRecursing() {
        AtomicInteger calls = new AtomicInteger();
        Thread.UncaughtExceptionHandler originalHandler =
                (thread, exception) -> calls.incrementAndGet();
        CustomUncaughtExceptionHandler handler = new CustomUncaughtExceptionHandler();
        RuntimeException exception = new RuntimeException("test");

        Thread.UncaughtExceptionHandler previousHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler);
            handler.init((Application)ApplicationProvider.getApplicationContext());
            handler.uncaughtException(Thread.currentThread(), exception);

            assertEquals(1, calls.get());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }
    }
}
