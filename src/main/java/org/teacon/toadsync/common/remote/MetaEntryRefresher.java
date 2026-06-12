/*
 * Copyright (C) 2025 TeaConMC <contact@teacon.org>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.teacon.toadsync.common.remote;

import com.google.common.hash.HashCode;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.toadsync.spi.ToadSyncProvider;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class MetaEntryRefresher implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Path dir;
    private final HttpClient client;
    private final ToadSyncProvider provider;
    private final AtomicBoolean errorRecorded;
    private final AtomicReference<HashCode> latestDeliveredHash;
    private final AtomicReference<CompletableFuture<?>> latestTask;

    public MetaEntryRefresher(Path dir, HttpClient client, @Nullable HashCode initHash, ToadSyncProvider provider) {
        this.dir = dir;
        this.client = client;
        this.provider = provider;
        this.errorRecorded = new AtomicBoolean();
        this.latestTask = new AtomicReference<>();
        this.latestDeliveredHash = new AtomicReference<>(initHash);
    }

    public void submit(RemoteMeta.Entry entry) {
        var newHash = entry.hash();
        if (newHash.equals(this.latestDeliveredHash.get())) {
            return;
        }
        switch (entry.file().getScheme()) {
            case "data" -> {
                try (var tempFile = TempDownloadFile.create(this.dir)) {
                    tempFile.write(entry.literal(), newHash);
                    this.submit(tempFile, newHash);
                    this.errorRecorded.set(false);
                } catch (IOException e) {
                    var level = this.errorRecorded.getAndSet(true) ? Level.DEBUG : Level.WARN;
                    LOGGER.log(level, "Failed to download file for {} provider", this.provider.id(), e);
                }
            }
            case "http", "https" -> {
                try (var tempFile = TempDownloadFile.create(this.dir)) {
                    var pending = tempFile.download(this.client, entry.request(), newHash);
                    pending.whenComplete((f, t) -> {
                        if (t != null) {
                            var level = this.errorRecorded.getAndSet(true) ? Level.DEBUG : Level.WARN;
                            LOGGER.log(level, "Failed to download file for {} provider", this.provider.id(), t);
                            return;
                        }
                        try (var newTempFile = f) {
                            this.submit(newTempFile, newHash);
                            this.errorRecorded.set(false);
                        } catch (IOException e) {
                            var level = this.errorRecorded.getAndSet(true) ? Level.DEBUG : Level.WARN;
                            LOGGER.log(level, "Failed to download file for {} provider", this.provider.id(), e);
                        }
                    });
                    var oldTask = this.latestTask.getAndSet(pending);
                    if (oldTask != null) {
                        oldTask.cancel(true);
                    }
                } catch (IOException e) {
                    var level = this.errorRecorded.getAndSet(true) ? Level.DEBUG : Level.WARN;
                    LOGGER.log(level, "Failed to download file for {} provider", this.provider.id(), e);
                }
            }
        }
    }

    private void submit(TempDownloadFile temp, HashCode expected) throws IOException {
        var destFile = this.dir.toAbsolutePath().resolve(this.provider.artifact());
        temp.move(destFile);
        var old = this.latestDeliveredHash.getAndSet(expected);
        if (old == null) {
            this.provider.load(expected, destFile);
        } else {
            this.provider.update(old, expected, destFile);
        }
    }

    @Override
    public void close() {
        var task = this.latestTask.getAndSet(null);
        if (task != null) {
            task.cancel(true);
            task.exceptionally(t -> null).join();
        }
    }
}
