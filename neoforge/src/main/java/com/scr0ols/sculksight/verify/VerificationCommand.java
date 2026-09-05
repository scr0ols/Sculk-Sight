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
 * The dev-only differential verification command on NeoForge:
 * {@code /sculksight-verify <scene> [samples] [seed]}.
 *
 * <p><b>A thin Brigadier shim over {@link VerificationCommandCore}</b>, the NeoForge counterpart
 * of {@code fabric}'s own {@code VerificationCommand} - same core, same argument shape. Only the
 * command source type differs: NeoForge's own {@code RegisterClientCommandsEvent} hands back the
 * ordinary server-shaped {@code CommandDispatcher<CommandSourceStack>} rather than a client-only
 * type (DECISIONS.md ADR-044 point 5), confirmed against `neoforged/NeoForge`'s own source
 * (`net.neoforged.neoforge.client.event.RegisterClientCommandsEvent`, tag {@code 26.2.0-stable}),
 * per CONVENTIONS.md §6 - so this class registers against {@code Commands.literal}/{@code argument},
 * vanilla's own builders for that source type, rather than Fabric's {@code ClientCommands}.
 *
 * <p><b>{@code CommandSourceStack} carries no client reference of its own</b> - unlike
 * {@code FabricClientCommandSource#getClient()} - so {@code Minecraft.getInstance()} stands in
 * for it directly. This is safe here because the event that supplies the dispatcher fires only on
 * the logical client (the event's own javadoc), and this command is registered only from
 * {@code SculkSightNeoForge}'s handler for that same event.
 *
 * <p><b>Feedback: {@code CommandSourceStack#sendSuccess(Supplier<Component>, boolean)}</b>, read
 * from the real class rather than assumed to mirror {@code FabricClientCommandSource#sendFeedback}
 * (ADR-044's own "Revisit if" - confirmed by decompiling {@code net.minecraft.commands.CommandSourceStack}
 * from the module's own resolved Minecraft artifact). The boolean argument is whether to also
 * broadcast the message to other operators; this command only ever runs against a local
 * integrated server with a single player attached (ADR-019's own guard, enforced in
 * {@link VerificationCommandCore}), so there is no one else to broadcast to and it is always
 * {@code false} here.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 - the gate lives in
 * {@code SculkSightNeoForge}, the same real check fabric's own {@code SculkSightClient} applies,
 * not repeated here.
 */
public final class VerificationCommand {

	private VerificationCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight-verify")
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
		return VerificationCommandCore.run(Minecraft.getInstance(),
				message -> source.sendSuccess(() -> Component.literal(message), false),
				scene, samples, seedOverride);
	}
}
