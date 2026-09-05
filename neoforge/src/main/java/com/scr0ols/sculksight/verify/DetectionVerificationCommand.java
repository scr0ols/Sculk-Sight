package com.scr0ols.sculksight.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Mode C's differential verification command on NeoForge:
 * {@code /sculksight-verify-detection <scene> [samples] [seed]}.
 *
 * <p><b>A thin Brigadier shim over {@link DetectionVerificationCommandCore}</b>, the NeoForge
 * counterpart of {@code fabric}'s own {@code DetectionVerificationCommand} - the same relationship
 * {@link VerificationCommand}'s own javadoc explains for mode A's command, including why
 * {@code CommandSourceStack} rather than a client-only source type, why {@code Minecraft.getInstance()}
 * stands in for {@code source.getClient()}, and why {@code sendSuccess(Supplier<Component>, boolean)}
 * with a constant {@code false} is this command's own feedback shape.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019, through the same gate and
 * the same shape {@code /sculksight-verify} uses. See {@code SculkSightNeoForge}.
 */
public final class DetectionVerificationCommand {

	private DetectionVerificationCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight-verify-detection")
						.then(Commands.argument("scene", StringArgumentType.word())
								.executes(context -> run(context.getSource(),
										StringArgumentType.getString(context, "scene"), 200, null))
								.then(Commands.argument("samples", IntegerArgumentType.integer(2, 20000))
										.executes(context -> run(context.getSource(),
												StringArgumentType.getString(context, "scene"),
												IntegerArgumentType.getInteger(context, "samples"), null))
										.then(Commands.argument("seed", LongArgumentType.longArg())
												.executes(context -> run(context.getSource(),
														StringArgumentType.getString(context, "scene"),
														IntegerArgumentType.getInteger(context, "samples"),
														LongArgumentType.getLong(context, "seed")))))));
	}

	private static int run(CommandSourceStack source, String scene, int samples, Long seedOverride) {
		return DetectionVerificationCommandCore.run(Minecraft.getInstance(),
				message -> source.sendSuccess(() -> Component.literal(message), false),
				scene, samples, seedOverride);
	}
}
