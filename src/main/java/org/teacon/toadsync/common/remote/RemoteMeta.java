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

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class RemoteMeta {
    private static final Pattern SHA256 = Pattern.compile("[A-fa-f0-9]{64}");
    private static final RemoteMeta EMPTY = new RemoteMeta(null, null, ImmutableMap.of());

    private final ImmutableMap<String, Entry> syncEntries;
    private final @Nullable Duration interval;
    private final @Nullable URI remote;

    private RemoteMeta(@Nullable Duration interval, @Nullable URI remote, ImmutableMap<String, Entry> syncEntries) {
        this.syncEntries = syncEntries;
        this.interval = interval;
        this.remote = remote;
    }

    public static RemoteMeta of() {
        return EMPTY;
    }

    public RemoteMeta read(UnmodifiableConfig config) throws ParsingException {
        var interval = switch (config.get("interval")) {
            case null -> this.interval;
            case Integer i when i >= 1L -> Duration.ofSeconds(i);
            case Long l when l >= 1L && l <= Integer.MAX_VALUE -> Duration.ofSeconds(l);
            case Double d when d >= 5e-4 && d <= Integer.MAX_VALUE -> Duration.ofMillis(Math.round(d * 1e3));
            default -> throw new ParsingException("Invalid interval entry: " + config.get("interval"));
        };
        var remote = switch (config.get("remote")) {
            case null -> this.remote;
            case String s -> {
                try {
                    var uri = new URI(s);
                    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                        throw new ParsingException("Unsupported protocol of remote entry: " + s);
                    }
                    yield uri;
                } catch (URISyntaxException e) {
                    throw new ParsingException("Invalid remote entry: " + s, e);
                }
            }
            default -> throw new ParsingException("Invalid remote entry: " + config.get("remote"));
        };
        if (interval == null && remote != null) {
            throw new ParsingException("Interval must be specified if remote is specified");
        }
        var syncEntries = new LinkedHashMap<>(this.syncEntries);
        if (config.get("sync") instanceof UnmodifiableConfig c) {
            for (var sync : c.entrySet()) {
                if (sync.getValue() instanceof UnmodifiableConfig m) {
                    var fmt = switch (m.get("hash-format")) {
                        case String s -> s;
                        case null -> "sha256";
                        default -> throw new ParsingException("Invalid hash format: " + m.get("hash-format"));
                    };
                    if (!"sha256".equals(fmt)) {
                        throw new ParsingException("Only sha256 hash format supported but get " + fmt);
                    }
                    var hash = switch (m.get("hash")) {
                        case String s when SHA256.matcher(s).matches() -> HashCode.fromString(s);
                        case null, default -> throw new ParsingException("Invalid sha256 hash entry: " + m.get("hash"));
                    };
                    var file = switch (m.get("file")) {
                        case String s -> {
                            try {
                                var uri = remote == null ? new URI(s) : remote.resolve(new URI(s));
                                yield switch (uri.getScheme()) {
                                    case "data", "http", "https" -> uri;
                                    case null, default -> throw new ParsingException("Invalid file entry: " + s);
                                };
                            } catch (URISyntaxException e) {
                                throw new ParsingException("Invalid file entry: " + s, e);
                            }
                        }
                        case null, default -> throw new ParsingException("Invalid file entry: " + m.get("file"));
                    };
                    var id = sync.getKey();
                    syncEntries.put(id, new Entry(hash, file));
                }
            }
        }
        return new RemoteMeta(interval, remote, ImmutableMap.copyOf(syncEntries));
    }

    public Optional<URI> remote() {
        return Optional.ofNullable(this.remote);
    }

    public Optional<Duration> interval() {
        return Optional.ofNullable(this.interval);
    }

    public ImmutableMap<String, Entry> syncEntries() {
        return this.syncEntries;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.syncEntries, this.interval, this.remote);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RemoteMeta that && this.syncEntries.equals(that.syncEntries)
                && Objects.equals(this.interval, that.interval) && Objects.equals(this.remote, that.remote);
    }

    public record Entry(HashCode hash, URI file) {
        public byte[] literal() throws IOException {
            try {
                if (!"data".equals(this.file.getScheme())) {
                    throw new IllegalArgumentException("invalid URI scheme " + this.file.getScheme());
                }
                var split = this.file.toString().substring(5).split(",", 2);
                if (split.length != 2) {
                    throw new IllegalArgumentException("invalid data URI: " + this.file);
                }
                var decodeCharset = StandardCharsets.ISO_8859_1; // latin-1 for byte-to-byte mapping
                var data = URLDecoder.decode(split[1], decodeCharset).getBytes(decodeCharset);
                return split[1].endsWith(";base64") ? Base64.getDecoder().decode(data) : data;
            } catch (IllegalArgumentException e) {
                throw new IOException("Failed to parse the entry as a literal (data URI)", e);
            }
        }

        public HttpRequest request() throws IOException {
            try {
                var builder = HttpRequest.newBuilder(this.file); // implicit scheme check
                return builder.header("Cache-Control", "no-cache").GET().build();
            } catch (IllegalArgumentException e) {
                throw new IOException("Failed to parse the entry as a request (http/https URI)", e);
            }
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Validatable {
        private final RemoteMeta meta;
        private final @Nullable String etag;
        private final @Nullable Instant lastModified;

        private Validatable(RemoteMeta meta, @Nullable String etag, @Nullable Instant lastModified) {
            this.meta = meta;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public static Validatable of(RemoteMeta meta) {
            return new Validatable(meta, null, null);
        }

        public static Validatable of(Validatable old, HttpHeaders headers) throws IOException {
            if (noStore(headers) != null) {
                return new Validatable(old.meta, null, null);
            }
            return new Validatable(old.meta, etag(headers), lastModified(headers));
        }

        public Validatable read(UnmodifiableConfig config) throws ParsingException {
            return new Validatable(this.meta.read(config), this.etag, this.lastModified);
        }

        public RemoteMeta meta() {
            return this.meta;
        }

        public Optional<HttpRequest> request() {
            var remote = this.meta.remote;
            if (remote == null) {
                return Optional.empty();
            }
            var builder = HttpRequest.newBuilder(remote);
            if (this.etag != null) {
                builder = builder.header("If-None-Match", this.etag);
            }
            var rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME;
            if (this.lastModified != null) {
                var str = rfc1123.format(this.lastModified.atOffset(ZoneOffset.UTC));
                builder = builder.header("If-Modified-Since", str);
            }
            return Optional.of(builder.header("Cache-Control", "no-cache").GET().build());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.meta, this.etag, this.lastModified);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Validatable that && this.meta.equals(that.meta)
                    && Objects.equals(this.etag, that.etag) && Objects.equals(this.lastModified, that.lastModified);
        }

        private static @Nullable String noStore(HttpHeaders httpHeaders) {
            return httpHeaders.allValues("Cache-Control").stream()
                    .flatMap(s -> Arrays.stream(s.split(","))).map(String::strip)
                    .filter("no-store"::equalsIgnoreCase).findFirst().orElse(null);
        }

        private static @Nullable String etag(HttpHeaders httpHeaders) {
            try {
                return Iterables.getOnlyElement(httpHeaders.allValues("ETag"), null);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static @Nullable Instant lastModified(HttpHeaders httpHeaders) {
            try {
                var str = Iterables.getOnlyElement(httpHeaders.allValues("Last-Modified"), "");
                return str.isEmpty() ? null : DateTimeFormatter.RFC_1123_DATE_TIME.parse(str, Instant::from);
            } catch (IllegalArgumentException | DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
