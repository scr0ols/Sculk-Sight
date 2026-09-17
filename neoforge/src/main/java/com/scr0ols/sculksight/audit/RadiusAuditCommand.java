package com.scr0ols.sculksight.audit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Mode B's entry point on NeoForge: {@code /sculksight radius <n> [type]}. ARCHITECTURE.md
 * section 12.
 *
 * <p><b>A thin Brigadier shim over {@link RadiusAuditClient}</b>, the NeoForge counterpart of
 * {@code fabric}'s own {@code RadiusAuditCommand}. The two files share no logic and are not meant
 * to: everything they would have shared is in {@code common} already.
 *
 * <p><b>A client command, so it works on a vanilla server.</b> {@code RegisterClientCommandsEvent}
 * hands over the client's own dispatcher, so the command resolves locally and is never sent to the
 * server - PLAN.md section 5's requirement for this phase.
 *
 * <p><b>Registered unconditionally</b>, unlike {@code com.scr0ols.sculksight.verify}'s three
 * commands: this one is a player-facing feature rather than a development mechanism, so ADR-019's
 * gate does not apply to it. It is therefore registered outside the {@code FMLEnvironment}
 * branch in {@code SculkSightNeoForge}.
 *
 * <p>The argument shape and the reasoning behind it are the Fabric class's javadoc; this one is
 * kept short deliberately, so the two do not drift into two different accounts of one decision.
 */
public final class RadiusAuditCommand {

	private RadiusAuditCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight")
						.then(Commands.literal("radius")
								.then(Commands.argument("radius",
										IntegerArgumentType.integer(RadiusAuditRequest.MIN_RADIUS,
												RadiusAuditRequest.MAX_RADIUS))
										.executes(context -> run(context.getSource(),
												IntegerArgumentType.getInteger(context, "radius"),
												null))
										.then(Commands.argument("type", StringArgumentType.word())
												.suggests((context, builder) -> SharedSuggestionProvider
														.suggest(RadiusAuditRequest.DETECTOR_NAMES, builder))
												.executes(context -> run(context.getSource(),
														IntegerArgumentType.getInteger(context, "radius"),
														StringArgumentType.getString(context, "type")))))));
	}

	private static int run(CommandSourceStack source, int radius, String type) {
		return RadiusAuditClient.run(
				message -> source.sendSuccess(() -> Component.literal(message), false),
				radius,
				type);
	}
}
