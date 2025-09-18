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

package org.teacon.toadsync.common.sync;

import com.google.common.hash.HashCode;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.api.distmarker.Dist;
import org.teacon.toadsync.ToadSync;
import org.teacon.toadsync.spi.ToadSyncProvider;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.file.Path;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class OptionsSyncProvider implements ToadSyncProvider {
    @Override
    public String id() {
        return "options";
    }

    @Override
    public Path artifact() {
        return Path.of("options.override.txt");
    }

    @Override
    public boolean enabled(Dist dist) {
        return true;
    }

    @Override
    public void load(HashCode initHash, Path artifactLocation) throws IOException {
        ToadSync.OBJECTS.readOptions(initHash, artifactLocation);
    }

    @Override
    public void update(HashCode oldHash, HashCode newHash, Path artifactLocation) {
        ToadSync.OBJECTS.markOptionsUpdated(newHash);
    }
}
