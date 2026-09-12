package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Set;

/**
 * Heals one-sided chain conveyor connections instead of amputating them.
 *
 * <p>Vanilla keeps a connection alive only if BOTH conveyors still list each
 * other. When a save/load cycle or an unload-timing race leaves one side
 * missing (the other side still points at it), vanilla's
 * {@code removeInvalidConnections()} deletes the surviving half. The frogport
 * bound to that conveyor then fails its
 * {@code connections.contains(connection)} check and silently refuses to send
 * packages ("does not recognise" the conveyor), routing entries time out and
 * in-flight packages loop forever.
 *
 * <p>This mixin replaces the amputation with a repair: if the opposite
 * conveyor exists but lacks the reverse record, we ADD it there. Connections
 * only get removed when the target position no longer holds a chain conveyor
 * at all. The same repair also runs on every lazy tick, so corruption that
 * happens mid-session (or while the opposite chunk loads later) self-heals
 * within a second.
 *
 * <p>Note: this mixin deliberately shadows no vanilla members (the mod mixes
 * with {@code remap=false} and has no refmap) - level and position are
 * reached by casting {@code this} to {@code BlockEntity}.
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorHealMixin {

	@Shadow
	public Set<BlockPos> connections;

	/**
	 * Validate + repair every connection of this conveyor.
	 * Runs on load (replacing removeInvalidConnections) and on every lazy tick.
	 */
	@Unique
	private void chainhealer$healConnections() {
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		BlockPos selfPos = self.getBlockPos();
		if (level == null || level.isClientSide())
			return;

		ChainConveyorBlockEntity selfClbe = (ChainConveyorBlockEntity) (Object) this;
		boolean changed = false;

		for (BlockPos next : new ArrayList<>(connections)) {
			BlockPos target = selfPos.offset(next);
			if (!level.isLoaded(target))
				continue;

			BlockEntity be = level.getBlockEntity(target);
			if (be instanceof ChainConveyorBlockEntity ccbe) {
				// One-sided record: the neighbour exists but doesn't list us.
				// Vanilla deletes our half here; we repair their half instead.
				if (!ccbe.connections.contains(next.multiply(-1))) {
					ccbe.addConnectionTo(selfPos);
					changed = true;
				}
			} else {
				// Target is no longer a chain conveyor at all -> legitimate removal.
				// removeConnectionTo also cleans stats and drops travelling packages.
				selfClbe.removeConnectionTo(target);
				changed = true;
			}
		}

		if (changed)
			selfClbe.notifyUpdate();
	}

	/**
	 * Full takeover of the vanilla amputation: heal instead of remove.
	 */
	@Inject(method = "removeInvalidConnections", at = @At("HEAD"), cancellable = true)
	private void chainhealer$healInsteadOfAmputate(CallbackInfo ci) {
		ci.cancel();
		chainhealer$healConnections();
	}

	/**
	 * Periodic repair pass: catches corruption that happens while this
	 * conveyor is already running (unload races, contraption transforms) and
	 * the case where the opposite conveyor's chunk only loads later.
	 */
	@Inject(method = "lazyTick", at = @At("HEAD"))
	private void chainhealer$periodicHeal(CallbackInfo ci) {
		chainhealer$healConnections();
	}
}
