package com.devopshub.execution;

import com.devopshub.model.ExecutionLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Thread-safe circular buffer for log lines.
 * Prevents JVM OutOfMemory errors by capping in-memory retention.
 */
public class BoundedLogBuffer {
    private final int capacity;
    private final Deque<ExecutionLog> buffer;
    private boolean truncatedNoticeAdded = false;

    public BoundedLogBuffer(int capacity) {
        this.capacity = capacity > 0 ? capacity : 5000;
        this.buffer = new ArrayDeque<>(this.capacity);
    }

    public synchronized void add(ExecutionLog log) {
        if (buffer.size() >= capacity) {
            buffer.pollFirst(); // Drop oldest line
            if (!truncatedNoticeAdded) {
                truncatedNoticeAdded = true;
                ExecutionLog notice = new ExecutionLog();
                notice.setLogLevel(com.devopshub.model.LogLevel.WARN);
                notice.setMessage("[LOG BUFFER NOTICE] Log output exceeded in-memory capacity (" + capacity + " lines). Older lines preserved in persistent storage.");
                notice.setTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
                buffer.addFirst(notice);
            }
        }
        buffer.addLast(log);
    }

    public synchronized List<ExecutionLog> getRecentLogs() {
        return new ArrayList<>(buffer);
    }

    public synchronized void clear() {
        buffer.clear();
    }

    public synchronized int size() {
        return buffer.size();
    }
}
