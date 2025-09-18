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
    private static final long VISIBILITY_DURATION = 10000L;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ToadSync.ID, "toast/sync");

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent component, long timeSinceLastVisible) {
        guiGraphics.blitSprite(TEXTURE, 0, 0, this.width(), this.height());
        guiGraphics.drawString(component.getMinecraft().font, this.title, 18, 7, 0xFFFFFF00, false);
        guiGraphics.drawString(component.getMinecraft().font, this.first, 18, 18, 0xFFFFFFFF, false);
        guiGraphics.drawString(component.getMinecraft().font, this.second, 18, 30, 0xFFFFFFFF, false);
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
