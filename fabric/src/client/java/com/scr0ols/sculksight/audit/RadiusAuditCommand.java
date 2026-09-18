package com.scr0ols.sculksight.audit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Mode B's entry point on Fabric: {@code /sculksight find <type> <n> <mode>}. ARCHITECTURE.md
 * section 12.
 *
 * <p><b>A thin Brigadier shim over {@link RadiusAuditClient}</b>, the same split the three
 * verification commands already use. Everything this class knows is how to reach Fabric's client
 * command API; everything about what the arguments mean, and about turning the sensor index into
 * those arguments' candidate set, is in {@code common}.
 *
 * <p><b>A client command, so it works on a vanilla server.</b>
 * {@code ClientCommandRegistrationCallback} registers into the client's own dispatcher, which
 * resolves the command locally and never sends it to the server - PLAN.md section 5's requirement
 * for this phase, and the reason mode B is usable on a server that has never heard of this mod.
 *
 * <p><b>Registered unconditionally</b>, unlike {@code com.scr0ols.sculksight.verify}'s three
 * commands: this one is a player-facing feature rather than a development mechanism, so ADR-019's
 * gate does not apply to it.
 *
 * <p>Brigadier does the parsing that Brigadier is good at. The radius is an
 * {@code IntegerArgumentType} bounded by the record's own constants, so a non-integer or an
 * out-of-range number is rejected before this mod's code runs and the player gets Brigadier's own
 * red underline as they type. The detector and the mode are each a word with a suggestion provider
 * rather than a set of literal children, which costs one validation branch in
 * {@link RadiusAuditRequest} and {@link RadiusAuditMode} respectively and buys having those
 * branches somewhere the JUnit suite can reach - the trade ARCHITECTURE.md section 12.1 describes
 * as keeping selection and validation at layer 1.
 *
 * <p><b>All three arguments are required, and the type comes before the radius.</b> Neither mode is
 * a default: a player who has not said {@code static} or {@code live} has not yet said which of the
 * command's two jobs they wanted, and guessing for them is what made the older
 * {@code /sculksight radius} hard to explain (see {@link RadiusAuditMode}). The type is first
 * because that is also what makes it safely required rather than optional - it is a
 * {@code word()}, which would happily match a radius like {@code 64} and report it as an unknown
 * detector if the argument could be skipped. {@code all} is therefore a suggested value in
 * {@link RadiusAuditRequest#TYPE_NAMES} rather than something the player expresses by leaving the
 * argument out.
 *
 * <p><b>Renamed from {@code /sculksight radius} on 2026-09-17, with no alias.</b> That name appears
 * in no tagged release - {@code v0.0.1}, {@code v0.1.0} and {@code v0.2.0} all predate the whole
 * audit package - so nothing a player has ever run is being broken and there is no deprecation
 * period to serve.
 */
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
