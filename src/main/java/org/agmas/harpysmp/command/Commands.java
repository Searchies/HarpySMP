package org.agmas.harpysmp.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.agmas.harpysmp.components.DeathbanWorldComponent;
import org.agmas.harpysmp.components.HarpyLivesComponent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.agmas.harpysmp.HarpySMP.MOD_ID;

public class Commands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess
            , CommandManager.RegistrationEnvironment environment) {
        var root = CommandManager.literal(MOD_ID);

        // /harpysmp setLives <player> <lives>
        root.then(CommandManager.literal("setLives")
            .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
            .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.argument("lives", IntegerArgumentType.integer(0))
            .executes(Commands::setLives))));

        // /harpysmp setGracePeriod <player> <seconds>
        root.then(CommandManager.literal("setGracePeriod")
            .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
            .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0))
            .executes(Commands::setGracePeriod))));

        // /harpysmp setNickname <name>
        root.then(CommandManager.literal("setNickname")
            .then(CommandManager.argument("name", StringArgumentType.string())
            .executes(Commands::setNickname)));

        // /harpysmp deathban <player>
        root.then(CommandManager.literal("deathban")
            .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
            .executes(Commands::deathban));

        // /harpysmp refund <player>
        root.then(CommandManager.literal("refund")
            .then(CommandManager.argument("victim", EntityArgumentType.player())
            .executes(Commands::refundCommand)));

        // /harpysmp refund list
        root.then(CommandManager.literal("refund")
            .then(CommandManager.literal("list")
            .executes(Commands::refundListCommand)));

        // harpysmp forceRefund <player> <player>
        root.then(CommandManager.literal("forceRefund")
            .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
            .then(CommandManager.argument("killer", EntityArgumentType.player())
            .then(CommandManager.argument("victim", EntityArgumentType.player())
            .executes(Commands::adminRefundCommand))));

        // /harpysmp refund list <player>
        root.then(CommandManager.literal("refund")
            .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
            .then(CommandManager.literal("list")
            .then(CommandManager.argument("victim", EntityArgumentType.player())
            .executes(Commands::adminRefundListCommand))));

        dispatcher.register(root);
    }

    public static int setLives(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity entity = EntityArgumentType.getPlayer(context, "player");
        HarpyLivesComponent.KEY.get(entity).lives = IntegerArgumentType.getInteger(context, "lives");

        for (ServerPlayerEntity player : entity.getServer().getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, entity));
        }

        return 1;
    }

    public static int setGracePeriod(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity entity = EntityArgumentType.getPlayer(context, "player");
        int grace = IntegerArgumentType.getInteger(context, "seconds");

        HarpyLivesComponent.KEY.get(entity).graceTime = Date.from(Instant.now().plus(grace, ChronoUnit.SECONDS)).getTime();
        HarpyLivesComponent.KEY.get(entity).sync();
        return 1;
    }

    public static int setNickname(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (name.length() > 16) name = name.substring(0,16);

        HarpyLivesComponent.KEY.get(context.getSource().getPlayer()).nickname = name;
        HarpyLivesComponent.KEY.get(context.getSource().getPlayer()).sync();

        for (ServerPlayerEntity player : context.getSource().getPlayer().getServer().getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, context.getSource().getPlayer()));
        }

        return 1;
    }

    public static int deathban(CommandContext<ServerCommandSource> context) {
        DeathbanWorldComponent.KEY.get(context.getSource().getServer().getOverworld()).enabled = !DeathbanWorldComponent.KEY.get(context.getSource().getServer().getOverworld()).enabled;

        return 1;
    }

    public static int refund(CommandContext<ServerCommandSource> context, ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (killer == null) return 0;

        if (killer.getUuid().equals(victim.getUuid())) {
            context.getSource().sendError(Text.literal("You cannot refund a life to yourself!"));
            return 0;
        }

        HarpyLivesComponent killerComponent = HarpyLivesComponent.KEY.get(killer);
        HarpyLivesComponent victimComponent = HarpyLivesComponent.KEY.get(victim);

        if (killerComponent.consumeRefundableLife(victim.getUuid())) {
            if (victimComponent.lives == 0) {
                context.getSource().sendError(Text.literal("Player cannot be refunded life."));
                return 0;
            }

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
    }

    public static int refundList(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        HarpyLivesComponent component = HarpyLivesComponent.KEY.get(player);

        if (component.refundableLives.isEmpty()) {
            context.getSource().sendError(Text.literal("You have no lives to refund to anyone"));
        }

        context.getSource().sendMessage(Text.literal(player.getStyledDisplayName().getString() + " refundable lives:" +
                " "));
        component.refundableLives.forEach((uuid, lives) -> {
            String displayName = getNameFromUUID(uuid, context.getSource());
            if (displayName != null) {
                context.getSource().sendMessage(Text.literal("- " + displayName + ": " + lives));
            }
        });

        return 1;
    }

    public static int refundCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity killer = context.getSource().getPlayer();
        ServerPlayerEntity victim = EntityArgumentType.getPlayer(context, "victim");
        return refund(context, killer, victim);
    }


    public static int refundListCommand(CommandContext<ServerCommandSource> context) {
        return refundList(context, context.getSource().getPlayer());
    }


    public static int adminRefundCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity killer = EntityArgumentType.getPlayer(context, "killer");
        ServerPlayerEntity victim = EntityArgumentType.getPlayer(context, "victim");
        return refund(context, killer, victim);
    }

    public static int adminRefundListCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "victim");
        return refundList(context, player);
    }


    public static String getNameFromUUID(UUID uuid, ServerCommandSource source) {
        try {
            return source.getServer().getUserCache().getByUuid(uuid).map(GameProfile::getName).orElseGet(() -> {return null;});
        } catch (Exception e) {
            return null;
        }
    }
}
