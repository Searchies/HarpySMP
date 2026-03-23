package org.agmas.harpysmp.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ColorPlayerListNameMixin {
    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    public void harpysmp$changeName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(((PlayerEntity)(Object)this).getDisplayName());
    }
}
