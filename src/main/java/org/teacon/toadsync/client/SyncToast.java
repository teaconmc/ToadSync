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

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.teacon.toadsync.ToadSync;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncToast implements Toast {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 44;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(ToadSync.ID, "toast/sync");

    private final Component title;
    private final Component first;
    private final Component second;
    private Visibility wantedVisibility;

    public SyncToast(Component title, Component first, Component second) {
        this.title = title;
        this.first = first;
        this.second = second;
        this.wantedVisibility = Visibility.HIDE;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.wantedVisibility = ToadSync.OBJECTS.isAssetsToastVisible() ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long l) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, WIDTH, HEIGHT);
        graphics.text(font, this.title, 18, 7, YELLOW, false);
        graphics.text(font, this.first, 18, 18, WHITE, false);
        graphics.text(font, this.second, 18, 30, WHITE, false);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }
}
