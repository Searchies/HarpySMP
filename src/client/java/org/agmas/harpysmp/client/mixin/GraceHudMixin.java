package org.agmas.harpysmp.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.agmas.harpysmp.components.HarpyLivesComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Instant;
import java.util.Date;

@Mixin(InGameHud.class)
public abstract class GraceHudMixin {
    @Shadow public abstract TextRenderer getTextRenderer();

    @Shadow @Nullable protected abstract PlayerEntity getCameraPlayer();

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    public void phantomHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HarpyLivesComponent harpyLivesComponent = HarpyLivesComponent.KEY.get(MinecraftClient.getInstance().player);
        if (harpyLivesComponent.lives == 0 && getCameraPlayer() != null && !getCameraPlayer().isSpectator() && !client.options.hudHidden) {
            int drawY = context.getScaledWindowHeight();

            Text line = Text.literal("Death-ban toggled off.");

            drawY -= getTextRenderer().getWrappedLinesHeight(line, 999999);
            context.drawTextWithShadow(getTextRenderer(), line, context.getScaledWindowWidth() - getTextRenderer().getWidth(line), drawY, Colors.GRAY);

        } else if (harpyLivesComponent.graceTime > Date.from(Instant.now()).getTime() && !client.options.hudHidden) {
            int drawY = context.getScaledWindowHeight();

            long seconds = (harpyLivesComponent.graceTime - Date.from(Instant.now()).getTime()) / 1000;
            long minutes = seconds / 60;
            long hours = seconds / 3600;
            long days = seconds / 86400;

            Text line;
            if (minutes <= 1) {
                line = Text.literal("Grace Time: " + seconds + " seconds");
            } else if (hours <= 1) {
                line = Text.literal("Grace Time: " + minutes + " minutes");
            } else if (days <= 1) {
                line = Text.literal("Grace Time: " + hours + " hours, " + (minutes - (60 * hours))  + " minutes");
            } else {
                line = Text.literal("Grace Time: " + days + " days, " + (hours - (24 * days))  + " hours");
            }

            drawY -= getTextRenderer().getWrappedLinesHeight(line, 999999);
            context.drawTextWithShadow(getTextRenderer(), line, context.getScaledWindowWidth() - getTextRenderer().getWidth(line), drawY, Colors.GREEN);
        }
    }
}
