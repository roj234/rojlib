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
import net.minecraft.inventory.Container;
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
public class MsgSyncField implements IMessage, IMessageHandler<MsgSyncField> {
    public int key, val;

    public int wid;
    public BlockPos pos;

    public MsgSyncField() {}

    public MsgSyncField(int key, int val) {
        this.key = key;
        this.val = val;
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        key = buf.readVarInt();
        val = buf.readVarInt();
        int w = buf.readVarInt();
        if (buf.isReadable()) {
            pos = new BlockPos(w, buf.readVarInt(), buf.readVarInt());
        } else {
            wid = w;
        }
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeVarInt(key).writeVarInt(val);
        if (pos != null) {
            buf.writeVarInt(pos.getX()).writeVarInt(pos.getY()).writeVarInt(pos.getZ());
        } else {
            buf.writeVarInt(wid);
        }
    }

    @Override
    public void onMessage(MsgSyncField msg, MessageContext ctx) {
        if (ctx.side == Side.SERVER) {
            EntityPlayerMP p = ctx.getServerHandler().player;

            TileEntity t = p.world.getTileEntity(msg.pos);
            if (!(t instanceof Syncable)) {
                p.sendMessage(new TextComponentTranslation("ilib.illegal_packet.1"));
                return;
            }

            if (!MsgSyncFields.isUsableByPlayer(t, p)) {
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
            fs.setField(msg.key, msg.val, FieldSyncer.CLIENT);
        } else {
            onClient(msg);
        }
    }

    @SideOnly(Side.CLIENT)
    private void onClient(MsgSyncField msg) {
        Container con = ClientProxy.mc.player.openContainer;
        if (con.windowId == wid) con.updateProgressBar(msg.key, msg.val);
    }
}