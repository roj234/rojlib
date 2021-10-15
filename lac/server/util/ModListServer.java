package lac.server.util;

import io.netty.buffer.ByteBuf;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.text.CharList;
import roj.text.crypt.Base64;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;

import java.util.Map;

/**
 * ModListServer
 *
 * @author Roj233
 * @since 2021/7/8 23:38
 */
@Nixim("net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage$ModList")
public class ModListServer extends FMLHandshakeMessage {
    @Shadow("modTags")
    private Map<String, String> modTags;

    @Inject("fromBytes")
    public void fromBytes(ByteBuf buffer) {
        int modCount = ByteBufUtils.readVarInt(buffer, 2);

        CharList out = new CharList();
        for(int i = 0; i < modCount; ++i) {
            String k = Base64.decode(ByteBufUtils.readUTF8String(buffer), out, EncodeUtil.BBREV).toString();
            out.clear();
            this.modTags.put(k, Base64.decode(ByteBufUtils.readUTF8String(buffer), out, EncodeUtil.BBREV).toString());
        }
    }
}