package com.haha.chatcommands.db;

import java.util.UUID;

public record Home(
		UUID playerUuid,
		String name,
		String world,
		double x,
		double y,
		double z,
		float yaw,
		float pitch
) {
}
