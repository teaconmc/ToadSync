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

package org.teacon.toadsync.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.teacon.toadsync.ToadSync;

import java.util.function.Consumer;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    public @Shadow Options options;

    public abstract @Shadow void updateTitle();

    @ModifyArg(method = "updateTitle()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private String onSetTitle(String inner) {
        var builder = new StringBuilder(inner);
        ToadSync.OBJECTS.onTitleUpdate(builder, this.options.languageCode, false);
        return builder.toString();
    }

    @ModifyArg(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/VirtualScreen;newWindow(Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)Lcom/mojang/blaze3d/platform/Window;"))
    private String onNewWindow(String inner) {
        var builder = new StringBuilder(inner);
        ToadSync.OBJECTS.onTitleUpdate(builder, this.options.languageCode, true);
        return builder.toString();
    }

    @ModifyArg(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/LanguageManager;<init>(Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private Consumer<ClientLanguage> onNewLanguageManager(Consumer<ClientLanguage> inner) {
        return lang -> {
            this.updateTitle();
            inner.accept(lang);
        };
    }
}
