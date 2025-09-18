package org.teacon.toadsync.common.remote;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.google.common.collect.ImmutableMap;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class MetaValidatableRefresher implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();

    private final HttpClient client;
    private final Executor clientExecutor;
    private final AtomicReference<Task> latestTask;
    private final ImmutableMap<String, MetaEntryRefresher> entryRefreshers;

    public MetaValidatableRefresher(HttpClient client, Map<String, MetaEntryRefresher> entryRefreshers) {
        this.client = client;
        this.latestTask = new AtomicReference<>();
        this.clientExecutor = client.executor().orElseThrow();
        this.entryRefreshers = ImmutableMap.copyOf(entryRefreshers);
    }

    public void submit(FileConfig config) {
        var meta = RemoteMeta.of();
        // load the file config
        try {
            config.load();
            meta = meta.read(config);
        } catch (ParsingException e) {
            LOGGER.warn("Failed to read the remote meta", e);
        }
        // enter submit cycles if http download the meta is needed
        if (meta.remote().isPresent()) {
            this.submit(RemoteMeta.Validatable.of(meta), 0L, config);
            return;
        }
        // just sync the entries otherwise
        this.submit(RemoteMeta.Validatable.of(meta), (Duration) null, config);
    }

    private void submit(RemoteMeta.Validatable validatable, long delayMillis, FileConfig config) {
        // check if http download of the meta is needed
        var request = validatable.request();
        if (request.isEmpty()) {
            return;
        }
        // launch a new delay task
        var delayed = CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, this.clientExecutor);
        var pending = CompletableFuture.runAsync(() -> this.submit(validatable, request.get(), config), delayed);
        // cancel the old delayed task
        var oldTask = this.latestTask.getAndSet(new Task(pending));
        if (oldTask != null) {
            oldTask.close();
        }
    }

    private void submit(RemoteMeta.Validatable validatable, HttpRequest request, FileConfig config) {
        // launch a new http download task and cancel the old one
        var pending = this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var oldTask = this.latestTask.getAndSet(new Task(pending));
        if (oldTask != null) {
            oldTask.close();
        }
        // the callback will be invoked when the complete response is sent
        pending.whenComplete((r, t) -> {
            var newValidatable = validatable;
            var newInterval = validatable.meta().interval();
            try {
                // throw error if raised in http connection
                if (t != null) {
                    if (t instanceof CompletionException e) {
                        t = e.getCause();
                    }
                    if (t instanceof ParsingException e) {
                        throw e;
                    }
                    if (t instanceof IOException e) {
                        throw e;
                    }
                }
                // throw error if the status code >= 400
                if (r.statusCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new IOException("Bad status code (" + r.statusCode() + ")");
                }
                // parse the content and add validatable information unless 304 returned
                if (r.statusCode() != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    var parser = config.configFormat().createParser();
                    parser.parse(r.body(), config, ParsingMode.MERGE);
                    newValidatable = RemoteMeta.Validatable.of(validatable.read(config), r.headers());
                    // write new config to file
                    config.save();
                }
                // get next interval
                newInterval = newValidatable.meta().interval();
            } catch (IOException | ParsingException e) {
                LOGGER.warn("Failed to read the remote meta", e);
            } finally {
                // iterate sync entries and submit refreshes
                this.submit(newValidatable, newInterval.orElse(null), config);
            }
        });
    }

    private void submit(RemoteMeta.Validatable validatable, @Nullable Duration interval, FileConfig config) {
        var syncEntries = validatable.meta().syncEntries();
        for (var entry : this.entryRefreshers.entrySet()) {
            var syncEntry = syncEntries.get(entry.getKey());
            if (syncEntry != null) {
                entry.getValue().submit(syncEntry);
            }
        }
        // enter next submit cycle if needed
        if (interval != null) {
            this.submit(validatable, interval.toMillis(), config);
        }
    }

    @Override
    public void close() {
        var task = this.latestTask.getAndSet(null);
        if (task != null) {
            task.close();
        }
        for (var refresher : this.entryRefreshers.values()) {
            refresher.close();
        }
    }

    private record Task(CompletableFuture<?> pending) implements Closeable {
        @Override
        public void close() {
            this.pending.cancel(true);
            this.pending.exceptionally(t -> null).join();
        }
    }
}
