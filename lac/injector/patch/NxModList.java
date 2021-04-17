package lac.injector.patch;

import io.netty.buffer.ByteBuf;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;

import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;

import java.util.List;

/**
 * @author Roj233
 * @since 2021/7/8 23:38
 */
@Nixim("net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage$ModList")
class NxModList extends FMLHandshakeMessage {
    @Inject(value = "<init>", at = At.REPLACE)
    public NxModList(List<ModContainer> mods) {}

    @Inject("toBytes")
    public void toBytes(ByteBuf buf) {}

//    @Inject("fromBytes")
//    public void fromBytes(ByteBuf buf) {}
}