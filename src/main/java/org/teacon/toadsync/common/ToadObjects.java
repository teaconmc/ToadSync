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

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.*;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.ChatFormatting;
import net.minecraft.DetectedVersion;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.toadsync.ToadSync;
import org.teacon.toadsync.common.remote.MetaValidatableRefresher;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ToadObjects implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String PACK_PREFIX = ToadSync.ID + "/";
    private static final String TOAD_SYNC_IGNORE = "toadSyncIgnore";

    private static final ConfigFormat<CommentedConfig> TOML_FORMAT = new TomlFormat();
    private static final PackSource PACK_SOURCE = PackSource.create(ToadObjects::decorateDescription, true);
    private static final PackSelectionConfig PACK_SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);

    private @Nullable MetaValidatableRefresher refresher;
    private final TitleOverride titleOverride = new TitleOverride();
    private final OptionsOverride optionsOverride = new OptionsOverride();
    private final GamePacksOverride gamePacksOverride = new GamePacksOverride();

    public void submitRefresher(MetaValidatableRefresher refresher) {
        if (this.refresher != null) {
            throw new IllegalStateException("Refresher added twice");
        }
        this.refresher = refresher;
        refresher.submit(FileConfig
                .builder(ToadSync.CONFIG, TOML_FORMAT)
                .writingMode(WritingMode.REPLACE_ATOMIC)
                .onFileNotFound(FileNotFoundAction.READ_NOTHING)
                .preserveInsertionOrder().async().build());
    }

    public void readTitle(Path path) throws IOException {
        try (var conf = FileConfig.builder(path, TOML_FORMAT).onFileNotFound(FileNotFoundAction.READ_NOTHING).build()) {
            conf.load();
            this.titleOverride.config = conf.unmodifiable();
            if (this.titleOverride.windowCreated) {
                this.titleOverride.updateHook.run();
            }
        } catch (ParsingException e) {
            throw new IOException("Failed to parse title on loading", e);
        }
    }

    public void setUpdateTitleHook(Runnable hook) {
        this.titleOverride.updateHook = hook;
    }

    public void onTitleUpdate(StringBuilder title, String langCode, boolean windowCreation) {
        if (windowCreation && this.titleOverride.windowCreated) {
            throw new IllegalStateException("Game window created twice");
        }
        this.titleOverride.windowCreated = true;
        var newTitle = this.titleOverride.config.get(List.of("lang", langCode, "title"));
        if (!(newTitle instanceof String s) || s.isBlank()) {
            newTitle = this.titleOverride.config.get(List.of("title"));
        }
        if (newTitle instanceof String s && !s.isBlank()) {
            title.setLength(0);
            title.append(newTitle);
        }
    }

    public void readOptions(HashCode hash, Path path) throws IOException {
        this.markOptionsUpdated(hash);
        try (var reader = new LineNumberReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                try {
                    var split = line.split(":", 2);
                    if (split[0].equals(TOAD_SYNC_IGNORE)) {
                        throw new IllegalArgumentException("Invalid key: " + TOAD_SYNC_IGNORE);
                    }
                    this.optionsOverride.optionsData.putString(split[0], split[1]);
                } catch (Exception e) {
                    var lineNumber = reader.getLineNumber();
                    throw new IOException("Failed to parse options (line " + lineNumber + ") on loading", e);
                }
            }
        }
    }

    public void markOptionsUpdated(HashCode hash) {
        if (!this.optionsOverride.overridden) {
            this.optionsOverride.loadedHash = hash;
        }
        this.optionsOverride.latestHash = hash;
        this.optionsOverride.brandResetHook.run();
    }

    public void setBrandHook(Supplier<String> optionsHintHook, Runnable resetHook) {
        this.optionsOverride.optionsHintHook = optionsHintHook;
        this.optionsOverride.brandResetHook = resetHook;
    }

    public void appendOptionsBrand(ImmutableList.Builder<String> brands) {
        if (!Objects.equals(this.optionsOverride.latestHash, this.optionsOverride.loadedHash)) {
            brands.add(this.optionsOverride.optionsHintHook.get());
        }
    }

    public void afterOptionsLoad(CompoundTag options, CompoundTag toOverride) throws JsonParseException {
        LOGGER.debug("Loading vanilla options.txt ({} entries)", options.size());
        var syncIgnoreSet = new LinkedHashSet<String>();
        if (options.contains(TOAD_SYNC_IGNORE, Tag.TAG_STRING)) {
            var syncIgnore = GsonHelper.parseArray(options.getString(TOAD_SYNC_IGNORE));
            for (var key : syncIgnore) {
                if (!key.isJsonPrimitive()) {
                    throw new JsonParseException("Invalid element of value: " + TOAD_SYNC_IGNORE);
                }
                syncIgnoreSet.add(key.getAsString());
            }
        }
        for (var key : this.optionsOverride.optionsData.getAllKeys()) {
            var oldTag = options.get(key);
            var newTag = StringTag.valueOf(this.optionsOverride.optionsData.getString(key));
            if (!newTag.equals(oldTag) && !syncIgnoreSet.contains(key)) {
                toOverride.put(key, newTag);
            }
        }
        this.optionsOverride.overridden = true;
    }

    public void beforeOptionsSave(StringBuilder options) throws IOException {
        var syncIgnore = new JsonArray();
        try (var reader = new LineNumberReader(new CharSequenceReader(options))) {
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                try {
                    var split = line.split(":", 2);
                    var overridable = this.optionsOverride.optionsData.contains(split[0], Tag.TAG_STRING);
                    var ignore = overridable && !this.optionsOverride.optionsData.getString(split[0]).equals(split[1]);
                    if (ignore) {
                        syncIgnore.add(new JsonPrimitive(split[0]));
                    }
                } catch (Exception e) {
                    var lineNumber = reader.getLineNumber();
                    throw new IOException("Failed to parse options (line " + lineNumber + ") on saving", e);
                }
            }
            options.append(TOAD_SYNC_IGNORE + ":");
            options.append(GsonHelper.toStableString(syncIgnore));
            options.append(System.lineSeparator());
            LOGGER.debug("Saving vanilla options.txt ({} entries)", reader.getLineNumber());
        }
    }

    public void readAssets(Path path) throws IOException {
        var id = PACK_PREFIX + path.getFileName();
        var info = new PackLocationInfo(id, Component.literal("ToadSync"), PACK_SOURCE, Optional.empty());
        var supplier = new FilePackResources.FileResourcesSupplier(path);
        var version = DetectedVersion.BUILT_IN.getPackVersion(PackType.CLIENT_RESOURCES);
        var meta = Pack.readPackMetadata(info, supplier, version);
        if (meta == null) {
            var oldPack = this.gamePacksOverride.packs.remove(PackType.CLIENT_RESOURCES);
            if (oldPack != null) {
                this.gamePacksOverride.needToReload.add(PackType.CLIENT_RESOURCES);
            }
            throw new IOException("Invalid pack metadata of assets in " + path);
        }
        var newPack = new Pack(info, supplier, meta, PACK_SELECTION);
        this.gamePacksOverride.needToReload.add(PackType.CLIENT_RESOURCES);
        this.gamePacksOverride.packs.put(PackType.CLIENT_RESOURCES, newPack);
    }

    public void setAssetsToastHook(Supplier<Optional<Runnable>> hook) {
        this.gamePacksOverride.assetsToastHook = hook;
    }

    public void readData(Path path) throws IOException {
        var id = PACK_PREFIX + path.getFileName();
        var info = new PackLocationInfo(id, Component.literal("ToadSync"), PACK_SOURCE, Optional.empty());
        var supplier = new FilePackResources.FileResourcesSupplier(path);
        var version = DetectedVersion.BUILT_IN.getPackVersion(PackType.SERVER_DATA);
        var meta = Pack.readPackMetadata(info, supplier, version);
        if (meta == null) {
            var oldPack = this.gamePacksOverride.packs.remove(PackType.SERVER_DATA);
            if (oldPack != null) {
                this.gamePacksOverride.needToReload.add(PackType.SERVER_DATA);
            }
            throw new IOException("Invalid pack metadata of data in " + path);
        }
        var newPack = new Pack(info, supplier, meta, PACK_SELECTION);
        this.gamePacksOverride.needToReload.add(PackType.SERVER_DATA);
        this.gamePacksOverride.packs.put(PackType.SERVER_DATA, newPack);
    }

    public void handleServerTick(ServerTickEvent.Pre event) {
        Objects.requireNonNull(event);
        var needReload = this.gamePacksOverride.needToReload.remove(PackType.SERVER_DATA);
        if (needReload) {
            var server = event.getServer();
            server.getPackRepository().reload();
            var ids = calculateSelectedPacks(server);
            var css = server.createCommandSourceStack();
            server.reloadResources(ids).whenComplete((v, t) -> {
                if (t != null) {
                    LOGGER.warn("Failed to reload the pack by data provider", t);
                    // noinspection DataFlowIssue
                    css.sendFailure(Component.translatableWithFallback("toad_sync.data.reload.hint.failed",
                            Language.getInstance().getOrDefault("toad_sync.data.reload.hint.failed", null)));
                } else {
                    // noinspection DataFlowIssue
                    css.sendSuccess(() -> Component.translatableWithFallback("toad_sync.data.reload.hint.success",
                            Language.getInstance().getOrDefault("toad_sync.data.reload.hint.success", null)), true);
                }
            });
        }
    }

    public void handleClientTick(ClientTickEvent.Pre event) {
        Objects.requireNonNull(event);
        var hook = this.gamePacksOverride.assetsToastHook.get();
        if (hook.isPresent()) {
            var needReload = this.gamePacksOverride.needToReload.remove(PackType.CLIENT_RESOURCES);
            if (needReload) {
                hook.get().run();
            }
        }
    }

    public void handleAddPackFinders(AddPackFindersEvent event) {
        event.addRepositorySource(consumer -> {
            var pack = this.gamePacksOverride.packs.get(event.getPackType());
            this.gamePacksOverride.needToReload.remove(event.getPackType());
            Optional.ofNullable(pack).ifPresent(consumer);
        });
    }

    private static ArrayList<String> calculateSelectedPacks(MinecraftServer server) {
        var repository = server.getPackRepository();
        var selected = repository.getSelectedPacks();
        var ids = new ArrayList<String>(selected.size() + 1);
        for (var pack : selected) {
            ids.add(pack.getId());
        }
        var available = repository.getAvailablePacks();
        for (var pack : available) {
            var id = pack.getId();
            if (id.startsWith(PACK_PREFIX) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Component decorateDescription(Component description) {
        return Component.translatable("pack.nameAndSource", description, "ToadSync").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void close() {
        if (this.refresher != null) {
            this.refresher.close();
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private static final class TitleOverride {
        private boolean windowCreated = false;
        private Runnable updateHook = Runnables.doNothing();
        private UnmodifiableConfig config = Config.inMemory().unmodifiable();
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private static final class OptionsOverride {
        private boolean overridden = false;
        private @Nullable HashCode loadedHash;
        private @Nullable HashCode latestHash;
        public Supplier<String> optionsHintHook = () -> "";
        private Runnable brandResetHook = Runnables.doNothing();
        private final CompoundTag optionsData = new CompoundTag();
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private static class GamePacksOverride {
        private Supplier<Optional<Runnable>> assetsToastHook = Optional::empty;
        private final Map<PackType, Pack> packs = new EnumMap<>(PackType.class);
        private final Set<PackType> needToReload = EnumSet.noneOf(PackType.class);
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private static class TomlFormat implements ConfigFormat<CommentedConfig> {
        @Override
        public ConfigWriter createWriter() {
            var writer = new TomlWriter();
            writer.setIndent(IndentStyle.NONE);
            return writer;
        }

        @Override
        public ConfigParser<CommentedConfig> createParser() {
            return new TomlParser();
        }

        @Override
        public CommentedConfig createConfig(Supplier<Map<String, Object>> mapCreator) {
            return CommentedConfig.of(mapCreator, this);
        }

        @Override
        public boolean supportsComments() {
            return true;
        }

        @Override
        public boolean supportsType(@Nullable Class<?> type) {
            return type != null && (ConfigFormat.super.supportsType(type) || Temporal.class.isAssignableFrom(type));
        }
    }
}
