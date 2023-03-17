package ilib.world.saver.tag;

import ilib.net.mock.MockingUtil;
import ilib.util.DimensionHelper;
import ilib.util.EntityHelper;
import ilib.util.PlayerUtil;
import io.netty.buffer.ByteBuf;
import roj.math.Vec3d;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author solo6975
 * @since 2022/3/31 21:28
 */
public class WailaTagGetter implements ITagGetter {
	static final int WAILA_PING_ID = 0;
	static final int WAILA_TILE_ID = 1;
	static final int WAILA_ENT_ID = 2;
	static final int WAILA_DATA_ID = 3;

	static ConcurrentHashMap<Vec3d, AsyncPacket> map = new ConcurrentHashMap<>();
	public static boolean isWailaInstalled;

	public static void register() {
		MockingUtil.modInterceptor.add((packet, handler) -> {
			if (packet.channel().equals("waila")) {
				ByteBuf buf = packet.payload().duplicate();
				switch (buf.readByte() & 0xFF) {
					case WAILA_PING_ID:
						PlayerUtil.sendTo(null, "屏蔽了Waila的配置同步");
						isWailaInstalled = true;
						return true;
					case WAILA_DATA_ID:
						NBTTagCompound tag = ByteBufUtils.readTag(buf);
						if (tag == null) break;
						if (tag.hasKey("x")) {
							AsyncPacket pkt = map.remove(new Vec3d(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
							if (pkt != null) {
								PlayerUtil.sendTo(null, "结果: " + tag);
								return true;
							}
						} else if (tag.hasKey("posX")) {
							AsyncPacket pkt = map.remove(new Vec3d(tag.getDouble("posX"), tag.getDouble("posY"), tag.getDouble("posZ")));
							if (pkt != null) {
								pkt.setDone(tag);
								return true;
							}
						}
						break;
				}
			}
			return false;
		});
	}

	public WailaTagGetter() {}

	public boolean isBusy() {
		return false;
	}

	public static void forceLoadChunk(int dim, BlockPos pos) {
		PacketBuffer buf = MockingUtil.newBuffer(64);
		buf.writeByte(WAILA_TILE_ID).writeInt(dim).writeLong(pos.toLong()).writeInt(1);
		ByteBufUtils.writeUTF8String(buf, "*");

		MockingUtil.sendProxyPacket("waila", buf);

		map.put(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), new AsyncPacket());
	}

	public AsyncPacket getTag(TileEntity tile) {
		return null;
	}

	public AsyncPacket getTag(Entity entity) {
		PacketBuffer buf = MockingUtil.newBuffer(64);
		buf.writeByte(WAILA_ENT_ID).writeInt(DimensionHelper.idFor(entity.world)).writeInt(entity.getEntityId()).writeInt(1);
		ByteBufUtils.writeUTF8String(buf, "*");

		MockingUtil.sendProxyPacket("waila", buf);

		AsyncPacket pkt = new AsyncPacket();
		map.put(EntityHelper.vec(entity), pkt);
		return pkt;
	}

	@Override
	public boolean supportTile() {
		return false;
	}

	@Override
	public boolean supportEntity() {
		return true;
	}
}
