package roj.plugins.minecraft.server.data;

import roj.math.MathUtils;
import roj.plugins.minecraft.server.data.world.World;
import roj.util.DynByteBuf;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/3/19 23:25
 */
public class PlayerEntity extends Entity {
	public World world;

	public final ItemStack[] inventory = new ItemStack[45];
	public ItemStack cursorStack = ItemStack.EMPTY;
	public int selectedInventory;

	public boolean isCreative = true;
	public boolean moveDisabled, collisionDisabled, flying;

	public PlayerEntity() {
		Arrays.fill(inventory, ItemStack.EMPTY);
	}

	public ItemStack getSelectedItem() {
		return inventory[selectedInventory+36];
	}

	public DynByteBuf createSpawnPacket(DynByteBuf buf) {
		// verint entityId
		// UUID uuid
		// double x,y,z
		// byte pitch, yaw
		return buf
			.putVarInt(id)
			.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
			.putDouble(x).putDouble(y).putDouble(z)
			.put(MathUtils.floor(this.pitch * 256.0F / 360.0F))
			.put(MathUtils.floor(this.yaw * 256.0F / 360.0F));
	}

	public void jump() {
		this.onGround = false;
	}

	public void moveTo(double x, double y, double z, float yaw, float pitch) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public void setWorld(World world) {
		this.world = world;
	}
}