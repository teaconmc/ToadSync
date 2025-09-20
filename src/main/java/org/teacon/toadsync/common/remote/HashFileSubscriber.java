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
import com.google.common.hash.Hasher;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class HashFileSubscriber implements HttpResponse.BodySubscriber<HashCode> {
    private final Hasher hasher;
    private final OutputStream out;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final CompletableFuture<HashCode> result = new CompletableFuture<>();

    public HashFileSubscriber(HashFunction function, Path dest) {
        this.hasher = function.newHasher();
        var out = OutputStream.nullOutputStream();
        try {
            out = Files.newOutputStream(dest);
        } catch (IOException e) {
            this.result.completeExceptionally(e);
        }
        this.out = out;
    }

    private Throwable ensureClose(Throwable throwable) {
        try {
            this.out.close();
        } catch (IOException e) {
            throwable.addSuppressed(e);
        }
        return throwable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription.compareAndSet(null, Objects.requireNonNull(subscription))) {
            subscription.request(Long.MAX_VALUE);
        } else {
            subscription.cancel();
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        try {
            var bytes = new byte[item.stream().mapToInt(ByteBuffer::remaining).max().orElse(0)];
            for (var buffer : item) {
                var remaining = buffer.remaining();
                buffer.get(bytes, 0, remaining);
                this.hasher.putBytes(bytes, 0, remaining);
                this.out.write(bytes, 0, remaining);
            }
        } catch (IOException e) {
            this.result.completeExceptionally(this.ensureClose(e));
        }
    }

    @Override
    public void onError(Throwable throwable) {
        this.result.completeExceptionally(this.ensureClose(Objects.requireNonNull(throwable)));
    }

    @Override
    public void onComplete() {
        try {
            this.out.close();
        } catch (IOException e) {
            this.result.completeExceptionally(e);
        }
        this.result.complete(this.hasher.hash());
    }

    @Override
    public CompletionStage<HashCode> getBody() {
        return this.result;
    }
}
