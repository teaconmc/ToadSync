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

package org.teacon.toadsync;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.teacon.toadsync.common.ToadEventHandler;
import org.teacon.toadsync.common.ToadObjects;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.file.Path;

@Mod(ToadSync.ID)
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ToadSync {
    public static final String ID = "toad_sync";
    public static final ToadObjects OBJECTS = new ToadObjects();
    public static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("toad-sync");
    public static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("toad-sync-common.toml");

    public ToadSync(IEventBus bus) {
        ToadEventHandler.init(bus);
    }
}
