package ilib.asm.nx.client;

import ilib.client.KeyRegister;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.CPacketInput;
import net.minecraft.network.play.client.CPacketPlayer.Rotation;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/17 13:02
 */
@Nixim("net.minecraft.client.entity.EntityPlayerSP")
class SeparateDismount extends EntityPlayerSP {
	public SeparateDismount(Minecraft a, World b, NetHandlerPlayClient c, StatisticsManager d, RecipeBook e) {
		super(a, b, c, d, e);
	}

	@Shadow("func_175161_p")
	private void onUpdateWalkingPlayer() {}

	@Shadow(value = "func_70071_h_", owner = "net.minecraft.entity.player.EntityPlayer")
	private void super_onUpdate() {}

	@Inject(value = "func_70071_h_", at = At.REPLACE)
	public void onUpdate() {
		if (world.isChunkGeneratedAt((int) (posX + 0.5) >> 4, (int) (posZ + 0.5) >> 4)) {
			super_onUpdate();
			if (isRiding()) {
				connection.sendPacket(new Rotation(rotationYaw, rotationPitch, onGround));
				connection.sendPacket(new CPacketInput(moveStrafing, moveForward, movementInput.jump, KeyRegister.keyDismount.isKeyDown()));
				Entity entity = this.getLowestRidingEntity();
				if (entity != this && entity.canPassengerSteer()) {
					connection.sendPacket(new CPacketVehicleMove(entity));
				}
			} else {
				onUpdateWalkingPlayer();
			}
		}

	}
}
