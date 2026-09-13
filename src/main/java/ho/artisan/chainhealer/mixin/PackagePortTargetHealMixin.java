package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectedPort;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hardens the frogport target's flip-migration against zombie states.
 *
 * <p>When a chain conveyor's rotation reverses, vanilla
 * {@code ChainConveyorFrogportTarget.register()} deregisters the port from its
 * current anchor and migrates the binding to the conveyor at the other end of
 * the chain. Two failure modes leave the port registered NOWHERE, silently,
 * forever:
 *
 * <ol>
 * <li>the other end's block entity is null (chunk unloaded, or the block there
 *     is no longer a chain conveyor) - vanilla returns early AFTER
 *     deregistering, with {@code flipped} unchanged, so every lazyTick repeats
 *     the failed migration: a permanent zombie.</li>
 * <li>after migrating, the far end does not contain the reverse connection -
 *     vanilla silently skips the re-registration.</li>
 * </ol>
 *
 * <p>With no live registration the routing table never receives the port's
 * entry, packages addressed to it loop forever and clog the network (see
 * {@link DeadLetterMixin} for the capacity consequence).
 *
 * <p>This mixin re-implements register() with three hardening rules: the
 * migration only runs when the far end is a chain conveyor that actually lists
 * the reverse connection; if the far chunk is simply unloaded the migration is
 * postponed WITHOUT deregistering (the old registration keeps working); if
 * the far end is invalid but loaded, {@code flipped} is aligned with reality
 * and the port stays registered at its current anchor.
 *
 * <p>Inherited members ({@code be()}, {@code deregister()}, {@code relativePos})
 * are reached through a cast because @Shadow cannot reference superclass
 * members of the target.
 */
@Mixin(targets = "com.simibubi.create.content.logistics.packagePort.PackagePortTarget$ChainConveyorFrogportTarget", remap = false)
public abstract class PackagePortTargetHealMixin {

	// own fields of the target class - @Shadow is legal for these
	@Shadow
	public float chainPos;

	@Shadow
	public BlockPos connection;

	@Shadow
	public boolean flipped;

	@Inject(method = "register", at = @At("HEAD"), cancellable = true)
	private void chainhealer$hardenedRegister(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos, CallbackInfo ci) {
		ci.cancel();

		PackagePortTarget self = (PackagePortTarget) (Object) this;

		if (!(self.be(level, portPos) instanceof ChainConveyorBlockEntity clbe))
			return;

		ChainConveyorBlockEntity registerAt = clbe;
		BlockPos conn = this.connection;
		boolean speedNegative = clbe.getSpeed() < 0.0F;

		if (conn != null && speedNegative != this.flipped) {
			BlockPos farPos = clbe.getBlockPos().offset(conn);
			boolean farLoaded = !(level instanceof Level l) || l.isLoaded(farPos);

			if (farLoaded
					&& level.getBlockEntity(farPos) instanceof ChainConveyorBlockEntity farClbe
					&& farClbe.connections.contains(conn.multiply(-1))) {
				// vanilla migration, with the far end verified first
				self.deregister(ppbe, level, portPos);
				clbe.prepareStats();
				ConnectionStats stats = clbe.connectionStats.get(conn);
				if (stats != null)
					this.chainPos = stats.chainLength() - this.chainPos;
				this.connection = conn.multiply(-1);
				this.flipped = !this.flipped;
				self.relativePos = farClbe.getBlockPos().subtract(portPos);
				ppbe.notifyUpdate();
				registerAt = farClbe;
			} else if (!farLoaded) {
				// far chunk not loaded yet: postpone the migration, keep the
				// current registration alive, retry next lazyTick
			} else {
				// far end is invalid (not a conveyor / reverse connection
				// missing): align flipped with reality and stay at the current
				// anchor instead of becoming a deregistered zombie
				this.flipped = speedNegative;
				ppbe.notifyUpdate();
			}
		}

		if (this.connection == null || registerAt.connections.contains(this.connection)) {
			String portFilter = ppbe.getFilterString();
			if (portFilter != null) {
				registerAt.routingTable.receivePortInfo(portFilter, this.connection == null ? BlockPos.ZERO : this.connection);
				Map<BlockPos, ConnectedPort> portMap = this.connection == null ? registerAt.loopPorts : registerAt.travelPorts;
				portMap.put(self.relativePos.multiply(-1), new ConnectedPort(this.chainPos, this.connection, portFilter));
			}
		}
	}
}
