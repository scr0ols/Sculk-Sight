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

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** Registers {@code /sculksight find <type> <n> <mode>} (and its {@code find off} short-circuit) as a NeoForge client command. */
public final class RadiusAuditCommand {

	private static final String OFF_NAME = "off";

	/** {@code off}, then every real detector type, in the order offered as completions. */
	private static final List<String> TYPE_SUGGESTIONS =
			Stream.concat(Stream.of(OFF_NAME), RadiusAuditRequest.TYPE_NAMES.stream()).toList();

	private RadiusAuditCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("sculksight")
						.then(Commands.literal("find")
								.then(Commands.argument("type", StringArgumentType.word())
										.suggests((context, builder) -> suggestTypesAndOff(builder))
										.executes(context -> runTypeOnly(context.getSource(),
												StringArgumentType.getString(context, "type")))
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
				component -> source.sendSuccess(() -> component, false),
				type,
				radius,
				mode);
	}

	/** Handles {@code /sculksight find <type>} with nothing after it: only {@code off} means anything here. */
	private static int runTypeOnly(CommandSourceStack source, String type) {
		if (type.toLowerCase(Locale.ROOT).equals(OFF_NAME)) {
			return runOff(source);
		}

		source.sendSuccess(() -> Component.translatable("sculksight.command.find.usage"), false);
		return RadiusAuditCommandCore.FAILURE;
	}

	private static int runOff(CommandSourceStack source) {
		return RadiusAuditClient.runOff(component -> source.sendSuccess(() -> component, false));
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
