package ilib.net.packet;

import ilib.ClientProxy;
import ilib.api.Ownable;
import ilib.api.Syncable;
import ilib.net.IMessage;
import ilib.net.IMessageHandler;
import ilib.net.MessageContext;
import ilib.tile.FieldSyncer;
import ilib.tile.OwnerManager;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MsgSyncFields implements IMessage, IMessageHandler<MsgSyncFields> {
    int[] fields;
    BlockPos pos;
    byte type;

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
        }
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeByte((byte) fields.length);
        for (int value : fields) {
            buf.writeVarInt(value);
        }
        buf.writeVarInt(pos.getX())
           .writeVarInt(pos.getY())
           .writeVarInt(pos.getZ());
        buf.writeByte(type);
    }

    static boolean isUsableByPlayer(TileEntity te, EntityPlayerMP player) {
        BlockPos pos = te.getPos();
        double Max_Distance = 8.0 * 8.0;

        return player.getDistanceSq(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) < Max_Distance;
    }

    @Override
    public void onMessage(MsgSyncFields msg, MessageContext ctx) {
        if (ctx.side == Side.SERVER) {
            EntityPlayerMP p = ctx.getServerHandler().player;

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

            FieldSyncer fs = ((Syncable) t).getSyncHandler();
            fs.setFields(msg.fields, FieldSyncer.CLIENT);
        } else {
            onClient(msg);
        }
    }

    @SideOnly(Side.CLIENT)
    private void onClient(MsgSyncFields msg) {
        TileEntity tile = ClientProxy.mc.world.getTileEntity(msg.pos);
        if (!(tile instanceof Syncable)) {
            ClientProxy.mc.player.sendMessage(new TextComponentTranslation("ilib.illegal_packet.1"));
            return;
        }

        FieldSyncer fs = ((Syncable) tile).getSyncHandler();
        fs.setFields(msg.fields, msg.type);
    }
}