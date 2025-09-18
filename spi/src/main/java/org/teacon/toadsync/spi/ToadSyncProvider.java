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

package org.teacon.toadsync.spi;

import com.google.common.hash.HashCode;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.api.distmarker.Dist;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Service Provider Interface (SPI) for ToadSync.
 * <p>
 * ToadSync is a NeoForge mod for loading and synchronizing external artifacts such as data packs or
 * resource packs for game clients and servers. Implementations of the interface are discovered via
 * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html">
 * Java SPI</a>.
 * <p>
 * Implementations are identified by a unique {@link #id()}. ToadSync reads remote TOML metadata,
 * locating a {@code sync.{{id}}} section that provides the download URL and SHA-256 hash for an
 * artifact. The file is saved to the location specified by {@link #artifact()}, and then passed to
 * either {@link #load} or {@link #update} depending on whether it is a first-time load or an update
 * to a previously loaded version.
 *
 * @author TeaConMC
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface ToadSyncProvider {

    /**
     * Returns the unique identifier for this provider. It must start with a lowercase ASCII letter
     * (U+0061–U+007A), and may only contain lowercase letters (U+0061–U+007A), digits (U+0030–U+0039),
     * underscores (U+005F), and hyphens (U+002D). This ID matches the key {@code sync.{{id}}} in the
     * remote TOML metadata.
     *
     * @return the unique provider ID
     */
    String id();

    /**
     * Returns the file name where the artifact will be downloaded. This must be a plain file name, not
     * a path with directories. For example, {@code "foo.txt"} is valid, but {@code "foo/bar.txt"} is
     * invalid.
     *
     * @return the file name of the artifact
     */
    Path artifact();

    /**
     * Indicates whether this provider is active on the given physical side. If this returns {@code false}
     * for a given side (e.g., {@link Dist#CLIENT} or {@link Dist#DEDICATED_SERVER}), the provider will
     * be ignored entirely for downloading, loading, and updating artifacts.
     *
     * @param  dist the current physical side
     * @return true if this provider should be active on the given side
     */
    boolean enabled(Dist dist);

    /**
     * Called to initially load the artifact when it is loaded for the first time. The provided hash is
     * the verified SHA-256 checksum of the file. The {@code artifactLocation} parameter specifies the
     * file path of the downloaded artifact, which matches the file name returned by {@link #artifact()}.
     * <p>
     * This method is guaranteed to be called at most once, and always before any call to {@link #update}.
     *
     * @param  initHash         the SHA-256 hash of the new artifact
     * @param  artifactLocation the path to the downloaded artifact file (matches {@link #artifact()})
     * @throws IOException      if loading fails
     */
    void load(HashCode initHash, Path artifactLocation) throws IOException;

    /**
     * Called to update an existing artifact to a newer one. The old SHA-256 hashes and new SHA-256
     * hashes are provided as hints for the implementation's reference. The {@code artifactLocation}
     * parameter specifies the path to the new artifact file, which matches the file name returned by
     * {@link #artifact()}.
     * <p>
     * This method is only called when the artifact content has changed (i.e., the SHA-256 hash has
     * changed), and only after {@link #load} has been successfully called once.
     *
     * @param  oldHash          the SHA-256 hash of the old artifact
     * @param  newHash          the SHA-256 hash of the new artifact
     * @param  artifactLocation the path to the new artifact file (matches {@link #artifact()})
     * @throws IOException      if the update process fails
     */
    void update(HashCode oldHash, HashCode newHash, Path artifactLocation) throws IOException;
}
