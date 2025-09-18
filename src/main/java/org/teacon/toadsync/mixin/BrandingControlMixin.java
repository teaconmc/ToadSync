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

import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.teacon.toadsync.ToadSync;

import java.util.List;

@Mixin(BrandingControl.class)
public abstract class BrandingControlMixin {
    private static @Shadow List<String> brandings;
    private static @Shadow List<String> brandingsNoMC;

    @Inject(method = "computeBranding()V", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;build()Lcom/google/common/collect/ImmutableList;"))
    private static void onComputeBranding(CallbackInfo ci, ImmutableList.Builder<String> brd) {
        ToadSync.OBJECTS.setBrandHook(() -> I18n.get("toad_sync.options.reload.hint"), () -> {
            brandings = null;
            brandingsNoMC = null;
        });
        ToadSync.OBJECTS.appendOptionsBrand(brd);
    }
}
