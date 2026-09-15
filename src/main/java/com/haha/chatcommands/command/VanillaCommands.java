package com.haha.chatcommands.command;

import com.haha.chatcommands.MikasaTpMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Vanilla {@code /tp} is OP-only. Merging into it keeps that requirement, so non-ops
 * see "Unknown command". We remove the vanilla literal first, then register ours.
 */
public final class VanillaCommands {
	private VanillaCommands() {
	}

	@SuppressWarnings("unchecked")
	public static void removeLiteral(CommandDispatcher<?> dispatcher, String name) {
		try {
			CommandNode<?> root = dispatcher.getRoot();
			Field childrenField = CommandNode.class.getDeclaredField("children");
			childrenField.setAccessible(true);
			Map<String, CommandNode<?>> children = (Map<String, CommandNode<?>>) childrenField.get(root);
			children.remove(name);

			Field literalsField = CommandNode.class.getDeclaredField("literals");
			literalsField.setAccessible(true);
			Map<String, LiteralCommandNode<?>> literals = (Map<String, LiteralCommandNode<?>>) literalsField.get(root);
			literals.remove(name);

			MikasaTpMod.LOGGER.info("Removed vanilla command /{} so the mod can replace it", name);
		} catch (ReflectiveOperationException e) {
			MikasaTpMod.LOGGER.error("Failed to remove vanilla command /{}", name, e);
		}
	}
}
