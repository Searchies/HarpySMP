package org.agmas.harpysmp;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.agmas.harpysmp.components.DeathbanWorldComponent;
import org.agmas.harpysmp.components.HarpyLivesComponent;
import org.agmas.holo.state.HoloPlayerComponent;
import org.agmas.holo.util.FakestPlayer;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

public class HarpySMP implements ModInitializer {

    public static String MOD_ID = "harpysmp";
    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /harpysmp
            var root = CommandManager.literal(MOD_ID);

            // /harpysmp setLives <player> <lives>
            root.then(CommandManager.literal("setLives")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("lives", IntegerArgumentType.integer(0))
                .executes((commandContext)-> {
                    ServerPlayerEntity entity = EntityArgumentType.getPlayer(commandContext, "player");
                    int lives = IntegerArgumentType.getInteger(commandContext, "lives");
                    HarpyLivesComponent.KEY.get(entity).lives = lives;
                    for (ServerPlayerEntity player : entity.getServer().getPlayerManager().getPlayerList()) {
                        player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, entity));
                    }
                    return 1;
                }))));
            // /harpysmp setGracePeriod <player> <seconds>
            root.then(CommandManager.literal("setGracePeriod")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0))
                .executes((commandContext)->{
                    ServerPlayerEntity entity = EntityArgumentType.getPlayer(commandContext, "player");
                    int grace = IntegerArgumentType.getInteger(commandContext, "seconds");
                    HarpyLivesComponent.KEY.get(entity).graceTime = Date.from(Instant.now().plus(grace, ChronoUnit.SECONDS)).getTime();
                    return 1;
                }))));
            // /harpysmp setNickname <name>
            root.then(CommandManager.literal("setNickname")
                .then(CommandManager.argument("name", StringArgumentType.string())
                .executes((commandContext)->{
                    String name = StringArgumentType.getString(commandContext, "name");
                    if (name.length() > 16) name = name.substring(0,16);
                    HarpyLivesComponent.KEY.get(commandContext.getSource().getPlayer()).nickname = name;
                    HarpyLivesComponent.KEY.get(commandContext.getSource().getPlayer()).sync();
                    for (ServerPlayerEntity player : commandContext.getSource().getPlayer().getServer().getPlayerManager().getPlayerList()) {
                        player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, (ServerPlayerEntity) commandContext.getSource().getPlayer()));
                    }
                    return 1;
                })));
            // /harpysmp deathban <player>
            root.then(CommandManager.literal("deathban")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .executes((commandContext)-> {
                    DeathbanWorldComponent.KEY.get(commandContext.getSource().getServer().getOverworld()).enabled = !DeathbanWorldComponent.KEY.get(commandContext.getSource().getServer().getOverworld()).enabled;
                    return 1;
                }));
            // /harpysmp refund <player>
            root.then(CommandManager.literal("refund")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> {
                    ServerPlayerEntity killer = context.getSource().getPlayer();
                    ServerPlayerEntity victim = EntityArgumentType.getPlayer(context, "player");

                    if (killer == null) return 0; // Command run by console

                    if (killer.getUuid().equals(victim.getUuid())) {
                        context.getSource().sendError(Text.literal("You cannot refund a life to yourself!"));
                        return 0;
                    }

                    HarpyLivesComponent killerComponent = HarpyLivesComponent.KEY.get(killer);
                    HarpyLivesComponent victimComponent = HarpyLivesComponent.KEY.get(victim);

                    if (killerComponent.consumeRefundableLife(victim.getUuid())) {
                        // Give the life back
                        victimComponent.lives++;
                        victimComponent.sync();

                        context.getSource().sendFeedback(() -> Text.literal("Refunded 1 life to " + victim.getName().getString()), false);
                        victim.sendMessage(Text.literal("§a" + killer.getName().getString() + " refunded a life to you!"));
                        victim.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1, 1);

                        for (ServerPlayerEntity player : victim.getServer().getPlayerManager().getPlayerList()) {
                            player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, victim));
                        }

                        return 1;
                    } else {
                        context.getSource().sendError(Text.literal("You have no lives to refund to this player"));
                        return 0;
                    }
                })));
            // /harpysmp refund list
            root.then(CommandManager.literal("refund")
                .then(CommandManager.literal("list")
                .executes(context -> {
                    HarpyLivesComponent component = HarpyLivesComponent.KEY.get(context.getSource().getPlayer());

                    if (component.refundableLives.isEmpty()) {
                        context.getSource().sendError(Text.literal("You have no lives to refund to anyone"));
                    }

                    component.refundableLives.forEach((uuid, lives) -> {
                        String displayName = getNameFromUUID(uuid, context.getSource());
                        if (displayName != null) {
                            context.getSource().sendMessage(Text.literal(displayName + ": " + lives));
                        }
                    });

                    return 1;
                })));

            dispatcher.register(root);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register(((serverPlayerEntity, damageSource) -> {
            if (!(serverPlayerEntity instanceof ServerPlayerEntity)) return;
            if (serverPlayerEntity.getAttacker() instanceof PlayerEntity killer && HarpyLivesComponent.KEY.get(serverPlayerEntity).lives > 0) {
                if (HoloPlayerComponent.KEY.get(serverPlayerEntity).inHoloMode) return;
                if (serverPlayerEntity instanceof FakestPlayer) return;
                if (killer.getUuid() == serverPlayerEntity.getUuid()) return; // doesn't remove life if you kill urself somehow

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
                return;
            }
            return;
        }));
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

    public static String getNameFromUUID(UUID uuid, ServerCommandSource source) {
        try {
            return source.getServer().getUserCache().getByUuid(uuid).map(GameProfile::getName).get();
        } catch (Exception e) {
            return null;
        }
    }
}
