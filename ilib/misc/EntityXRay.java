package ilib.misc;

import ilib.ClientProxy;
import ilib.util.ChatColor;
import ilib.util.PlayerUtil;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author solo6975
 * @since 2022/3/31 23:42
 */
public class EntityXRay {
	private static boolean enable;

	@SubscribeEvent
	public static void update(LivingEvent.LivingUpdateEvent event) {
		Entity entity = event.getEntity();
		if (!entity.getEntityWorld().isRemote) return;

		NBTTagCompound tag = entity.getEntityData();
		if (enable) {
			if (!tag.hasKey("\u0000")) {
				tag.setBoolean("\u0000", entity.isGlowing());
				tag.setBoolean("\u0001", entity.getAlwaysRenderNameTag());

				if (null == entity.getCustomNameTag()) {
					tag.setBoolean("\u0002", true);
					entity.setCustomNameTag(entity.getName());
				}
			}

			entity.setGlowing(true);
			entity.setAlwaysRenderNameTag(true);
		} else {
			if (tag.hasKey("\u0000")) {
				entity.setGlowing(tag.getBoolean("\u0000"));
				entity.setAlwaysRenderNameTag(tag.getBoolean("\u0001"));
				if (tag.getBoolean("\u0002")) entity.setCustomNameTag(null);
				tag.removeTag("\u0000");
				tag.removeTag("\u0001");
				tag.removeTag("\u0002");
			}
		}
	}

	public static void toggle() {
		MinecraftForge.EVENT_BUS.register(EntityXRay.class);
		enable = !enable;
		PlayerUtil.sendTo(ClientProxy.mc.player, ChatColor.GREY + "当前状态: " + ChatColor.ORANGE + enable);
	}
}
