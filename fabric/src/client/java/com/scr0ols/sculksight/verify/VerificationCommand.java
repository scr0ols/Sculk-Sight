package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

/**
 * The dev-only differential verification command: {@code /sculksight-verify <scene> [samples]}.
 *
 * <p><b>A thin Brigadier shim over {@link VerificationCommandCore}</b>, since DECISIONS.md
 * ADR-043's follow-up split moved everything else - the solve, the probe, the report - into
 * {@code common}, generic over a plain {@code Minecraft} client and a feedback callback rather
 * than tied to {@link FabricClientCommandSource}. This class's own job is exactly two things
 * {@link FabricClientCommandSource} supplies that {@code common} cannot: Fabric's own client
 * command tree, and where its feedback actually goes.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 - the gate is a real
 * check rather than an intention, so this command cannot exist in a shipped jar. See
 * {@code SculkSightClient} for where that gate is applied.
 */
public final class VerificationCommand {

	private VerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify")
						.then(ClientCommands.argument("scene", StringArgumentType.word())
								.executes(context -> run(context.getSource(),
										StringArgumentType.getString(context, "scene"), 200, null))
								.then(ClientCommands.argument("samples", IntegerArgumentType.integer(2, 20000))
										.executes(context -> run(context.getSource(),
												StringArgumentType.getString(context, "scene"),
												IntegerArgumentType.getInteger(context, "samples"), null))
										.then(ClientCommands.argument("seed", LongArgumentType.longArg())
												.executes(context -> run(context.getSource(),
														StringArgumentType.getString(context, "scene"),
														IntegerArgumentType.getInteger(context, "samples"),
														LongArgumentType.getLong(context, "seed")))))));
	}

	private static int run(FabricClientCommandSource source, String scene, int samples, Long seedOverride) {
		return VerificationCommandCore.run(source.getClient(),
				message -> source.sendFeedback(Component.literal(message)),
				scene, samples, seedOverride);
	}
}
