package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** The dev-only index verification command on NeoForge: {@code /sculksight-verify-index <chunkRadius>}. */
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
