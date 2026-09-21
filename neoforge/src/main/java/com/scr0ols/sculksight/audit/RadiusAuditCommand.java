package com.scr0ols.sculksight.audit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** Registers {@code /sculksight find <type> <n> <mode>} as a NeoForge client command. */
public final class RadiusAuditCommand {

	private RadiusAuditCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight")
						.then(Commands.literal("find")
								.then(Commands.argument("type", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider
												.suggest(RadiusAuditRequest.TYPE_NAMES, builder))
										.then(Commands.argument("radius",
												IntegerArgumentType.integer(RadiusAuditRequest.MIN_RADIUS,
														RadiusAuditRequest.MAX_RADIUS))
												.then(Commands.argument("mode", StringArgumentType.word())
														.suggests((context, builder) -> SharedSuggestionProvider
																.suggest(RadiusAuditMode.NAMES, builder))
														.executes(context -> run(context.getSource(),
																StringArgumentType.getString(context, "type"),
																IntegerArgumentType.getInteger(context, "radius"),
																StringArgumentType.getString(context, "mode"))))))));
	}

	private static int run(CommandSourceStack source, String type, int radius, String mode) {
		return RadiusAuditClient.run(
				message -> source.sendSuccess(() -> Component.literal(message), false),
				type,
				radius,
				mode);
	}
}
