package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes re-connecting two chain conveyors converge to a symmetric state.
 *
 * <p>Vanilla's {@code applySettings} adds the connection on both ends in two
 * steps and treats {@code addConnectionTo} returning false (connection
 * already present on that side) as a hard failure: it either aborts before
 * the second side is added, or rolls the first side back. Once a connection is
 * one-sided (e.g. from an unload race), re-connecting can therefore NEVER
 * repair it in one or the other click order - the only workaround left to
 * players is breaking the conveyor block entirely and re-placing it.
 *
 * <p>This redirect makes {@code addConnectionTo}'s "false" (= already
 * connected) a success: both sides always end up listing each other.
 * Disconnecting is untouched and stays symmetric.
 */
@Mixin(value = ChainConveyorConnectionPacket.class, remap = false)
public abstract class ConnectionPacketHealMixin {

	@Redirect(
			method = "applySettings",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addConnectionTo(Lnet/minecraft/core/BlockPos;)Z"
			)
	)
	private boolean chainhealer$treatAlreadyConnectedAsSuccess(ChainConveyorBlockEntity receiver, BlockPos target) {
		// addConnectionTo is a no-op that still refreshes kinetic state when the
		// connection is already present; its false return value is the bug trigger.
		receiver.addConnectionTo(target);
		return true;
	}
}
