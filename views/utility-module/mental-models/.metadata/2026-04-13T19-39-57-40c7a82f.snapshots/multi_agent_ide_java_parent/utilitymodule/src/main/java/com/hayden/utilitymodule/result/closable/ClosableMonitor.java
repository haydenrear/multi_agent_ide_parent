package com.hayden.utilitymodule.result.closable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Slf4j
public class ClosableMonitor {

    Queue<AutoCloseable> q = new ConcurrentLinkedQueue<>();

    public <T extends AutoCloseable> void afterClose(Supplier<T> e) {
        var ret = e.get();
        warningLogger(ret);
        synchronized (ret) {
            q.removeIf(c -> c == ret);
        }
    }

    public <T extends AutoCloseable> void onInitialize(Supplier<T> e) {
        var ret = e.get();
        warningLogger(ret);
        synchronized (ret) {
            q.removeIf(c -> c == ret);
            q.add(ret);
        }
    }

    public synchronized boolean hasOpenResources() {
        return !q.isEmpty();
    }

    public synchronized void registerClosed(AutoCloseable closeable) {
        q.stream().filter(a -> a == closeable).findFirst()
                .ifPresent(a -> q.remove(a));
    }

    @SneakyThrows
    public synchronized void closeAll() {
        q.forEach(a -> {
            try {
                a.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        q.clear();
    }

    private <T extends AutoCloseable> void warningLogger(T ret) {
        log.debug("Testing if {} exists in {}", ret, this.getClass().getName());
    }

}
