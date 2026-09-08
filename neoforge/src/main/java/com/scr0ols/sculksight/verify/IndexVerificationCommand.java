package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The dev-only mechanism DECISIONS.md ADR-041 requires, on NeoForge:
 * {@code /sculksight-verify-index <chunkRadius>}.
 *
 * <p><b>A thin Brigadier shim over {@link IndexVerificationCommandCore}</b>, the NeoForge
 * counterpart of {@code fabric}'s own {@code IndexVerificationCommand} - the same relationship
 * {@link VerificationCommand}'s own javadoc explains for mode A's command.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 and ADR-041 point 4, the
 * same gate and the same shape {@code /sculksight-verify} and
 * {@code /sculksight-verify-detection} already use. See {@code SculkSightNeoForge}.
 */
public final class IndexVerificationCommand {

	private IndexVerificationCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight-verify-index")
						.then(Commands.argument("chunkRadius",
								IntegerArgumentType.integer(1, IndexVerificationCommandCore.MAX_CHUNK_RADIUS))
								.executes(context -> run(context.getSource(),
										IntegerArgumentType.getInteger(context, "chunkRadius")))));
	}

	private static int run(CommandSourceStack source, int chunkRadius) {
		return IndexVerificationCommandCore.run(Minecraft.getInstance(),
				message -> source.sendSuccess(() -> Component.literal(message), false),
				chunkRadius);
	}
}
