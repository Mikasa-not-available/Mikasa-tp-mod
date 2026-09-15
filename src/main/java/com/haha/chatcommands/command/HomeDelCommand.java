package com.haha.chatcommands.command;

import com.haha.chatcommands.db.HomeRepository;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HomeDelCommand implements CommandHandler {
	private final HomeRepository homes;

	public HomeDelCommand(HomeRepository homes) {
		this.homes = homes;
	}

	@Override
	public String name() {
		return "homedel";
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
		if (!homes.delete(player.getUUID(), homeName)) {
			source.sendFailure(Component.literal("Home '" + homeName + "' not found"));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Deleted home '" + homeName + "'"), false);
		return Command.SINGLE_SUCCESS;
	}
}
