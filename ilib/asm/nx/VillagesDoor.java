package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.World;

/**
 * @author solo6975
 * @since 2022/3/31 19:17
 */
@Nixim("net.minecraft.village.VillageCollection")
class VillagesDoor {
	@Shadow("field_75556_a")
	World world;
	@Shadow("field_75553_e")
	int tickCounter;

	@Inject("func_180609_b")
	private void addDoorsAround(BlockPos central) {
		if (this.world.isAreaLoaded(central, 16)) {
			for (int x = -16; x < 16; ++x) {
				for (int y = -4; y < 4; ++y) {
					for (int z = -16; z < 16; ++z) {
						BlockPos pos = central.add(x, y, z);
						if (world.isBlockLoaded(pos) && isWoodDoor(pos)) {
							VillageDoorInfo info = checkDoorExistence(pos);
							if (info == null) {
								addToNewDoorsList(pos);
							} else {
								info.setLastActivityTimestamp(tickCounter);
							}
						}
					}
				}
			}

		}
	}

	@Shadow("func_176058_f")
	private boolean isWoodDoor(BlockPos pos) {
		return false;
	}

	@Shadow("func_176055_c")
	private VillageDoorInfo checkDoorExistence(BlockPos pos) {
		return null;
	}

	@Shadow("func_176059_d")
	private void addToNewDoorsList(BlockPos pos) {}
}
