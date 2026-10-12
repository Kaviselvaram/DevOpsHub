package com.devopshub.execution;

import com.devopshub.exception.ExecutionTimeoutException;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Safely executes OS processes with timeout controls and streaming stdout/stderr collection.
 */
public class ProcessExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);

    private final File workingDirectory;

    public ProcessExecutor(File workingDirectory) {
        this.workingDirectory = workingDirectory != null ? workingDirectory : new File(".");
        if (!this.workingDirectory.exists()) {
            this.workingDirectory.mkdirs();
        }
    }

    public int execute(String command, String stageExecutionId, String stageName, int timeoutSeconds, LogCollector logCollector) {
        if (command == null || command.trim().isEmpty()) {
            return 0;
        }

        ProcessBuilder pb;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("/bin/sh", "-c", command);
        }

        pb.directory(workingDirectory);
        pb.redirectErrorStream(false);

        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT, "Executing: " + command);

        Process process = null;
        ExecutorService streamReaders = Executors.newFixedThreadPool(2);

        try {
            process = pb.start();
            final Process proc = process;

            // Read stdout
            Future<?> stdoutFuture = streamReaders.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT, line);
                    }
                } catch (Exception e) {
                    // stream closed
                }
            });

            // Read stderr
            Future<?> stderrFuture = streamReaders.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR, line);
                    }
                } catch (Exception e) {
                    // stream closed
                }
            });

            boolean finished = process.waitFor(timeoutSeconds > 0 ? timeoutSeconds : 300, TimeUnit.SECONDS);
            if (!finished) {
                logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                        "Stage execution timed out after " + timeoutSeconds + " seconds.");
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                throw new ExecutionTimeoutException("Stage timed out after " + timeoutSeconds + "s", timeoutSeconds);
            }

            // Wait briefly for stream buffers to drain
            try {
                stdoutFuture.get(1, TimeUnit.SECONDS);
                stderrFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                        "Process exited with non-zero code: " + exitCode);
            } else {
                logCollector.append(stageExecutionId, stageName, LogLevel.INFO, LogStream.STDOUT,
                        "Process completed successfully with exit code 0");
            }
            return exitCode;

        } catch (InterruptedException e) {
            logCollector.append(stageExecutionId, stageName, LogLevel.WARN, LogStream.STDERR,
                    "Stage execution interrupted or cancelled by user.");
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return 130;
        } catch (ExecutionTimeoutException e) {
            return 124;
        } catch (Exception e) {
            logCollector.append(stageExecutionId, stageName, LogLevel.ERROR, LogStream.STDERR,
                    "Failed to execute process: " + e.getMessage());
            return 1;
        } finally {
            streamReaders.shutdownNow();
            logCollector.flush();
        }
    }
}
