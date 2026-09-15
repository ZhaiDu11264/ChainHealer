package ho.artisan.chainhealer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ChainHealer.MODID)
public class ChainHealer {
	public static final String MODID = "chainhealer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	/** In-game chat notices (blocked exports / rescued packages). Off by default. */
	public static final ModConfigSpec.BooleanValue CHAT_NOTICES = BUILDER
			.comment("Broadcast an in-game chat notice when a frogport export is blocked",
					"or when stranded packages are rescued from a dead chain conveyor lane.",
					"Off by default - the log still records the details.")
			.define("alerts.chatNotices", false);

	/** Sound cue played at the affected conveyor. Off by default. */
	public static final ModConfigSpec.BooleanValue SOUND_CUE = BUILDER
			.comment("Play a sound at the affected conveyor when stranded packages are rescued.",
					"Only used when alerts.chatNotices is enabled.")
			.define("alerts.soundCue", false);

	/** Diagnostic log lines. On by default (rate-limited, useful for bug reports). */
	public static final ModConfigSpec.BooleanValue LOG_DIAGNOSTICS = BUILDER
			.comment("Write rate-limited WARN lines for blocked frogport exports and",
					"purged orphaned lanes to the log file.",
					"On by default - these lines are what makes bug reports diagnosable.")
			.define("diagnostics.logToConsole", true);

	public static final ModConfigSpec SPEC = BUILDER.build();

	public ChainHealer(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
		LOGGER.info("Chain Healer loaded - chain conveyor connections now heal instead of amputating");
	}
}
