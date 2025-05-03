package roj.plugins.minecraft.server.event;

import roj.asmx.event.Cancellable;
import roj.asmx.event.Event;
import roj.plugins.minecraft.server.data.PlayerEntity;

/**
 * @author Roj234
 * @since 2024/3/22 2:42
 */
@Cancellable
public class PlayerMoveEvent extends Event {
	public final PlayerEntity player;
	public double x, y, z;
	public float yaw, pitch;
	public boolean jump;
	public double moveDistance;

	public PlayerMoveEvent(PlayerEntity player, double x, double y, double z, float yaw, float pitch, boolean jump, double moveDistance) {
		this.player = player;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.jump = jump;
		this.moveDistance = moveDistance;
	}
}