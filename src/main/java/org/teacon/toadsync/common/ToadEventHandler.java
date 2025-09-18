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

package org.teacon.toadsync.common;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.toadsync.ToadSync;
import org.teacon.toadsync.common.remote.MetaEntryRefresher;
import org.teacon.toadsync.common.remote.MetaValidatableRefresher;
import org.teacon.toadsync.common.sync.OptionsSyncProvider;
import org.teacon.toadsync.spi.ToadSyncProvider;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.ServiceLoader;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ToadEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void bootstrap(Dist dist) {
        var versionString = FMLLoader.getLoadingModList().getModFileById(ToadSync.ID).versionString();
        var distString = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, dist.toString());
        LOGGER.info("ToadSync Version: {} ({} side)", versionString, distString);
        // get providers (options always at the first)
        var suppliers = ServiceLoader.load(ToadSyncProvider.class).stream().collect(Collectors.toList());
        var optionsIndex = Iterables.indexOf(suppliers, p -> p != null && p.type() == OptionsSyncProvider.class);
        Collections.rotate(suppliers.subList(0, optionsIndex + 1), 1);
        // load and check providers
        var artifacts = HashBiMap.<String, Path>create();
        var providers = ImmutableMap.<String, ToadSyncProvider>builderWithExpectedSize(suppliers.size());
        var refreshers = new HashMap<String, MetaEntryRefresher>(suppliers.size());
        var client = HttpClient.newBuilder()
                .executor(Util.nonCriticalIoPool())
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofMillis(300_000L)).build();
        var pattern = Pattern.compile("[a-z][a-z0-9_-]*");
        for (var supplier : suppliers) {
            var provider = supplier.get();
            // check id and ensure uniqueness
            var id = provider.id();
            Preconditions.checkArgument(pattern.matcher(id).matches(), "id format invalid: " + id);
            var cls = provider.getClass().getName();
            if (artifacts.containsKey(id)) {
                LOGGER.info("Ignore duplicate {} provider ({}) ...", id, cls);
                continue;
            }
            // check file path
            var file = provider.artifact();
            Preconditions.checkArgument(!artifacts.containsValue(file), "artifact already exists: " + file);
            Preconditions.checkArgument(file.getFileName().equals(file), "artifact should be a file name: " + file);
            // register and load file
            if (!provider.enabled(dist)) {
                LOGGER.info("Skip {} provider ({}) since it is not enabled on dist {}", id, cls, distString);
                continue;
            }
            artifacts.put(id, file);
            providers.put(id, provider);
            var hash = (HashCode) null;
            try {
                var location = ToadSync.CONFIG_DIR.resolve(file);
                if (Files.exists(location)) {
                    hash = MoreFiles.asByteSource(location).hash(Hashing.sha256());
                    LOGGER.info("Start loading {} ({}) of {} provider ({}) ...", file, hash, id, cls);
                    provider.load(hash, location);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load {} of {} provider ({})", file, id, cls, e);
            }
            refreshers.put(id, new MetaEntryRefresher(ToadSync.CONFIG_DIR, client, hash, provider));
        }
        // start remote address sync
        ToadSync.OBJECTS.submitRefresher(new MetaValidatableRefresher(client, refreshers));
    }

    public static void init(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, ToadSync.OBJECTS::handleServerTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, ToadSync.OBJECTS::handleClientTick);
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, ignored -> ToadSync.OBJECTS.close());
        bus.addListener(EventPriority.LOWEST, AddPackFindersEvent.class, ToadSync.OBJECTS::handleAddPackFinders);
    }
}
