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

package org.teacon.toadsync.client;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.teacon.toadsync.ToadSync;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ToadClientEventHandler {
    private static final SystemToast.SystemToastId RESOURCE_RELOAD = new SystemToast.SystemToastId(10000L);

    public static void bootstrap() {
        ToadSync.OBJECTS.setUpdateTitleHook(() -> Minecraft.getInstance().updateTitle());
        ToadSync.OBJECTS.setAssetsToastHook(() -> {
            var loaded = Minecraft.getInstance().player != null;
            if (loaded) {
                var toasts = Minecraft.getInstance().getToasts();
                var title = Component.translatable("toad_sync.assets.reload.hint.title");
                var message = Component.translatable("toad_sync.assets.reload.hint.message");
                return Optional.of(() -> toasts.addToast(new SystemToast(RESOURCE_RELOAD, title, message)));
            }
            return Optional.empty();
        });
    }
}
