package org.agmas.harpysmp.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.agmas.harpysmp.HarpySMP;
import org.agmas.harpysmp.components.HarpyLivesComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerDisplayNameMixin {
    @Shadow public abstract GameProfile getGameProfile();

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    public void harpysmp$changeDisplayName(CallbackInfoReturnable<Text> cir) {
        Text name = cir.getReturnValue();
        if (HarpyLivesComponent.KEY.get(((PlayerEntity)(Object)this)).nickname != null) {
            name = Text.literal(HarpyLivesComponent.KEY.get(((PlayerEntity)(Object)this)).nickname);
            name = name.copy().fillStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(getGameProfile().getName()))));
        }

        cir.setReturnValue(name.copy().withColor(HarpySMP.colorFromLives(HarpyLivesComponent.KEY.get((PlayerEntity)(Object)this).lives).getRGB()));
        cir.cancel();
    }
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    public void harpysmp$changeName(CallbackInfoReturnable<Text> cir) {

        Text name = cir.getReturnValue();
        if (HarpyLivesComponent.KEY.get(((PlayerEntity)(Object)this)).nickname != null) {
            name = Text.of(HarpyLivesComponent.KEY.get(((PlayerEntity)(Object)this)).nickname);
            name = name.copy().fillStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,  Text.of(getGameProfile().getName()))));
        }

        cir.setReturnValue(name.copy().withColor(HarpySMP.colorFromLives(HarpyLivesComponent.KEY.get((PlayerEntity)(Object)this).lives).getRGB()));
        cir.cancel();
    }
}
