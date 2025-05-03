package roj.plugins.minecraft.server.data;

import roj.math.MathUtils;
import roj.util.DynByteBuf;

import java.util.UUID;

/**
 * @author Roj234
 * @since 2024/3/19 23:24
 */
public class Entity {
	public int id, type, bitset0;
	public double x, y, z;
	public double prevX, prevY, prevZ;
	public long alignX, alignY, alignZ;
	public float headYaw, yaw, pitch;
	public float prevYaw, prevPitch;
	public boolean onGround;
	public UUID uuid;

	public double velocityX, velocityY, velocityZ;

	public DynByteBuf createSpawnPacket(DynByteBuf buf) {
		// verint entityId
		// UUID uuid
		// varint entityType
		// double x,y,z
		// byte pitch, yaw, headYaw
		// varint entityData
		// short velocity[X,Y,Z]
		return buf
			.putVarInt(id)
			.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
			.putVarInt(type)
			.putDouble(x).putDouble(y).putDouble(z)
			.put(MathUtils.floor(this.pitch * 256.0F / 360.0F))
			.put(MathUtils.floor(this.yaw * 256.0F / 360.0F))
			.put(MathUtils.floor(this.headYaw * 256.0F / 360.0F))
			.putVarInt(bitset0)
			.putShort((int) (MathUtils.clamp(velocityX, -3.9, 3.9) * 8000.0))
			.putShort((int) (MathUtils.clamp(velocityY, -3.9, 3.9) * 8000.0))
			.putShort((int) (MathUtils.clamp(velocityZ, -3.9, 3.9) * 8000.0));
	}
}