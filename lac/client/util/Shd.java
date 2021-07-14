package lac.client.util;

import io.netty.buffer.ByteBuf;
import lac.server.note.DefaultObfuscatePolicy;

import java.nio.charset.StandardCharsets;

@DefaultObfuscatePolicy(onlyHaveStatic = true)
public class Shd {
    public static void writeVarInt(ByteBuf to, int i) {
        while((i & -128) != 0) {
            to.writeByte(i & 127 | 128);
            i >>>= 7;
        }

        to.writeByte(i);
    }

    public static int readVarInt(ByteBuf buf) {
        int i = 0;
        int j = 0;

        byte b0;
        do {
            b0 = buf.readByte();
            i |= (b0 & 127) << j++ * 7;
        } while((b0 & 128) == 128);

        return i;
    }

    public static void writeUTF8String(ByteBuf to, String string) {
        byte[] utf8Bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(to, utf8Bytes.length);
        to.writeBytes(utf8Bytes);
    }

    public static String readUTF8String(ByteBuf from) {
        int len = readVarInt(from);
        String str = from.toString(from.readerIndex(), len, StandardCharsets.UTF_8);
        from.readerIndex(from.readerIndex() + len);
        return str;
    }
}
