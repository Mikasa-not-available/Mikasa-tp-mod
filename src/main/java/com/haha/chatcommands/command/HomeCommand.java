package com.haha.chatcommands.command;

import com.haha.chatcommands.db.Home;
import com.haha.chatcommands.db.HomeRepository;
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

import java.util.Optional;
import java.util.Set;

public final class HomeCommand implements CommandHandler {
	private final HomeRepository homes;

	public HomeCommand(HomeRepository homes) {
		this.homes = homes;
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
						.then(Commands.argument("homeName", StringArgumentType.word())
								.executes(ctx -> execute(
										ctx.getSource(),
										StringArgumentType.getString(ctx, "homeName"),
										gate)))
		);
	}

	private int execute(CommandSourceStack source, String homeName, PermissionGate gate) throws CommandSyntaxException {
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
}
