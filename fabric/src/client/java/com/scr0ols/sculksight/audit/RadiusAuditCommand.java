package com.scr0ols.sculksight.audit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** Registers {@code /sculksight find <type> <n> <mode>} as a Fabric client command. */
public final class RadiusAuditCommand {

	private RadiusAuditCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight")
						.then(ClientCommands.literal("find")
								.then(ClientCommands.argument("type", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider
												.suggest(RadiusAuditRequest.TYPE_NAMES, builder))
										.then(ClientCommands.argument("radius",
												IntegerArgumentType.integer(RadiusAuditRequest.MIN_RADIUS,
														RadiusAuditRequest.MAX_RADIUS))
												.then(ClientCommands.argument("mode", StringArgumentType.word())
														.suggests((context, builder) -> SharedSuggestionProvider
																.suggest(RadiusAuditMode.NAMES, builder))
														.executes(context -> run(context.getSource(),
																StringArgumentType.getString(context, "type"),
																IntegerArgumentType.getInteger(context, "radius"),
																StringArgumentType.getString(context, "mode"))))))));
	}

	private static int run(FabricClientCommandSource source, String type, int radius, String mode) {
		return RadiusAuditClient.run(
				message -> source.sendFeedback(Component.literal(message)),
				type,
				radius,
				mode);
	}
}
