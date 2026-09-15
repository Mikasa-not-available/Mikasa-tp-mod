package com.haha.chatcommands.command;

import com.haha.chatcommands.db.Home;
import com.haha.chatcommands.db.HomeRepository;
import com.haha.chatcommands.db.SettingsRepository;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class HomesCommand implements CommandHandler {
	private final HomeRepository homes;
	private final SettingsRepository settings;

	public HomesCommand(HomeRepository homes, SettingsRepository settings) {
		this.homes = homes;
		this.settings = settings;
	}

	@Override
	public String name() {
		return "homeList";
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
						.executes(ctx -> execute(ctx.getSource(), gate))
		);
	}

	private int execute(CommandSourceStack source, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		List<Home> list = homes.listByPlayer(player.getUUID());
		int max = settings.maxHomes();

		// false = only the command source sees this (not other players / not broadcast)
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

	private static String format(double value) {
		return String.format("%.1f", value);
	}
}
