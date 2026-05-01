package org.agmas.harpysmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.agmas.harpysmp.command.Commands;
import org.agmas.harpysmp.components.HarpyLivesComponent;
import org.agmas.holo.state.HoloPlayerComponent;
import org.agmas.holo.util.FakestPlayer;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class HarpySMP implements ModInitializer {

    public static String MOD_ID = "harpysmp";
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(Commands::register);
        ServerLivingEntityEvents.AFTER_DEATH.register(HarpySMP::harpyDeath);
    }

    public static void harpyDeath(LivingEntity serverPlayerEntity, DamageSource damageSource) {
        if (!(serverPlayerEntity instanceof ServerPlayerEntity)) return;
        if (serverPlayerEntity.getAttacker() instanceof PlayerEntity killer && HarpyLivesComponent.KEY.get(serverPlayerEntity).lives > 0) {
            if (HoloPlayerComponent.KEY.get(serverPlayerEntity).inHoloMode) return;
            if (serverPlayerEntity instanceof FakestPlayer) return;
            if (killer.getUuid() == serverPlayerEntity.getUuid()) return;

            HarpyLivesComponent victimComponent = HarpyLivesComponent.KEY.get(serverPlayerEntity);

            if (victimComponent.graceTime > Date.from(Instant.now()).getTime()) {
                long time = victimComponent.graceTime - Date.from(Instant.now()).getTime();
                time = time / 1000;
                time = time / 60;
                serverPlayerEntity.getServer().getPlayerManager().broadcast(serverPlayerEntity.getDisplayName().copy().append(Text.literal( "'s death did not count due to their grace period of ").append(Text.literal(time+""))).append(" minutes."), false);
                return;
            }

            HarpyLivesComponent attackerComponent = HarpyLivesComponent.KEY.get(killer);
            if (attackerComponent.graceTime > Date.from(Instant.now()).getTime()) {

                long time = attackerComponent.graceTime - Date.from(Instant.now()).getTime();
                time = time / 1000;
                time = time / 60;
                serverPlayerEntity.getServer().getPlayerManager().broadcast(serverPlayerEntity.getDisplayName().copy().append(Text.literal( "'s death did not count due to their killer's grace period of ").append(Text.literal(time+""))).append(" minutes."), false);
                return;
            }
            victimComponent.lives--;
            victimComponent.graceTime = Date.from(Instant.now().plus(1, ChronoUnit.HOURS)).getTime();
            victimComponent.sync();

            if (killer.isPlayer()) {
                attackerComponent.addRefundableLife(serverPlayerEntity.getUuid());
            }

            if (victimComponent.lives == 0) {
                for (ServerPlayerEntity player : serverPlayerEntity.getServer().getPlayerManager().getPlayerList()) {
                    player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 1, 1);
                }
            } else {
                for (ServerPlayerEntity player : serverPlayerEntity.getServer().getPlayerManager().getPlayerList()) {
                    player.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1, 1);
                }
            }
            for (ServerPlayerEntity player : serverPlayerEntity.getServer().getPlayerManager().getPlayerList()) {
                player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, (ServerPlayerEntity) serverPlayerEntity));
            }
        }
    }

    public static Color colorFromLives(int lives) {
        return switch (lives) {
            case 1 -> Color.RED;
            case 2 -> Color.YELLOW;
            case 3 -> Color.GREEN;
            case 4 -> Color.GREEN.darker();
            default -> Color.GRAY;
        };
    }
}
