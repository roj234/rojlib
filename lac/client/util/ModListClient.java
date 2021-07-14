package lac.client.util;

import io.netty.buffer.ByteBuf;
import lac.client.LACClient;
import lac.server.note.ClassInject;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ModListClient
 *
 * @author Roj233
 * @since 2021/7/8 23:38
 */
@ClassInject(dest = "net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage$ModList")
public class ModListClient extends FMLHandshakeMessage {
    private Map<String, String> modTags = new HashMap<>();

    // injector define

    public ModListClient() {
        if(MyBase64.TABLEDEC == null) {
            MyBase64.TABLEDEC = new byte[MyBase64.TABLEENC.length];
            MyBase64.reverseOf(MyBase64.TABLEENC, MyBase64.TABLEDEC);
        }
    }

    public ModListClient(List<ModContainer> modList) {
        ByteList bl = new ByteList();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < modList.size(); i++) {
            ModContainer mod = modList.get(i);
            ByteWriter.writeUTF(bl, mod.getModId(), -1);
            String a = MyBase64.encode(bl, out, MyBase64.TABLEENC).toString();
            bl.clear();
            out.delete(0, out.length());
            ByteWriter.writeUTF(bl, mod.getVersion(), -1);
            String b = MyBase64.encode(bl, out, MyBase64.TABLEENC).toString();
            bl.clear();
            out.delete(0, out.length());
            this.modTags.put(a, b);
        }
        for (int i = 0; i < modList.size(); i++) {
            // write md5
        }
    }

    public void toBytes(ByteBuf buffer) {
        super.toBytes(buffer);
        Shd.writeVarInt(buffer, this.modTags.size() << 1);
        for (Map.Entry<String, String> modTag : this.modTags.entrySet()) {
            Shd.writeUTF8String(buffer, modTag.getKey());
            Shd.writeUTF8String(buffer, modTag.getValue());
        }
    }

    public void fromBytes(ByteBuf buf) {
        int modCount = Shd.readVarInt(buf);

        for(int i = 0; i < modCount; ++i) {
            this.modTags.put(Shd.readUTF8String(buf), Shd.readUTF8String(buf));
        }

        if(buf.readerIndex() < buf.writerIndex()) {
            int len = Shd.readVarInt(buf);
            ArrayList<String> classChecks = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                classChecks.add(Shd.readUTF8String(buf));
            }
            LACClient.CLASS_CHECKS = classChecks;
        } else {
            toString(null);
            LACClient.CLASS_CHECKS = FMLCommonHandler.instance().getBrandings(false);
        }
    }

    public String modListAsString() {
        return "";
    }

    public int modListSize() {
        return this.modTags.size();
    }

    public Map<String, String> modList() {
        return this.modTags;
    }

    public String toString(Class<? extends Enum<?>> side) {
        return super.toString(side) + ":" + this.modTags.size() + " mods";
    }
}