package dev.eliasnvx.tradery.economy;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import com.google.gson.JsonObject;
import dev.eliasnvx.tradery.Tradery;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Append-only audit log, one JSON object per line, one file per UTC day: {@code <world>/tradery/logs/YYYY-MM-DD.log}.
 * Lines are written on a background thread after the transaction is committed, so the log never slows the server
 * and never decides anything: after a crash it may contain transactions the world rolled back.
 */
public final class TransactionLog implements AutoCloseable {
    private final Path directory;
    private final int retentionDays;
    /** Lines waiting for the disk at most; beyond it, lines are dropped and counted. */
    static final int QUEUE_LIMIT = 10_000;
    private final ExecutorService executor;
    private final AtomicLong dropped = new AtomicLong();
    private BufferedWriter writer;
    private LocalDate writerDay;

    public TransactionLog(Path directory, int retentionDays) {
        this.directory = directory;
        this.retentionDays = retentionDays;
        // One thread, a bounded queue: a disk that can't keep up costs log lines (counted and reported), never memory
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_LIMIT), runnable -> {
            Thread thread = new Thread(runnable, "Tradery transaction log");
            thread.setDaemon(true);
            return thread;
        }, (runnable, pool) -> {
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("closed");
            }
            if (dropped.getAndIncrement() == 0) {
                Tradery.LOGGER.warn("The transaction log can't keep up with the disk: dropping lines (counted)");
            }
        });
        executor.execute(this::deleteOldFiles);
    }

    /** Queues a line; {@code time} is added. Never blocks and never throws. */
    public void write(JsonObject line) {
        Instant now = Instant.now();
        JsonObject stamped = new JsonObject();
        stamped.addProperty("time", now.toString());
        line.entrySet().forEach(e -> stamped.add(e.getKey(), e.getValue()));
        String text = stamped.toString();
        try {
            executor.execute(() -> append(LocalDate.ofInstant(now, ZoneOffset.UTC), text));
        } catch (RejectedExecutionException e) {
            Tradery.LOGGER.warn("Transaction log closed, dropped: {}", text);
        }
    }

    private void append(LocalDate day, String text) {
        try {
            if (writer == null || !day.equals(writerDay)) {
                closeWriter();
                Files.createDirectories(directory);
                writer = Files.newBufferedWriter(directory.resolve(day + ".log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (writerDay != null) {
                    deleteOldFiles();
                }
                writerDay = day;
            }
            writer.write(text);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            Tradery.LOGGER.error("Can't write the transaction log in {}", directory, e);
            closeWriter();
        }
    }

    private void deleteOldFiles() {
        if (retentionDays <= 0 || !Files.isDirectory(directory)) {
            return;
        }
        LocalDate oldest = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.log")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                try {
                    if (LocalDate.parse(name.substring(0, name.length() - ".log".length())).isBefore(oldest)) {
                        Files.deleteIfExists(file);
                    }
                } catch (DateTimeParseException e) {
                    // not one of ours
                }
            }
        } catch (IOException e) {
            Tradery.LOGGER.warn("Can't clean old transaction logs in {}", directory, e);
        }
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                Tradery.LOGGER.warn("Can't close the transaction log", e);
            }
            writer = null;
            writerDay = null;
        }
    }

    /** Writes everything queued, then stops. Waits up to 5 seconds. */
    /** Lines dropped because the queue was full. */
    public long dropped() {
        return dropped.get();
    }

    @Override
    public void close() {
        if (dropped.get() > 0) {
            Tradery.LOGGER.warn("Transaction log: {} lines were dropped this session", dropped.get());
        }
        try {
            executor.execute(this::closeWriter);
        } catch (RejectedExecutionException ignored) {
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                Tradery.LOGGER.warn("Transaction log didn't finish writing in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
