package com.devopshub.execution.event;

import com.devopshub.repository.ExecutionEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe pub/sub event bus for execution lifecycle events.
 * Guarantees monotonic sequence ordering, memory buffering for live subscribers,
 * and persistence for historical replay.
 */
public class ExecutionEventBus {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEventBus.class);
    private static final ExecutionEventBus INSTANCE = new ExecutionEventBus();

    private final ExecutionEventRepository repository;
    private final Map<String, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> memoryEventLog = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<ExecutionEvent>>> subscribers = new ConcurrentHashMap<>();
    private final List<Consumer<ExecutionEvent>> globalSubscribers = new CopyOnWriteArrayList<>();

    private ExecutionEventBus() {
        this.repository = new ExecutionEventRepository();
    }

    public ExecutionEventBus(ExecutionEventRepository repository) {
        this.repository = repository;
    }

    public static ExecutionEventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Publishes a new lifecycle event.
     * Automatically assigns a monotonic sequence number for the execution.
     */
    public ExecutionEvent publish(String executionId, String pipelineId, ExecutionEventType type, String message) {
        return publish(executionId, pipelineId, null, null, 0, type, null, message, null, null, true);
    }

    public ExecutionEvent publish(String executionId, String pipelineId, String stageExecutionId,
                                  String stageName, int stageOrder, ExecutionEventType type,
                                  String statusTransition, String message, Double durationSeconds,
                                  Integer exitCode, boolean authoritative) {
        long seq = sequenceCounters.computeIfAbsent(executionId, k -> new AtomicLong(0)).incrementAndGet();

        ExecutionEvent event = new ExecutionEvent(
                UUID.randomUUID().toString(), seq, executionId, pipelineId,
                stageExecutionId, stageName, stageOrder, type,
                new java.sql.Timestamp(System.currentTimeMillis()),
                statusTransition, message, durationSeconds, exitCode, authoritative
        );

        // Store in in-memory event log (capped at 500 per execution)
        memoryEventLog.compute(executionId, (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(event);
            if (list.size() > 500) {
                list.remove(0);
            }
            return list;
        });

        // Persist to repository
        try {
            repository.save(event);
        } catch (Exception e) {
            logger.warn("Could not persist execution event #{}: {}", seq, e.getMessage());
        }

        // Dispatch to execution subscribers
        List<Consumer<ExecutionEvent>> list = subscribers.get(executionId);
        if (list != null) {
            for (Consumer<ExecutionEvent> consumer : list) {
                try {
                    consumer.accept(event);
                } catch (Exception e) {
                    logger.warn("Subscriber failed on event #{}: {}", seq, e.getMessage());
                }
            }
        }

        // Dispatch to global subscribers
        for (Consumer<ExecutionEvent> consumer : globalSubscribers) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                logger.warn("Global subscriber failed on event #{}: {}", seq, e.getMessage());
            }
        }

        return event;
    }

    public void subscribe(String executionId, Consumer<ExecutionEvent> listener) {
        if (executionId == null || listener == null) return;
        subscribers.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(String executionId, Consumer<ExecutionEvent> listener) {
        if (executionId == null || listener == null) return;
        List<Consumer<ExecutionEvent>> list = subscribers.get(executionId);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                subscribers.remove(executionId);
            }
        }
    }

    public void subscribeGlobal(Consumer<ExecutionEvent> listener) {
        if (listener != null) {
            globalSubscribers.add(listener);
        }
    }

    public void unsubscribeGlobal(Consumer<ExecutionEvent> listener) {
        if (listener != null) {
            globalSubscribers.remove(listener);
        }
    }

    /**
     * Retrieves all events for an execution in sequence order.
     * Uses memory log if available, otherwise queries persistent repository.
     */
    public List<ExecutionEvent> getEvents(String executionId) {
        if (executionId == null) return Collections.emptyList();
        List<ExecutionEvent> inMem = memoryEventLog.get(executionId);
        if (inMem != null && !inMem.isEmpty()) {
            return new ArrayList<>(inMem);
        }
        return repository.findByExecutionId(executionId);
    }

    public void clearMemoryBuffer(String executionId) {
        if (executionId != null) {
            memoryEventLog.remove(executionId);
            sequenceCounters.remove(executionId);
            subscribers.remove(executionId);
        }
    }
}
