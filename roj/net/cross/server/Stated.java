package roj.net.cross.server;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:18
 */
abstract class Stated implements ChannelHandler {
	static void unknownPacket(Client self, DynByteBuf rb) throws IOException {
		int bc = (rb.get(0) & 0xFF) - 0x20;
		if (rb.readableBytes() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
			print(self + ": 错误 " + ERROR_NAMES[bc]);
		} else {
			print(self + ": 未知数据包: " + rb);
			ChannelCtx.bite(self.handler, (byte) PS_ERROR_UNKNOWN_PACKET);
		}
	}

	static boolean isInRoom(Client t) throws IOException {
		Room room = t.room;
		if (room != null) {
			if (room.master == null) {
				ChannelCtx.bite(t.handler, (byte) PS_ERROR_MASTER_DIE);
				return false;
			} else if (!room.clients.containsKey(t.clientId)) {
				ChannelCtx.bite(t.handler, (byte) PS_ERROR_KICKED);
				return false;
			}
		}
		return true;
	}


	static String getUTF(DynByteBuf buf, int len) {
		return buf.readUTF(len);
	}
}
