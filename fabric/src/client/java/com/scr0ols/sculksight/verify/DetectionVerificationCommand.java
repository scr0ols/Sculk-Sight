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
 * Mode C's differential verification command:
 * {@code /sculksight-verify-detection <scene> [samples] [seed]}.
 *
 * <p><b>A thin Brigadier shim over {@link DetectionVerificationCommandCore}</b>, the same split
 * {@link VerificationCommand}'s own javadoc explains for mode A's command.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019, through the same gate and
 * the same shape {@code /sculksight-verify} uses. See {@code SculkSightClient}.
 *
 * <p><b>Separate command rather than an argument on the existing one.</b> Mode A's command and its
 * recorded runs are this project's evidence that the shell is correct, and its argument shape is
 * quoted in the archive's evidence tables. A new mode taking a slot in it would change the shape
 * of a command whose past invocations are part of the record, for no benefit over a second name.
 */
public final class DetectionVerificationCommand {

	private DetectionVerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify-detection")
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
		return DetectionVerificationCommandCore.run(source.getClient(),
				message -> source.sendFeedback(Component.literal(message)),
				scene, samples, seedOverride);
	}
}
