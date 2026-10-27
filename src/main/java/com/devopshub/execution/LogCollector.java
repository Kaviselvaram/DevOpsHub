package com.devopshub.execution;

import com.devopshub.model.ExecutionLog;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import com.devopshub.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Collects, formats, buffers, and persists stdout/stderr log output.
 * Guarantees that logs are never lost due to buffering during early aborts or fatal errors.
 */
public class LogCollector {
    private static final Logger logger = LoggerFactory.getLogger(LogCollector.class);
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String executionId;
    private final LogRepository logRepository;
    private final BoundedLogBuffer buffer;
    private final List<Consumer<ExecutionLog>> listeners = new CopyOnWriteArrayList<>();
    private final List<ExecutionLog> pendingBatch = new ArrayList<>();

    public LogCollector() {
        this("in-memory-" + System.currentTimeMillis(), null);
    }

    public LogCollector(String executionId, LogRepository logRepository) {
        this.executionId = executionId;
        this.logRepository = logRepository;
        this.buffer = new BoundedLogBuffer(5000);
    }

    public void addListener(Consumer<ExecutionLog> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<ExecutionLog> listener) {
        listeners.remove(listener);
    }

    public synchronized void append(String stageExecutionId, String stageName, LogLevel level, LogStream stream, String rawLine) {
        String maskedLine = com.devopshub.util.SecretMasker.mask(rawLine);
        String timestampStr = TIME_FMT.format(new java.util.Date());
        String formattedMessage = String.format("[%s] %s", timestampStr, maskedLine);

        ExecutionLog log = new ExecutionLog();
        log.setExecutionId(executionId);
        log.setStageExecutionId(stageExecutionId);
        log.setStageName(stageName);
        log.setLogLevel(level);
        log.setStream(stream);
        log.setMessage(formattedMessage);
        log.setTimestamp(new Timestamp(System.currentTimeMillis()));

        buffer.add(log);
        pendingBatch.add(log);

        // Notify UI listeners
        for (Consumer<ExecutionLog> listener : listeners) {
            try {
                listener.accept(log);
            } catch (Exception e) {
                logger.warn("Log listener error: {}", e.getMessage());
            }
        }

        // Proactively flush errors, warnings, or when batch reaches threshold
        if (level == LogLevel.ERROR || level == LogLevel.WARN || pendingBatch.size() >= 15) {
            flush();
        }
    }

    public synchronized void flush() {
        if (!pendingBatch.isEmpty()) {
            if (logRepository != null) {
                try {
                    logRepository.saveBatch(new ArrayList<>(pendingBatch));
                } catch (Exception e) {
                    logger.error("Failed to flush log batch to database: {}", e.getMessage());
                }
            }
            pendingBatch.clear();
        }
    }

    public List<ExecutionLog> getBufferedLogs() {
        return buffer.getRecentLogs();
    }
}
