package com.haha.chatcommands.command;

import com.haha.chatcommands.tpa.TpaManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class TpaCommand implements CommandHandler {
	private final TpaManager tpa;

	public TpaCommand(TpaManager tpa) {
		this.tpa = tpa;
	}

	@Override
	public String name() {
		return "tpa";
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
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> execute(
										ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										gate)))
		);
	}

	private int execute(CommandSourceStack source, ServerPlayer target, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}
		ServerPlayer self = source.getPlayerOrException();
		long tick = source.getServer().getTickCount();
		return tpa.request(self, target, tick) ? Command.SINGLE_SUCCESS : 0;
	}
}
