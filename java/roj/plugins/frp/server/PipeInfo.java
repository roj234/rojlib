package roj.plugins.frp.server;

import org.jetbrains.annotations.Nullable;
import roj.net.ch.MyChannel;
import roj.net.ch.Pipe;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/1/24 3:20
 */
public final class PipeInfo implements Consumer<Pipe> {
	@Nullable
	Pipe pipe;

	Host host;
	Client client;
	MyChannel connection;

	long timeout;

	public void close(Connection from, String reason) throws IOException {
		Pipe p = pipe;
		if (p != null) p.close();
		pipe = null;

		System.out.println("管道 #"+hashCode()+" 终止: "+from+"|"+reason);

		synchronized (client) { client.pipes.remove(this); }
		synchronized (host) { host.pipes.remove(this); }
	}

	@Override
	public void accept(Pipe selectable) {
		try {
			close(null, "broken");
		} catch (IOException ignored) {}
	}

	public boolean isClosed() {return pipe != null && (pipe.isDownstreamEof() || pipe.isUpstreamEof() || pipe.idleTime > 120000);}
}