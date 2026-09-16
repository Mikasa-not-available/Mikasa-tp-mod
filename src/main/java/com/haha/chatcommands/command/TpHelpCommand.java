package com.haha.chatcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Author: Mikasa */
public final class TpHelpCommand implements CommandHandler {
	@Override
	public String name() {
		return "tphelp";
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

		// Only the requester sees this (broadcastToOps = false)
		send(source, "=== Mikasa-tp-mod commands ===");
		send(source, "/tphelp - this help");
		send(source, "/tp <player> - teleport to player");
		send(source, "/tpa <player> - request teleport");
		send(source, "/tpaccept - accept incoming TPA");
		send(source, "/tpdeny - deny incoming TPA");
		send(source, "/tpacancel - cancel your TPA / warmup");
		send(source, "/home set <name> - save home at your position");
		send(source, "/home <name> - teleport to home");
		send(source, "/home del <name> - delete home");
		send(source, "/home list - list your homes");
		send(source, "Config: config/Mikasa-tp-mod/ | DB: config/Mikasa-mods-general/database/ | Help: README.md");
		return Command.SINGLE_SUCCESS;
	}

	private static void send(CommandSourceStack source, String line) {
		source.sendSuccess(() -> Component.literal(line), false);
	}
}
