package com.haha.chatcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public interface CommandHandler {
	/** Stable command id used in DB / roles.json (e.g. "tp"). */
	String name();

	void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection,
			PermissionGate gate
	);
}
