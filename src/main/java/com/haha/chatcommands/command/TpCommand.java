package com.haha.chatcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class TpCommand implements CommandHandler {
	@Override
	public String name() {
		return "tp";
	}

	@Override
	public void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection,
			PermissionGate gate
	) {
		// Vanilla /tp requires OP - without replacing it, non-ops get "Unknown command"
		VanillaCommands.removeLiteral(dispatcher, name());

		dispatcher.register(
				Commands.literal(name())
						.requires(source -> true)
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> execute(context.getSource(), EntityArgument.getPlayer(context, "player"), gate)))
		);
	}

	private int execute(CommandSourceStack source, ServerPlayer target, PermissionGate gate) throws CommandSyntaxException {
		if (!gate.allow(source, name())) {
			return 0;
		}

		ServerPlayer self = source.getPlayerOrException();
		if (self.getUUID().equals(target.getUUID())) {
			source.sendFailure(Component.literal("You are already at yourself"));
			return 0;
		}

		teleportToPlayer(self, target);
		source.sendSuccess(
				() -> Component.literal("Teleported to " + target.getGameProfile().name()),
				false
		);
		return Command.SINGLE_SUCCESS;
	}

	private static void teleportToPlayer(ServerPlayer self, ServerPlayer target) {
		TeleportTransition transition = new TeleportTransition(
				target.level(),
				target.position(),
				Vec3.ZERO,
				target.getYRot(),
				target.getXRot(),
				Set.of(),
				TeleportTransition.DO_NOTHING
		);
		self.teleport(transition);
	}
}
