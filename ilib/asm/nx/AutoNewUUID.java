package ilib.asm.nx;

import ilib.asm.Loader;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class AutoNewUUID extends WorldServer {
	public AutoNewUUID() {
		super(null, null, null, 0, null);
	}

	@Shadow
	private Map<UUID, Entity> entitiesByUuid;

	@Inject("/")
	public void loadEntities(Collection<Entity> entities) {
		for (Entity entity : entities) {
			if (canAddEntity(entity) && !MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, this))) {
				loadedEntityList.add(entity);
				onEntityAdded(entity);
			}
		}
	}

	@Inject
	private boolean canAddEntity(Entity entity) {
		if (entity.isDead) {
			Loader.logger.warn("尝试添加死了的实体 {}", EntityList.getKey(entity));
			return false;
		}

		UUID uuid = entity.getUniqueID();
		Entity prevEnt = entitiesByUuid.get(uuid);
		if (prevEnt != null) {
			// ? compatibility with Pokemon
			if (prevEnt == entity) return false;
			if (!unloadedEntityList.remove(prevEnt)) {
				if (!(entity instanceof EntityPlayer)) {
					Loader.logger.warn("实体 {} 的全局标识 {} 重复,生成一个新的", EntityList.getKey(prevEnt), uuid.toString());

					do {
						uuid = MathHelper.getRandomUUID();
					} while (entitiesByUuid.containsKey(uuid));

					entity.setUniqueId(uuid);
					Loader.logger.warn("新的全局标识是 {}", uuid.toString());
					return true;
				}

				Loader.logger.warn("玩家的全局标识{}重复,删除原先的{}", uuid.toString(), EntityList.getKey(prevEnt));
			}

			removeEntityDangerously(prevEnt);
		}

		return true;
	}
}