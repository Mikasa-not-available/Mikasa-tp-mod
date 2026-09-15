package com.haha.chatcommands.command;

import com.haha.chatcommands.db.PermissionRepository;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;

public final class CommandRegistry {
	private final PermissionGate gate;
	private final List<CommandHandler> handlers = new ArrayList<>();

	public CommandRegistry(PermissionRepository permissions) {
		this.gate = new PermissionGate(permissions);
	}

	public void register(CommandHandler handler) {
		handlers.add(handler);
	}

	public void registerAll(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection
	) {
		for (CommandHandler handler : handlers) {
			handler.register(dispatcher, buildContext, selection, gate);
		}
	}
}
