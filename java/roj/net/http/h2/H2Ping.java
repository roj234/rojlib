package roj.net.http.h2;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/8 0008 4:23
 */
public final class H2Ping {
	public final long sendTime, nonce;
	public final Consumer<H2Ping> callback;
	public long recvTime;

	public H2Ping(Consumer<H2Ping> callback) {
		this.nonce = ThreadLocalRandom.current().nextLong();
		this.sendTime = System.currentTimeMillis();
		this.callback = callback;
	}

	public int getRTT() {return (int) (recvTime - sendTime);}
}