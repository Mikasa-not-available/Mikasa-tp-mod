package com.haha.chatcommands.tpa;

import com.haha.chatcommands.MikasaTpMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TPA flow: request -> accept/deny -> 5s no-move warmup -> teleport.
 * Countdown is shown via vanilla title packets (center of screen, no client mod).
 */
public final class TpaManager {
	public static final int REQUEST_TIMEOUT_TICKS = 20 * 60; // 60s to accept
	public static final int WARMUP_TICKS = 20 * 5; // 5s stand still
	private static final double MOVE_THRESHOLD_SQR = 0.05 * 0.05;

	public enum Phase {
		PENDING,
		WARMUP
	}

	public static final class Session {
		public final UUID requesterId;
		public final UUID targetId;
		public Phase phase;
		public long createdTick;
		public long warmupStartTick;
		public Vec3 warmupOrigin;
		public ResourceKey<Level> warmupDimension;
		public int lastShownSecond = -1;

		Session(UUID requesterId, UUID targetId, long createdTick) {
			this.requesterId = requesterId;
			this.targetId = targetId;
			this.phase = Phase.PENDING;
			this.createdTick = createdTick;
		}
	}

	private final Map<UUID, Session> byRequester = new ConcurrentHashMap<>();
	private final Map<UUID, UUID> incomingByTarget = new ConcurrentHashMap<>();

	public Optional<Session> findOutgoing(UUID requesterId) {
		return Optional.ofNullable(byRequester.get(requesterId));
	}

	public Optional<Session> findIncoming(UUID targetId) {
		UUID requesterId = incomingByTarget.get(targetId);
		if (requesterId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(byRequester.get(requesterId));
	}

	public boolean request(ServerPlayer requester, ServerPlayer target, long serverTick) {
		if (requester.getUUID().equals(target.getUUID())) {
			requester.sendSystemMessage(Component.literal("You cannot TPA to yourself"));
			return false;
		}

		cancelSession(requester.getUUID(), requester.level().getServer(), null, true);

		Session existingIncoming = findIncoming(target.getUUID()).orElse(null);
		if (existingIncoming != null && existingIncoming.phase == Phase.PENDING) {
			requester.sendSystemMessage(Component.literal(target.getGameProfile().name() + " already has a pending TPA"));
			return false;
		}

		Session session = new Session(requester.getUUID(), target.getUUID(), serverTick);
		byRequester.put(requester.getUUID(), session);
		incomingByTarget.put(target.getUUID(), requester.getUUID());

		requester.sendSystemMessage(Component.literal(
				"TPA request sent to " + target.getGameProfile().name() + ". Use /tpacancel to cancel."));
		target.sendSystemMessage(Component.literal(
				requester.getGameProfile().name()
						+ " wants to teleport to you. /tpaccept or /tpdeny"));
		return true;
	}

	public boolean accept(ServerPlayer target, long serverTick) {
		Session session = findIncoming(target.getUUID()).orElse(null);
		if (session == null || session.phase != Phase.PENDING) {
			target.sendSystemMessage(Component.literal("You have no pending TPA request"));
			return false;
		}

		MinecraftServer server = target.level().getServer();
		ServerPlayer requester = server.getPlayerList().getPlayer(session.requesterId);
		if (requester == null) {
			clear(session, null);
			target.sendSystemMessage(Component.literal("Requester is offline"));
			return false;
		}

		session.phase = Phase.WARMUP;
		session.warmupStartTick = serverTick;
		session.warmupOrigin = requester.position();
		session.warmupDimension = requester.level().dimension();
		session.lastShownSecond = 5;
		showCountdown(requester, 5);

		requester.sendSystemMessage(Component.literal(
				"TPA accepted. Stand still for 5 seconds. Moving cancels the teleport. /tpacancel to abort."));
		target.sendSystemMessage(Component.literal(
				"You accepted the TPA. " + requester.getGameProfile().name() + " will arrive in 5 seconds."));
		return true;
	}

	public boolean deny(ServerPlayer target) {
		Session session = findIncoming(target.getUUID()).orElse(null);
		if (session == null || session.phase != Phase.PENDING) {
			target.sendSystemMessage(Component.literal("You have no pending TPA request"));
			return false;
		}

		MinecraftServer server = target.level().getServer();
		ServerPlayer requester = server.getPlayerList().getPlayer(session.requesterId);
		clear(session, requester);

		target.sendSystemMessage(Component.literal("TPA denied"));
		if (requester != null) {
			requester.sendSystemMessage(Component.literal(
					target.getGameProfile().name() + " denied your TPA request"));
		}
		return true;
	}

	public boolean cancel(ServerPlayer player) {
		Session outgoing = byRequester.get(player.getUUID());
		if (outgoing != null) {
			MinecraftServer server = player.level().getServer();
			ServerPlayer target = server.getPlayerList().getPlayer(outgoing.targetId);
			clear(outgoing, player);
			player.sendSystemMessage(Component.literal("TPA cancelled"));
			if (target != null) {
				target.sendSystemMessage(Component.literal(
						player.getGameProfile().name() + " cancelled the TPA"));
			}
			return true;
		}

		Session incoming = findIncoming(player.getUUID()).orElse(null);
		if (incoming != null && incoming.phase == Phase.PENDING) {
			return deny(player);
		}

		player.sendSystemMessage(Component.literal("You have no active TPA"));
		return false;
	}

	public void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		Iterator<Map.Entry<UUID, Session>> it = byRequester.entrySet().iterator();
		while (it.hasNext()) {
			Session session = it.next().getValue();
			ServerPlayer requester = server.getPlayerList().getPlayer(session.requesterId);
			ServerPlayer target = server.getPlayerList().getPlayer(session.targetId);

			if (requester == null || target == null) {
				if (requester != null) {
					clearTitle(requester);
				}
				notifyGone(server, session);
				incomingByTarget.remove(session.targetId, session.requesterId);
				it.remove();
				continue;
			}

			if (session.phase == Phase.PENDING) {
				if (tick - session.createdTick >= REQUEST_TIMEOUT_TICKS) {
					requester.sendSystemMessage(Component.literal("TPA request timed out"));
					target.sendSystemMessage(Component.literal("TPA request timed out"));
					incomingByTarget.remove(session.targetId, session.requesterId);
					it.remove();
				}
				continue;
			}

			if (session.phase == Phase.WARMUP) {
				if (moved(requester, session)) {
					clearTitle(requester);
					requester.sendSystemMessage(Component.literal("TPA cancelled: you moved"));
					target.sendSystemMessage(Component.literal(
							requester.getGameProfile().name() + "'s TPA was cancelled (moved)"));
					incomingByTarget.remove(session.targetId, session.requesterId);
					it.remove();
					continue;
				}

				long elapsed = tick - session.warmupStartTick;
				int remainingSec = (int) Math.ceil((WARMUP_TICKS - elapsed) / 20.0);
				if (remainingSec >= 1 && remainingSec != session.lastShownSecond) {
					session.lastShownSecond = remainingSec;
					showCountdown(requester, remainingSec);
				}

				if (elapsed >= WARMUP_TICKS) {
					clearTitle(requester);
					teleport(requester, target);
					requester.sendSystemMessage(Component.literal(
							"Teleported to " + target.getGameProfile().name()));
					target.sendSystemMessage(Component.literal(
							requester.getGameProfile().name() + " teleported to you"));
					incomingByTarget.remove(session.targetId, session.requesterId);
					it.remove();
				}
			}
		}
	}

	private static void showCountdown(ServerPlayer player, int seconds) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Integer.toString(seconds))));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Don't move")));
	}

	private static void clearTitle(ServerPlayer player) {
		player.connection.send(new ClientboundClearTitlesPacket(true));
	}

	private static boolean moved(ServerPlayer requester, Session session) {
		if (!requester.level().dimension().equals(session.warmupDimension)) {
			return true;
		}
		return requester.position().distanceToSqr(session.warmupOrigin) > MOVE_THRESHOLD_SQR;
	}

	private static void teleport(ServerPlayer requester, ServerPlayer target) {
		TeleportTransition transition = new TeleportTransition(
				target.level(),
				target.position(),
				Vec3.ZERO,
				target.getYRot(),
				target.getXRot(),
				Set.of(),
				TeleportTransition.DO_NOTHING
		);
		requester.teleport(transition);
	}

	private void cancelSession(UUID requesterId, MinecraftServer server, String messageToRequester, boolean notifyTarget) {
		Session session = byRequester.remove(requesterId);
		if (session == null) {
			return;
		}
		incomingByTarget.remove(session.targetId, requesterId);
		if (server != null) {
			ServerPlayer requester = server.getPlayerList().getPlayer(requesterId);
			if (requester != null) {
				clearTitle(requester);
				if (messageToRequester != null) {
					requester.sendSystemMessage(Component.literal(messageToRequester));
				}
			}
			if (notifyTarget) {
				ServerPlayer target = server.getPlayerList().getPlayer(session.targetId);
				if (target != null) {
					target.sendSystemMessage(Component.literal("TPA cancelled"));
				}
			}
		}
	}

	private void clear(Session session, ServerPlayer requesterOrNull) {
		byRequester.remove(session.requesterId, session);
		incomingByTarget.remove(session.targetId, session.requesterId);
		if (requesterOrNull != null) {
			clearTitle(requesterOrNull);
		}
	}

	private void notifyGone(MinecraftServer server, Session session) {
		ServerPlayer requester = server.getPlayerList().getPlayer(session.requesterId);
		ServerPlayer target = server.getPlayerList().getPlayer(session.targetId);
		if (requester != null) {
			clearTitle(requester);
			requester.sendSystemMessage(Component.literal("TPA cancelled: player offline"));
		}
		if (target != null) {
			target.sendSystemMessage(Component.literal("TPA cancelled: player offline"));
		}
		MikasaTpMod.LOGGER.debug("Cleared TPA session due to offline player");
	}
}
