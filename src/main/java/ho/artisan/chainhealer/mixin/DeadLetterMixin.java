package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dead-letter mechanism for undeliverable packages.
 *
 * <p>Vanilla has NO timeout for packages circulating on chain conveyors: a
 * package whose address never matches a live routing entry (receiver
 * deregistered, zombie flip-migration, backed-up receivers) loops forever and
 * permanently occupies the conveyor's package capacity (default 20). Once the
 * capacity fills, frogports bound onto that wheel refuse to export
 * ({@code canAcceptPackagesFor} fails) - which players experience as the port
 * "not recognising" the conveyor. Re-connecting a chain frees exactly the
 * travelling packages on that one connection, so the port sends a single
 * package and clogs again; only breaking the wheel (dropping ALL packages)
 * resets it.
 *
 * <p>This mixin tracks how long each package has been looping without being
 * delivered and drops it as an item entity after a grace period, so the
 * network always self-unblocks. Dropped packages can be picked up - nothing
 * is destroyed.
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class DeadLetterMixin {

	/** Grace period (in ticks) a package may loop undelivered before it is dropped. */
	@Unique
	private static final int chainhealer$MAX_LOOP_AGE = 3600; // 3 minutes

	/**
	 * Package ages, tracked globally so they survive handoffs between conveyors.
	 * Weak keys: once a package is delivered or removed, the entry is
	 * garbage-collected automatically.
	 */
	@Unique
	private static final WeakHashMap<ChainConveyorPackage, Integer> chainhealer$loopAges = new WeakHashMap<>();

	@Shadow
	public List<ChainConveyorPackage> loopingPackages;

	@Inject(method = "tick", at = @At("HEAD"))
	private void chainhealer$dropDeadLetters(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (self.getLevel() == null || self.getLevel().isClientSide() || loopingPackages.isEmpty())
			return;

		List<ChainConveyorPackage> dead = null;

		for (ChainConveyorPackage box : loopingPackages) {
			int age = chainhealer$loopAges.merge(box, 1, Integer::sum);
			if (age >= chainhealer$MAX_LOOP_AGE && box.worldPosition != null) {
				Entity packageEntity = PackageEntity.fromItemStack(
						self.getLevel(), box.worldPosition.subtract(0.0, 0.5, 0.0), box.item);
				self.getLevel().addFreshEntity(packageEntity);
				chainhealer$loopAges.remove(box);
				if (dead == null)
					dead = new ArrayList<>();
				dead.add(box);
			}
		}

		if (dead != null) {
			loopingPackages.removeAll(dead);
			((ChainConveyorBlockEntity) (Object) this).notifyUpdate();
		}
	}
}
