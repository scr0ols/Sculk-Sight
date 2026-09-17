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
 * Mode B's entry point on Fabric: {@code /sculksight radius <n> [type]}. ARCHITECTURE.md section 12.
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
 * red underline as they type. The detector is a word with a suggestion provider rather than three
 * literal children, which costs one validation branch in {@link RadiusAuditRequest} and buys
 * having that branch somewhere the JUnit suite can reach - the trade ARCHITECTURE.md section 12.1
 * describes as keeping selection and validation at layer 1.
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
						.then(ClientCommands.literal("radius")
								.then(ClientCommands.argument("radius",
										IntegerArgumentType.integer(RadiusAuditRequest.MIN_RADIUS,
												RadiusAuditRequest.MAX_RADIUS))
										.executes(context -> run(context.getSource(),
												IntegerArgumentType.getInteger(context, "radius"),
												null))
										.then(ClientCommands.argument("type", StringArgumentType.word())
												.suggests((context, builder) -> SharedSuggestionProvider
														.suggest(RadiusAuditRequest.DETECTOR_NAMES, builder))
												.executes(context -> run(context.getSource(),
														IntegerArgumentType.getInteger(context, "radius"),
														StringArgumentType.getString(context, "type")))))));
	}

	private static int run(FabricClientCommandSource source, int radius, String type) {
		return RadiusAuditClient.run(
				message -> source.sendFeedback(Component.literal(message)),
				radius,
				type);
	}
}
