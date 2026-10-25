package de.example.flymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Serverseitige Mod: /fly schaltet Flugerlaubnis (wie im Creative) um.
 * Der Befehl hat bewusst KEIN .requires(...), ist also für alle Spieler
 * ohne OP-Rechte nutzbar. Clients brauchen die Mod nicht.
 */
public class FlyMod implements ModInitializer {

	/** Spieler, die /fly aktiviert haben (nur im Speicher, Reset bei Serverstart). */
	private static final Set<UUID> FLYERS = ConcurrentHashMap.newKeySet();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));

		// Nach dem Respawn gehen die Abilities verloren -> erneut setzen.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (FLYERS.contains(newPlayer.getUUID())) {
				applyFlight(newPlayer, true);
			}
		});

		// Beim Verlassen aufräumen.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FLYERS.remove(handler.getPlayer().getUUID()));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("fly").executes(FlyMod::toggleFly));
	}

	private static int toggleFly(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("Nur Spieler können /fly benutzen."));
			return 0;
		}

		if (player.isCreative() || player.isSpectator()) {
			ctx.getSource().sendFailure(Component.literal("Du kannst in diesem Spielmodus bereits fliegen."));
			return 0;
		}

		boolean enable = FLYERS.add(player.getUUID());
		if (!enable) {
			FLYERS.remove(player.getUUID());
		}

		applyFlight(player, enable);
		ctx.getSource().sendSuccess(
				() -> Component.literal(enable ? "Fliegen aktiviert." : "Fliegen deaktiviert."), false);
		return 1;
	}

	private static void applyFlight(ServerPlayer player, boolean enable) {
		player.getAbilities().mayfly = enable;
		if (!enable) {
			player.getAbilities().flying = false;
			// Kein Fallschaden, wenn man mitten in der Luft ausschaltet.
			player.resetFallDistance();
		}
		player.onUpdateAbilities();
	}
}
