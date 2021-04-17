package ilib.net.packet;

import ilib.ImpLib;
import ilib.api.Ownable;
import ilib.api.Syncable;
import ilib.net.IMessage;
import ilib.net.IMessageHandler;
import ilib.net.MessageContext;
import ilib.tile.FieldSyncer;
import ilib.tile.OwnerManager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.concurrent.Callable;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MsgSyncFields implements IMessage, IMessageHandler<MsgSyncFields>, Callable<Void> {
	private FieldSyncer owner;
	private int timer;

	private int[] fields;
	private BlockPos pos;
	private byte type;

	public MsgSyncFields() {}

	public MsgSyncFields(FieldSyncer syncer, BlockPos pos, byte type) {
		this.fields = syncer.getFields(type);
		this.pos = pos;
		this.type = type;
	}

	@Override
	public void fromBytes(PacketBuffer buf) {
		int length = buf.readByte() & 0xFF;
		fields = new int[length];
		for (int i = 0; i < length; i++) {
			fields[i] = buf.readVarInt();
		}
		pos = new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		if (buf.isReadable()) {
			type = buf.readByte();
		} else {
			type = 0;
		}
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeByte((byte) fields.length);
		for (int value : fields) {
			buf.writeVarInt(value);
		}
		buf.writeVarInt(pos.getX()).writeVarInt(pos.getY()).writeVarInt(pos.getZ());
		if (type != 0) buf.writeByte(type);
	}

	static boolean isUsableByPlayer(TileEntity te, EntityPlayer player) {
		BlockPos pos = te.getPos();
		double Max_Distance = 8.0 * 8.0;

		return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < Max_Distance;
	}

	@Override
	public void onMessage(MsgSyncFields msg, MessageContext ctx) {
		if (ctx.side == Side.SERVER) {
			EntityPlayerMP p = ctx.getServerHandler().player;

			if (!p.world.isBlockLoaded(msg.pos)) {
				p.connection.disconnect(new TextComponentTranslation("ilib.illegal_packet.0"));
				return;
			}

			TileEntity t = p.world.getTileEntity(msg.pos);
			if (!(t instanceof Syncable)) {
				p.sendMessage(new TextComponentTranslation("ilib.illegal_packet.1"));
				return;
			}

			if (!isUsableByPlayer(t, p)) {
				p.sendMessage(new TextComponentTranslation("ilib.illegal_packet.2"));
				return;
			}

			if (t instanceof Ownable) {
				Ownable t1 = (Ownable) t;
				OwnerManager om = t1.getOwnerManager();
				if (om != null && !om.isTrusted(p, t1.getOwnType())) {
					p.sendMessage(new TextComponentTranslation("ilib.illegal_packet.3"));
					return;
				}
			}

			owner = ((Syncable) t).getSyncHandler();
		}
		ImpLib.proxy.runAtMainThread(ctx.side == Side.CLIENT, this);
	}

	@SideOnly(Side.CLIENT)
	private void onClient() {
		TileEntity tile = mc.world.getTileEntity(pos);
		if (!(tile instanceof Syncable)) {
			if (!mc.world.isBlockLoaded(pos)) {
				if (timer++ < 10) {
					mc.addScheduledTask(this);
				}
				return;
			}
			mc.player.sendMessage(new TextComponentTranslation("ilib.illegal_packet.1"));
			return;
		}

		((Syncable) tile).getSyncHandler().setFields(fields, type);
	}

	@Override
	public Void call() {
		if (owner == null) {
			onClient();
		} else {
			owner.setFields(fields, type);
		}
		return null;
	}
}