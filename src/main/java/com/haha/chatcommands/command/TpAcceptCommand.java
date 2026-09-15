package com.haha.chatcommands.command;

import com.haha.chatcommands.tpa.TpaManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class TpAcceptCommand implements CommandHandler {
	private final TpaManager tpa;

	public TpAcceptCommand(TpaManager tpa) {
		this.tpa = tpa;
	}

	@Override
	public String name() {
		return "tpaccept";
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
		return tpa.accept(source.getPlayerOrException(), source.getServer().getTickCount())
				? Command.SINGLE_SUCCESS
				: 0;
	}
}
