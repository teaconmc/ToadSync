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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.teacon.toadsync.ToadSync;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public record SyncToast(Component title, Component first, Component second) implements Toast {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 44;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final long VISIBILITY_DURATION = 10000L;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ToadSync.ID, "toast/sync");

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent component, long timeSinceLastVisible) {
        guiGraphics.blitSprite(TEXTURE, 0, 0, this.width(), this.height());
        guiGraphics.drawString(component.getMinecraft().font, this.title, 18, 7, YELLOW, false);
        guiGraphics.drawString(component.getMinecraft().font, this.first, 18, 18, WHITE, false);
        guiGraphics.drawString(component.getMinecraft().font, this.second, 18, 30, WHITE, false);
        var displayTime = VISIBILITY_DURATION * component.getNotificationDisplayTimeMultiplier();
        return timeSinceLastVisible < displayTime ? Visibility.SHOW : Visibility.HIDE;
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
