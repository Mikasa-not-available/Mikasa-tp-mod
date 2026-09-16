package com.haha.chatcommands.command;

import com.haha.chatcommands.db.Home;
import com.haha.chatcommands.db.HomeRepository;
import com.haha.chatcommands.db.SettingsRepository;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Homes: {@code /home set|list|del|<name>}.
 * Author: Mikasa
 */
public final class HomeCommand implements CommandHandler {
	private final HomeRepository homes;
	private final SettingsRepository settings;

	public HomeCommand(HomeRepository homes, SettingsRepository settings) {
		this.homes = homes;
		this.settings = settings;
	}

	@Override
	public String name() {
		return "home";
	}

	@Override
	public void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection,
			PermissionGate gate
	) {
		dispatcher.register(
				Commands.literal(name())
						.requires(source -> true)
						.then(Commands.literal("set")
								.then(Commands.argument("homeName", StringArgumentType.word())
										.executes(ctx -> set(
												ctx.getSource(),
												StringArgumentType.getString(ctx, "homeName"),
												gate))))
						.then(Commands.literal("list")
								.executes(ctx -> list(ctx.getSource(), gate)))
						.then(Commands.literal("del")
								.then(Commands.argument("homeName", StringArgumentType.word())
										.executes(ctx -> del(
												ctx.getSource(),
												StringArgumentType.getString(ctx, "homeName"),
												gate))))
						.then(Commands.argument("homeName", StringArgumentType.word())
								.executes(ctx -> teleport(
										ctx.getSource(),
										StringArgumentType.getString(ctx, "homeName"),
										gate)))
		);
	}

	private int set(CommandSourceStack source, String homeName, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		boolean updating = homes.exists(player.getUUID(), homeName);
		if (!updating) {
			int max = settings.maxHomes();
			int count = homes.countHomes(player.getUUID());
			if (count >= max) {
				source.sendFailure(Component.literal("Home limit reached (" + max + "). Delete one with /home del <name>"));
				return 0;
			}
		}

		Home home = new Home(
				player.getUUID(),
				homeName,
				player.level().dimension().identifier().toString(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYRot(),
				player.getXRot()
		);

		if (!homes.upsert(home)) {
			source.sendFailure(Component.literal("Failed to save home"));
			return 0;
		}

		source.sendSuccess(
				() -> Component.literal(updating
						? "Updated home '" + homeName + "'"
						: "Saved home '" + homeName + "'"),
				false
		);
		return Command.SINGLE_SUCCESS;
	}

	private int list(CommandSourceStack source, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		List<Home> list = homes.listByPlayer(player.getUUID());
		int max = settings.maxHomes();

		if (list.isEmpty()) {
			source.sendSuccess(() -> Component.literal("You have no homes (0/" + max + ")"), false);
			return Command.SINGLE_SUCCESS;
		}

		source.sendSuccess(() -> Component.literal("Your homes (" + list.size() + "/" + max + "):"), false);
		for (Home home : list) {
			String line = "- " + home.name()
					+ " @ " + format(home.x()) + " " + format(home.y()) + " " + format(home.z())
					+ " [" + home.world() + "]";
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int del(CommandSourceStack source, String homeName, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		if (!homes.delete(player.getUUID(), homeName)) {
			source.sendFailure(Component.literal("Home '" + homeName + "' not found"));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Deleted home '" + homeName + "'"), false);
		return Command.SINGLE_SUCCESS;
	}

	private int teleport(CommandSourceStack source, String homeName, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		Optional<Home> found = homes.find(player.getUUID(), homeName);
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("Home '" + homeName + "' not found"));
			return 0;
		}

		Home home = found.get();
		Identifier worldId = Identifier.parse(home.world());
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, worldId);
		ServerLevel level = source.getServer().getLevel(dimension);
		if (level == null) {
			source.sendFailure(Component.literal("World '" + home.world() + "' is not loaded"));
			return 0;
		}

		TeleportTransition transition = new TeleportTransition(
				level,
				new Vec3(home.x(), home.y(), home.z()),
				Vec3.ZERO,
				home.yaw(),
				home.pitch(),
				Set.of(),
				TeleportTransition.DO_NOTHING
		);
		player.teleport(transition);

		source.sendSuccess(() -> Component.literal("Teleported to home '" + homeName + "'"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static String format(double value) {
		return String.format("%.1f", value);
	}
}
