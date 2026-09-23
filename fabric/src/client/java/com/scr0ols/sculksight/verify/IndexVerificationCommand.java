package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

/** The dev-only index verification command: {@code /sculksight-verify-index <chunkRadius>}. */
public final class IndexVerificationCommand {

	private IndexVerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify-index")
						.then(ClientCommands.argument("chunkRadius",
								IntegerArgumentType.integer(1, IndexVerificationCommandCore.MAX_CHUNK_RADIUS))
								.executes(context -> run(context.getSource(),
										IntegerArgumentType.getInteger(context, "chunkRadius")))));
	}

	private static int run(FabricClientCommandSource source, int chunkRadius) {
		return IndexVerificationCommandCore.run(source.getClient(),
				message -> source.sendFeedback(Component.literal(message)),
				chunkRadius);
	}
}
