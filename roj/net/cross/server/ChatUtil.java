package roj.net.cross.server;

import roj.net.WrappedSocket;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.ByteBuffer;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2022/1/24 3:12
 */
public class ChatUtil {
    public static final int RECENT_MESSAGE_COUNT;

    static {
        int c;
        try {
            c = Integer.parseInt(System.getProperty("AE.chat.msgCount", "1000"));
            if (c > 9999 || c < 0) c = 1000;
        } catch (Exception e) {
            c = 1000;
        }
        RECENT_MESSAGE_COUNT = c;
    }

    static UTFCoder uc;
    static void chat(Client W, WrappedSocket ch, ByteBuffer rb) throws IOException {
        int target = rb.getInt(1);
        if (target == W.clientId) return;

        Room room = W.room;
        if (target == -1) {
            boolean isLong = rb.get(0) == P_MSG_LONG;

            int pos = rb.position();
            rb.position(isLong ? 6 : 5);
            room.globalChat(isLong ? rb.getChar(6) : (rb.get(5) & 0xFF), rb);
            rb.position(pos);
            return;
        }

        Client to = room.clients.get(target);
        if (null == to || !room.chatEnabled.contains(target) || room.chatDisabled.contains(W.clientId)) {
            write1(ch, (byte) P_FAIL);
            return;
        }

        if (CHATSPY) {
            if (uc == null) {
                uc = new UTFCoder();
            }
            synchronized (uc) {
                boolean isLong = rb.get(0) == P_MSG_LONG;
                int len = isLong ? rb.getChar(6) : (rb.get(5) & 0xFF);
                ByteList buf = uc.byteBuf;
                buf.ensureCapacity(len);

                int pos = rb.position();
                rb.position(isLong ? 6 : 5);
                rb.get(buf.list).position(pos);

                buf.wIndex(len);
                syncPrint(W + ": msg => #" + to + ": " + uc.decode());
            }
        }

        rb.putInt(1, W.clientId).flip();
        to.sync(rb);
    }
}
