package com.haha.chatcommands.command;

import com.haha.chatcommands.db.PermissionRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionGate {
	private final PermissionRepository permissions;

	public PermissionGate(PermissionRepository permissions) {
		this.permissions = permissions;
	}

	public boolean allow(CommandSourceStack source, String commandName) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can use /" + commandName));
			return false;
		}

		if (!permissions.hasPermission(player.getUUID(), commandName)) {
			source.sendFailure(Component.literal("You do not have permission to use /" + commandName));
			return false;
		}
		return true;
	}
}
