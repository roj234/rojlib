package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.World;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/3/31 19:17
 */
@Nixim("net.minecraft.village.Village")
class VillageDoor extends Village {
	@Shadow("field_75586_a")
	World world;
	@Shadow("field_75584_b")
	List<VillageDoorInfo> villageDoorInfoList;
	@Shadow("field_75581_g")
	int tickCounter;

	@Inject("func_75557_k")
	private void removeDeadAndOutOfRangeDoors() {
		boolean flag1 = world.rand.nextInt(50) == 0;

		List<VillageDoorInfo> infos = villageDoorInfoList;
		for (int i = infos.size() - 1; i >= 0; i--) {
			VillageDoorInfo info = infos.get(i);
			if (flag1) {
				info.resetDoorOpeningRestrictionCounter();
			}

			if (!world.isBlockLoaded(info.getDoorBlockPos()) || !isWoodDoor(info.getDoorBlockPos()) || Math.abs(tickCounter - info.getLastActivityTimestamp()) > 1200) {
				info.setIsDetachedFromVillageFlag(true);
				infos.remove(i);
			}
		}
	}

	@Shadow("func_179860_f")
	private boolean isWoodDoor(BlockPos pos) {
		return false;
	}
}
