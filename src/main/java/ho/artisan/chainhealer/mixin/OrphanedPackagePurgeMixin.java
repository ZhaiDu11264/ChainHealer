package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageEntity;
import ho.artisan.chainhealer.ChainHealer;
import ho.artisan.chainhealer.Notice;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purges orphaned and empty travelling-package lanes.
 *
 * <p>Two vanilla leak paths permanently occupy conveyor capacity with
 * packages that can never move and (rendered at a stale position or
 * {@code Vec3.ZERO}) are effectively invisible to the player:
 *
 * <ol>
 * <li><b>Orphaned lanes:</b> {@code removeInvalidConnections()} (vanilla,
 * pre-ChainHealer, and still on the client) removes a dead connection from
 * {@code connections} but leaves {@code travellingPackages} untouched - unlike
 * {@code removeConnectionTo()}, which drops them. A package stranded this way
 * is keyed by an offset that is no longer a connection: {@code tick()} skips
 * it ({@code connectionStats.get(target) == null}), it can never reach the far
 * end, and it can never be dead-lettered (it is not looping). It counts
 * against {@code canAcceptMorePackages()} forever.</li>
 * <li><b>Empty lanes:</b> when a travelling package arrives, vanilla removes
 * the package from its list but never removes the now-empty list. Empty lanes
 * inflate the capacity check ({@code travellingPackages.size()} counts lanes,
 * not packages), so busy junctions silently lose capacity over time.</li>
 * </ol>
 *
 * <p>Either state produces the reported symptom: the node claims to be "at
 * package capacity" while nothing visible is on the chain, a re-connect frees
 * exactly one slot (one shipment), and only breaking the wheel truly resets
 * it. This purge runs server-side on every tick and is a no-op on healthy
 * conveyors.
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class OrphanedPackagePurgeMixin {

	@Shadow
	public Map<BlockPos, List<ChainConveyorPackage>> travellingPackages;

	@Shadow
	public abstract void notifyUpdate();

	@Unique
	private static final long chainhealer$LOG_COOLDOWN_MS = 5000;

	@Unique
	private static final Map<BlockPos, Long> chainhealer$lastPurgeLog = new ConcurrentHashMap<>();

	@Inject(method = "tick", at = @At("HEAD"))
	private void chainhealer$purgeOrphanedLanes(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (level == null || level.isClientSide() || travellingPackages.isEmpty())
			return;

		ChainConveyorBlockEntity clbe = (ChainConveyorBlockEntity) (Object) this;
		List<BlockPos> deadLanes = null;
		int droppedPackages = 0;

		for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
			BlockPos lane = entry.getKey();
			List<ChainConveyorPackage> boxes = entry.getValue();

			boolean orphaned = !clbe.connections.contains(lane);
			if (!orphaned && !boxes.isEmpty())
				continue;

			if (deadLanes == null)
				deadLanes = new ArrayList<>();

			if (orphaned) {
				// Drop stranded packages as item entities so nothing is destroyed.
				for (ChainConveyorPackage box : boxes) {
					chainhealer$dropPackage(level, self, box);
					droppedPackages++;
				}
			}
			deadLanes.add(lane);
		}

		if (deadLanes == null)
			return;

		for (BlockPos lane : deadLanes)
			travellingPackages.remove(lane);

		if (droppedPackages > 0) {
			// Orphaned packages were stranded: sync the corrected state, wake
			// the player up in chat and ping the location so it can be found.
			clbe.notifyUpdate();
			Notice.chat(level, self.getBlockPos(), "purge",
					"chainhealer.notice.purge", ChatFormatting.RED, 60_000L,
					self.getBlockPos().toShortString(), droppedPackages, deadLanes.size());
			level.playSound(null, self.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
					SoundSource.BLOCKS, 1.0F, 0.5F);
			long now = System.currentTimeMillis();
			Long last = chainhealer$lastPurgeLog.get(self.getBlockPos());
			if (last == null || now - last >= chainhealer$LOG_COOLDOWN_MS) {
				chainhealer$lastPurgeLog.put(self.getBlockPos(), now);
				ChainHealer.LOGGER.warn("[Purge] conveyor {} removed {} orphaned lane(s) and dropped {} package(s): {}",
						self.getBlockPos(), deadLanes.size(), droppedPackages, deadLanes);
			}
		}
		// Empty-lane cleanup is routine housekeeping under normal traffic:
		// silent, and no client sync needed (an empty lane renders nothing,
		// and the client never consults capacity).
	}

	@Unique
	private static void chainhealer$dropPackage(Level level, BlockEntity be, ChainConveyorPackage box) {
		Vec3 pos = box.worldPosition != null ? box.worldPosition.subtract(0.0, 0.5, 0.0)
				: Vec3.atBottomCenterOf(be.getBlockPos()).add(0.0, 1.0, 0.0);
		Entity entity = PackageEntity.fromItemStack(level, pos, box.item);
		level.addFreshEntity(entity);
	}
}
