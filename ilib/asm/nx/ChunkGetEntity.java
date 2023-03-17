package ilib.asm.nx;

import com.google.common.base.Predicate;
import ilib.asm.util.IEntityList;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/25 2:27
 */
@Nixim("net.minecraft.world.chunk.Chunk")
class ChunkGetEntity {
	@Shadow
	private ClassInheritanceMultiMap<Entity>[] entityLists;

	@Inject
	public void getEntitiesWithinAABBForEntity(@Nullable Entity exclude, AxisAlignedBB aabb, List<Entity> listToFill, Predicate<? super Entity> filter) {
		int i = MathHelper.floor((aabb.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
		int j = MathHelper.floor((aabb.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);
		i = MathHelper.clamp(i, 0, entityLists.length - 1);
		j = MathHelper.clamp(j, 0, entityLists.length - 1);

		while (i <= j) {
			if (!entityLists[i].isEmpty()) {
				List<Entity> list = ((IEntityList) entityLists[i]).getValue();
				for (int k = 0; k < list.size(); k++) {
					Entity entity = list.get(k);
					if (!entity.getEntityBoundingBox().intersects(aabb)) continue;
					if (entity == exclude) continue;
					if (filter == null || filter.apply(entity)) {
						listToFill.add(entity);
					}

					Entity[] parts = entity.getParts();
					if (parts != null) {
						for (Entity part : parts) {
							if (part != exclude && part.getEntityBoundingBox().intersects(aabb) && (filter == null || filter.apply(part))) {
								listToFill.add(part);
							}
						}
					}
				}
			}
			i++;
		}
	}

	@Inject("")
	public <T extends Entity> void getEntitiesOfTypeWithinAABB(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> filter) {
		int i = MathHelper.floor((aabb.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
		int j = MathHelper.floor((aabb.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);
		i = MathHelper.clamp(i, 0, entityLists.length - 1);
		j = MathHelper.clamp(j, 0, entityLists.length - 1);

		while (i <= j) {
			if (!entityLists[i].isEmpty()) {
				List<? extends T> list = ((IEntityList) entityLists[i]).getValue(entityClass);
				for (int k = 0; k < list.size(); k++) {
					T entity = list.get(k);
					if (!entity.getEntityBoundingBox().intersects(aabb)) continue;
					if (filter == null || filter.apply(entity)) {
						listToFill.add(entity);
					}
				}
			}
			i++;
		}
	}

}
