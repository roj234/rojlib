package ilib.asm.nx;

import ilib.ImpLib;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.AbstractIterator;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import net.minecraftforge.fml.common.registry.EntityRegistry;

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim("net.minecraft.entity.EntityTracker")
class TrackerAdd extends IterateAllEntities {
	TrackerAdd(WorldServer theWorldIn) {
		super(theWorldIn);
	}

	@Inject("/")
	public void track(Entity entity) {
		if (!EntityRegistry.instance().tryTrackingEntity(this, entity)) {
			ResourceLocation key = EntityList.getKey(entity);
			if (key == null) {
				if (entity instanceof EntityPlayerMP) {
					EntityPlayerMP player = (EntityPlayerMP) entity;

					AbstractIterator<EntityTrackerEntry> itr = (AbstractIterator<EntityTrackerEntry>) entries.iterator();//entriesItr;
					itr.reset();

					while (itr.hasNext()) {
						itr.next().updatePlayerEntity(player);
					}

					track(player, 512, 2);
				} else if (entity instanceof EntityFishHook) {
					track(entity, 64, 5, true);
				} else {
					ImpLib.logger().warn("Undefined track kind {}", entity.getClass().getName());
				}
				return;
			}

			if (!key.getNamespace().equals("minecraft")) {
				ImpLib.logger().warn("Undefined track kind {}", key);
				return;
			}

			switch (key.getPath()) {
				case "item":
					track(entity, 64, 20, true);
					return;
				case "egg":
				case "fireball":
				case "dragon_fireball":
				case "snowball":
				case "ender_pearl":
				case "xp_bottle":
				case "fireworks_rocket":
					track(entity, 64, 10, true);
					return;
				case "boat":
				case "minecart":
				case "chest_minecart":
				case "furnace_minecart":
				case "tnt_minecart":
				case "hopper_minecart":
				case "spawner_minecart":
				case "commandblock_minecart":
				case "shulker_bullet":
					track(entity, 80, 3, true);
					return;
				case "squid":
					track(entity, 64, 3, true);
					return;
				case "wither":
				case "bat":
					track(entity, 80, 3, false);
					return;
				case "small_fireball":
				case "llama_spit":
					track(entity, 64, 10, false);
					return;
				case "eye_of_ender_signal":
					track(entity, 64, 4, true);
					return;
				case "falling_block":
				case "xp_orb":
					track(entity, 160, 20, true);
					return;
				case "armor_stand":
				case "ender_dragon":
					track(entity, 160, 3, true);
					return;
				case "tnt":
					track(entity, 160, 10, true);
					return;
				case "area_effect_cloud":
					track(entity, 160, 2147483647, true);
					return;
				case "ender_crystal":
					track(entity, 256, 2147483647, false);
					return;
				case "evocation_fangs":
					track(entity, 160, 2, false);
					return;
			}
			if (entity instanceof EntityArrow) {
				track(entity, 64, 20, false);
			} else if (entity instanceof EntityPotion) {
				track(entity, 64, 10, true);
			} else if (entity instanceof IAnimals) {
				track(entity, 80, 3, true);
			} else if (entity instanceof EntityHanging) {
				track(entity, 160, 2147483647, false);
			}
		}
	}
}
