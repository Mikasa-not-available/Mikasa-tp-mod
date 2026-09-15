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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HomeSetCommand implements CommandHandler {
	private final HomeRepository homes;
	private final SettingsRepository settings;

	public HomeSetCommand(HomeRepository homes, SettingsRepository settings) {
		this.homes = homes;
		this.settings = settings;
	}

	@Override
	public String name() {
		return "homeset";
	}

	@Override
	public void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection,
			PermissionGate gate
	) {
		var node = Commands.literal(name())
				.requires(source -> true)
				.then(Commands.argument("homeName", StringArgumentType.word())
						.executes(ctx -> execute(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "homeName"),
								gate)));

		dispatcher.register(node);
	}

	private int execute(CommandSourceStack source, String homeName, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		boolean updating = homes.exists(player.getUUID(), homeName);
		if (!updating) {
			int max = settings.maxHomes();
			int count = homes.countHomes(player.getUUID());
			if (count >= max) {
				source.sendFailure(Component.literal("Home limit reached (" + max + "). Delete one with /homedel <name>"));
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
}
