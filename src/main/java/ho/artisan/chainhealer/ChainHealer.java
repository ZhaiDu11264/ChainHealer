package ho.artisan.chainhealer;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ChainHealer.MODID)
public class ChainHealer {
	public static final String MODID = "chainhealer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	public ChainHealer() {
		LOGGER.info("Chain Healer loaded - chain conveyor connections now heal instead of amputating");
	}
}
