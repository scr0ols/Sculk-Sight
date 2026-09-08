package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

/**
 * The dev-only mechanism DECISIONS.md ADR-041 requires:
 * {@code /sculksight-verify-index <chunkRadius>}.
 *
 * <p><b>A thin Brigadier shim over {@link IndexVerificationCommandCore}</b>, the same split
 * {@link VerificationCommand}'s own javadoc explains for mode A's command.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 and ADR-041 point 4, the
 * same gate and the same shape {@code /sculksight-verify} and {@code /sculksight-verify-detection}
 * already use. See {@code SculkSightClient}.
 */
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
