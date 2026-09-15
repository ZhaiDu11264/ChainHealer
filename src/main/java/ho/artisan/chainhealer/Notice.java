package ho.artisan.chainhealer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * In-game chat notifications for ChainHealer events.
 *
 * <p>Log files are for forensics; players need to know <i>now</i> when the
 * network eats a package or a frogport goes deaf. This broadcasts a colored
 * chat line to everyone on the server, rate-limited per position + reason so
 * a persistently broken spot does not turn into chat spam.
 */
public final class Notice {

	private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

	private Notice() {}

	/**
	 * Broadcast "[ChainHealer] <message>" to all players.
	 *
	 * @param level      server level the event happened on
	 * @param pos        position of the affected conveyor/port
	 * @param cooldownKey unique key for rate limiting (position + reason)
	 * @param langKey    translation key (see assets/chainhealer/lang)
	 * @param color      message color after the gold prefix
	 * @param cooldownMs minimum interval between two identical notices
	 * @param args       translation arguments
	 */
	public static void chat(Level level, BlockPos pos, String cooldownKey, String langKey,
			ChatFormatting color, long cooldownMs, Object... args) {
		if (!ChainHealer.CHAT_NOTICES.get())
			return;
		if (level.isClientSide())
			return;
		MinecraftServer server = level.getServer();
		if (server == null)
			return;

		String key = level.dimension().location() + "|" + pos.toShortString() + "|" + cooldownKey;
		long now = System.currentTimeMillis();
		Long last = COOLDOWNS.get(key);
		if (last != null && now - last < cooldownMs)
			return;
		COOLDOWNS.put(key, now);

		Component message = Component.literal("[ChainHealer] ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable(langKey, args).withStyle(color));
		server.getPlayerList().broadcastSystemMessage(message, false);
	}
}
