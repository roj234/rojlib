package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.ForgeHooks;

/**
 * @author solo6975
 * @since 2022/5/3 1:18
 */
@Nixim("net.minecraft.network.NetHandlerPlayServer")
class NxAttackFurther extends NetHandlerPlayServer {
	@Shadow("field_147367_d")
	private MinecraftServer server;

	public NxAttackFurther() {
		super(null, null, null);
	}

	@Inject("/")
	public void processUseEntity(CPacketUseEntity packetIn) {
		WorldServer w = this.player.getServerWorld();
		PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, w);
		Entity entity = packetIn.getEntityFromWorld(w);
		player.markPlayerActive();
		if (entity != null) {
			boolean seen = player.canEntityBeSeen(entity);
			double radius;
			if (!seen) {
				radius = 9;
			} else {
				AxisAlignedBB aabb = entity.getEntityBoundingBox();
				float border = entity.getCollisionBorderSize();
				if (border != 0) {
					aabb = aabb.grow(border);
				}

				double x = (aabb.maxX - aabb.minX) * 0.5D;
				double y = (aabb.maxY - aabb.minY) * 0.5D;
				double z = (aabb.maxZ - aabb.minZ) * 0.5D;
				radius = 36 + x * x + y * y + z * z;
			}

			if (player.getDistanceSq(entity) < radius) {
				switch (packetIn.getAction().ordinal()) {
					case 0:
						player.interactOn(entity, packetIn.getHand());
						break;
					case 2:
						EnumHand hand = packetIn.getHand();
						if (ForgeHooks.onInteractEntityAt(this.player, entity, packetIn.getHitVec(), hand) != null) {
							return;
						}

						entity.applyPlayerInteraction(this.player, packetIn.getHitVec(), hand);
						break;
					case 1:
						if (entity instanceof EntityItem || entity instanceof EntityXPOrb || entity instanceof EntityArrow || entity == player) {
							disconnect(new TextComponentTranslation("multiplayer.disconnect.invalid_entity_attacked"));
							server.logWarning("玩家尝试攻击无效的实体[A] " + player.getName());
							return;
						} else if (!entity.canBeAttackedWithItem()) {
							server.logWarning("玩家尝试攻击无效的实体[B] " + player.getName());
						}

						this.player.attackTargetEntityWithCurrentItem(entity);
						break;
				}
			}
		}
	}
}
