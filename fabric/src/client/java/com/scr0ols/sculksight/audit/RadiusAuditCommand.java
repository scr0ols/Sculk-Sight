package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

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
														.suggests((context, builder) -> suggestModes(builder))
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

	/**
	 * Suggests {@link RadiusAuditMode#NAMES} in their declared priority order rather than
	 * {@link SharedSuggestionProvider#suggest}'s alphabetical one. {@code SuggestionsBuilder.build()}
	 * and {@code buildFuture()} route through {@code Suggestions.create}, which always sorts its
	 * result case-insensitively regardless of the order suggestions were added in, so offering
	 * {@code off}, {@code static}, {@code live} in that order means building the {@link Suggestions}
	 * directly with its non-sorting constructor instead.
	 */
	private static CompletableFuture<Suggestions> suggestModes(SuggestionsBuilder builder) {
		StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
		List<Suggestion> matches = RadiusAuditMode.suggestionsMatching(builder.getRemaining()).stream()
				.filter(name -> !name.equals(builder.getRemaining()))
				.map(name -> new Suggestion(range, name))
				.toList();
		return CompletableFuture.completedFuture(new Suggestions(range, matches));
	}
}
