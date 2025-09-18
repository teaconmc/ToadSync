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

import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ProxyWriter;
import org.apache.commons.io.output.StringBuilderWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.teacon.toadsync.ToadSync;

import java.io.IOException;
import java.io.Writer;

@Mixin(Options.class)
public abstract class OptionsMixin {
    private int modifiedOptionsCount;

    public abstract @Shadow void save();

    @ModifyArg(method = "save()V", at = @At(value = "INVOKE", target = "Ljava/io/PrintWriter;<init>(Ljava/io/Writer;)V"))
    public Writer onPrintWriterInit(Writer inner) {
        var builder = new StringBuilder(IOUtils.DEFAULT_BUFFER_SIZE);
        return new ProxyWriter(new StringBuilderWriter(builder)) {
            @Override
            public void close() throws IOException {
                ToadSync.OBJECTS.beforeOptionsSave(builder);
                var builderChars = new char[builder.length()];
                builder.getChars(0, builderChars.length, builderChars, 0);
                inner.write(builderChars, 0, builderChars.length);
                inner.close();
            }
        };
    }

    @ModifyArg(method = "load(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;dataFix(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"))
    public CompoundTag onDataFix(CompoundTag inner) {
        var toOverride = new CompoundTag();
        ToadSync.OBJECTS.afterOptionsLoad(inner, toOverride);
        this.modifiedOptionsCount = toOverride.size();
        return inner.merge(toOverride);
    }

    @Inject(method = "loadSelectedResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;)V", at = @At("RETURN"))
    public void afterLoad(CallbackInfo ci) {
        if (this.modifiedOptionsCount > 0) {
            this.save();
        }
    }
}
