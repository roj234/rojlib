package ilib.asm.nx;

import com.google.common.collect.Lists;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Remap Collision
 *
 * @author solo6975
 * @since 2021/7/14 14:04
 */
@Nixim("/")
abstract class NxWorldColl extends World {
	protected NxWorldColl(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
		super(saveHandlerIn, info, providerIn, profilerIn, client);
	}

	@Override
	@Inject("/")
	public List<AxisAlignedBB> getCollisionBoxes(@Nullable Entity entityIn, AxisAlignedBB aabb) {
		List<AxisAlignedBB> list = Lists.newArrayList();
		// 不使用这个shadow, 可以让一切方块穿透
		this.shadowfunc_191504_a(entityIn, aabb, false, list);

		MinecraftForge.EVENT_BUS.post(new GetCollisionBoxesEvent(this, entityIn, aabb, list));
		return list;
	}

	@Shadow("func_191504_a")
	private boolean shadowfunc_191504_a(Entity a, AxisAlignedBB c, boolean b, List<AxisAlignedBB> d) {
		return false;
	}
}
