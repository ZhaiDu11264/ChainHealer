package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ho.artisan.chainhealer.ChainHealer;
import ho.artisan.chainhealer.Notice;
import net.minecraft.ChatFormatting;

/**
 * Diagnostics: when a frogport's simulated export fails, log the exact reason.
 *
 * <p>The port's pull cycle starts with {@code export(..., simulate=true)},
 * which silently fails on any of: bound conveyor BE missing, bound connection
 * no longer in the conveyor's connection set, conveyor speed == 0 (kinetic
 * network damaged), or the far-end conveyor unavailable/full. Vanilla gives
 * zero feedback, leaving players to guess why the port "does not recognise"
 * its conveyor.
 *
 * <p>This mixin inspects each gate in order when a simulate fails and logs
 * the first blocker, rate-limited per port position.
 */
@Mixin(targets = "com.simibubi.create.content.logistics.packagePort.PackagePortTarget$ChainConveyorFrogportTarget", remap = false)
public abstract class ExportDiagnosticsMixin {

	/** own field of the target class (NOT inherited - @Shadow is legal here) */
	@Shadow
	public BlockPos connection;

	/** Log cooldown per port position (ms). */
	@Unique
	private static final long chainhealer$LOG_COOLDOWN_MS = 5000;

	@Unique
	private static final Map<BlockPos, Long> chainhealer$lastLogged = new ConcurrentHashMap<>();

	@Inject(method = "export", at = @At("RETURN"))
	private void chainhealer$diagnoseExport(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
		if (!simulate || cir.getReturnValueZ())
			return;

		long now = System.currentTimeMillis();
		Long last = chainhealer$lastLogged.get(portPos);
		if (last != null && now - last < chainhealer$LOG_COOLDOWN_MS)
			return;
		chainhealer$lastLogged.put(portPos, now);

		// inherited members (be(), relativePos) must be reached via a cast -
		// @Shadow cannot reference superclass members
		PackagePortTarget self = (PackagePortTarget) (Object) this;
		BlockPos relativePos = self.relativePos;

		String reason = null;

		if (!(self.be(level, portPos) instanceof ChainConveyorBlockEntity clbe)) {
			reason = "bound conveyor BE missing/unloaded at " + portPos.offset(relativePos);
		} else if (this.connection != null && !clbe.connections.contains(this.connection)) {
			reason = "connection " + this.connection + " missing from conveyor " + clbe.getBlockPos()
					+ " (has " + clbe.connections + ")";
		} else if (clbe.getSpeed() == 0.0F) {
			reason = "conveyor " + clbe.getBlockPos() + " speed is 0 (kinetic network damaged/stalled)";
		} else if (this.connection == null) {
			if (!clbe.canAcceptMorePackages())
				reason = "conveyor " + clbe.getBlockPos() + " at package capacity" + chainhealer$breakdown(clbe);
		} else {
			BlockPos farPos = clbe.getBlockPos().offset(this.connection);
			if (!(level.getBlockEntity(farPos) instanceof ChainConveyorBlockEntity farClbe)) {
				reason = "far-end conveyor missing/unloaded at " + farPos;
			} else if (!farClbe.canAcceptMorePackages()) {
				reason = "far-end conveyor " + farPos + " at package capacity" + chainhealer$breakdown(farClbe);
			}
		}

		if (reason == null)
			reason = "unknown (all vanilla gates passed - please report)";

		if (ChainHealer.LOG_DIAGNOSTICS.get())
			ChainHealer.LOGGER.warn("[Diagnostics] Frogport export blocked at {}: {}", portPos, reason);

		// Optional in-game notice - default off, see alerts.chatNotices in the
		// mod config. Rate-limited per position + reason so a dead spot does
		// not turn into chat spam (5 minutes).
		if (level instanceof net.minecraft.world.level.Level lvl)
			Notice.chat(lvl, portPos, "blocked:" + reason, "chainhealer.notice.blocked",
					ChatFormatting.YELLOW, 300_000L, portPos.toShortString(), reason);
	}

	/**
	 * Human-readable breakdown of a "capacity full" verdict: looping count vs
	 * per-lane travelling counts, flagging orphaned lanes (key not in
	 * {@code connections} - vanilla's pre-heal amputation leak) and empty lanes
	 * (vanilla never removes them; they still count as one slot each).
	 */
	@Unique
	private static String chainhealer$breakdown(ChainConveyorBlockEntity clbe) {
		ChainConveyorInventoryAccessor accessor = (ChainConveyorInventoryAccessor) clbe;
		List<ChainConveyorPackage> looping = accessor.chainhealer$getLoopingPackages();
		Map<BlockPos, List<ChainConveyorPackage>> travelling = accessor.chainhealer$getTravellingPackages();

		int onLanes = 0;
		int emptyLanes = 0;
		int orphanedLanes = 0;
		StringBuilder laneDetail = new StringBuilder();
		for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : travelling.entrySet()) {
			int size = entry.getValue().size();
			onLanes += size;
			if (size == 0)
				emptyLanes++;
			boolean orphaned = !clbe.connections.contains(entry.getKey());
			if (orphaned)
				orphanedLanes++;
			if (laneDetail.length() > 0)
				laneDetail.append(", ");
			laneDetail.append(entry.getKey()).append(':').append(size);
			if (orphaned)
				laneDetail.append("[ORPHAN]");
			else if (size == 0)
				laneDetail.append("[EMPTY]");
		}

		return " (looping=" + looping.size() + ", packagesOnLanes=" + onLanes + ", lanes=" + travelling.size()
				+ " [empty=" + emptyLanes + ", orphaned=" + orphanedLanes + "] detail: " + laneDetail + ")";
	}
}
