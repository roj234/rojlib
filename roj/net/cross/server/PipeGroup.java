package roj.net.cross.server;

import roj.net.ch.Pipe;
import roj.util.ByteList;

import java.io.IOException;

import static roj.net.cross.Util.P_CHANNEL_CLOSE;
import static roj.net.cross.Util.print;

/**
 * @author Roj233
 * @since 2022/1/24 3:20
 */
public final class PipeGroup {
	long timeout;
	int id, upPass, downPass;
	Client downOwner;
	Pipe pairRef;

	// -2超时 -1连接断开 0房主关闭 1客户端关闭
	public void close(int from) throws IOException {
		if (pairRef == null) return;
		Pipe pipe = pairRef;
		// noinspection all
		if (pipe == null) return;
		pairRef = null;

		String fromStr;
		switch (from) {
			case 1:
				fromStr = "客户端";
				break;
			case 0:
				fromStr = "房主";
				break;
			case -1:
				fromStr = "连接中止";
				break;
			case -2:
				fromStr = "超时";
				break;
			default:
				fromStr = Integer.toString(from);
				break;
		}
		print("管道 #" + id + " 终止 " + fromStr);

		ByteList packet = ByteList.allocate(9);
		packet.put((byte) P_CHANNEL_CLOSE).putInt(-1).putInt(id);

		if (from != 1 && downOwner.getPipe(id) != null) {
			downOwner.closePipe(id);
			downOwner.sync(packet);
			if (downOwner.pending == this) downOwner.pending = null;
			packet.rIndex = 0;
		}

		Client upOwner = downOwner.room.master;
		if (from != 0 && upOwner.getPipe(id) != null) {
			upOwner.closePipe(id);
			upOwner.sync(packet);
		}

		pipe.close();
	}
}
