package ho.artisan.chainhealer.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-write accessor for the conveyor's package storage, so diagnostics and
 * the purge pass can inspect lanes without reflective hacks.
 *
 * <p>Note on vanilla's data shape: {@code travellingPackages} is keyed by the
 * LOCAL offset of the target conveyor. Vanilla never removes emptied lanes
 * (only {@code removeConnectionTo} does), and its pre-1.1.1
 * {@code removeInvalidConnections()} removed dead connections WITHOUT
 * dropping the packages travelling on them - those entries become invisible
 * ghosts that permanently occupy {@code canAcceptMorePackages()} capacity.
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public interface ChainConveyorInventoryAccessor {

	@Accessor(value = "loopingPackages", remap = false)
	List<ChainConveyorPackage> chainhealer$getLoopingPackages();

	@Accessor(value = "travellingPackages", remap = false)
	Map<BlockPos, List<ChainConveyorPackage>> chainhealer$getTravellingPackages();
}
