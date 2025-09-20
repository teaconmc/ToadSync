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
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class TempDownloadFile implements Closeable {
    private static final HashFunction SHA256 = Hashing.sha256();

    private final AtomicReference<Path> location;

    private TempDownloadFile(Path dir) throws IOException {
        this.location = new AtomicReference<>(Files.createTempFile(Files.createDirectories(dir), "toad-sync-", ".tmp"));
    }

    private TempDownloadFile(TempDownloadFile old) throws IOException {
        var location = old.location.getAndSet(null);
        if (location == null) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
        this.location = new AtomicReference<>(location);
    }

    private Path retrieve() throws IOException {
        var location = this.location.get();
        if (location == null) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
        return location;
    }

    private void consume(Path consumed) throws IOException {
        var done = this.location.compareAndSet(consumed, null);
        if (!done) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
    }

    public static TempDownloadFile create(Path dir) throws IOException {
        return new TempDownloadFile(dir);
    }

    public void write(byte[] literal, HashCode expected) throws IOException {
        var location = this.retrieve();
        try (var in = new HashingInputStream(SHA256, new ByteArrayInputStream(literal))) {
            try (var out = Files.newOutputStream(location)) {
                in.transferTo(out);
            }
            var actual = in.hash();
            if (!actual.equals(expected)) {
                throw new IOException("mismatched sha256 hash, expected: " + expected + ", actual: " + actual);
            }
        }
    }

    public void move(Path destination) throws IOException {
        var location = this.retrieve();
        try {
            Files.move(location, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            this.consume(location);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic move unsupported for " + location + " => " + destination, e);
        }
    }

    public CompletableFuture<TempDownloadFile> download(HttpClient client, HttpRequest request,
                                                        HashCode expected) throws IOException {
        var transferred = new TempDownloadFile(this);
        var location = transferred.retrieve();
        var pending = client.sendAsync(request, i -> new HashFileSubscriber(SHA256, location));
        var result = pending.<TempDownloadFile>newIncompleteFuture();
        pending.whenComplete((r, t) -> {
            try {
                if (t != null) {
                    throw t instanceof IOException e ? e : new IOException(t);
                }
                var statusCode = r.statusCode();
                if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new IOException("Bad status code (" + statusCode + ")");
                }
                var actual = r.body();
                if (!actual.equals(expected)) {
                    throw new IOException("mismatched sha256 hash, expected: " + expected + ", actual: " + actual);
                }
                result.complete(transferred);
            } catch (Throwable throwable) {
                try {
                    transferred.close();
                } catch (IOException e) {
                    throwable.addSuppressed(e);
                }
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    @Override
    public void close() throws IOException {
        var location = this.location.getAndSet(null);
        if (location != null) {
            Files.deleteIfExists(location);
        }
    }
}
