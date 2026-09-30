package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

/** Registers {@code /sculksight find <type> <n> <mode>} (and its {@code find off} short-circuit) as a Fabric client command. */
public final class RadiusAuditCommand {

	private static final String OFF_NAME = "off";

	/** {@code off}, then every real detector type, in the order offered as completions. */
	private static final List<String> TYPE_SUGGESTIONS =
			Stream.concat(Stream.of(OFF_NAME), RadiusAuditRequest.TYPE_NAMES.stream()).toList();

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
										.suggests((context, builder) -> suggestTypesAndOff(builder))
										.executes(context -> runTypeOnly(context.getSource(),
												StringArgumentType.getString(context, "type")))
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
				source::sendFeedback,
				type,
				radius,
				mode);
	}

	/** Handles {@code /sculksight find <type>} with nothing after it: only {@code off} means anything here. */
	private static int runTypeOnly(FabricClientCommandSource source, String type) {
		if (type.toLowerCase(Locale.ROOT).equals(OFF_NAME)) {
			return runOff(source);
		}

		source.sendFeedback(Component.translatable("sculksight.command.find.usage"));
		return RadiusAuditCommandCore.FAILURE;
	}

	private static int runOff(FabricClientCommandSource source) {
		return RadiusAuditClient.runOff(source::sendFeedback);
	}

	/**
	 * Suggests {@code off} before {@link RadiusAuditRequest#TYPE_NAMES} rather than
	 * {@link SharedSuggestionProvider#suggest}'s alphabetical order. {@code SuggestionsBuilder.build()}
	 * and {@code buildFuture()} route through {@code Suggestions.create}, which always sorts its
	 * result case-insensitively regardless of the order suggestions were added in, so offering
	 * {@code off} first means building the {@link Suggestions} directly with its non-sorting
	 * constructor instead.
	 */
	private static CompletableFuture<Suggestions> suggestTypesAndOff(SuggestionsBuilder builder) {
		StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
		String needle = builder.getRemaining().toLowerCase(Locale.ROOT);
		List<Suggestion> matches = TYPE_SUGGESTIONS.stream()
				.filter(name -> name.startsWith(needle))
				.filter(name -> !name.equals(builder.getRemaining()))
				.map(name -> new Suggestion(range, name))
				.toList();
		return CompletableFuture.completedFuture(new Suggestions(range, matches));
	}
}
