package roj.net.cross.server;

import roj.io.IOUtil;
import roj.net.ch.Pipe;
import roj.util.ByteList;

import java.io.IOException;
import java.util.function.Consumer;

import static roj.net.cross.Constants.P___CHANNEL_CLOSED;

/**
 * @author Roj233
 * @since 2022/1/24 3:20
 */
public final class PipeInfo implements Consumer<Pipe> {
	public Consumer<Pipe> host_wait;

	int sessionId, hostId, clientId;

	Pipe pipe;

	Host host;
	Client client;

	long timeout;

	int connected;

	public void close(Connection from, String reason) throws IOException {
		if (pipe == null) return;
		Pipe p = this.pipe;
		// noinspection all
		if (p == null) return;
		this.pipe = null;

		if (connected == 7) {
			System.out.println("管道 #"+hashCode()+" 终止: "+from+"|"+reason);
		}

		ByteList b = IOUtil.getSharedByteBuf();

		if (from != client) {
			synchronized (client.pipes) { client.pipes.remove(clientId); }
			client.writeAsync(b.put(P___CHANNEL_CLOSED).putInt(clientId).putVUIGB(reason));
			b.clear();
		}

		if (from != host) {
			synchronized (host.pipes) { host.pipes.remove(hostId); }
			host.writeAsync(b.put(P___CHANNEL_CLOSED).putInt(hostId).putVUIGB(reason));
		}

		p.close();
	}

	@Override
	public void accept(Pipe selectable) {
		try {
			close(null, "broken");
		} catch (IOException ignored) {}
	}
}
