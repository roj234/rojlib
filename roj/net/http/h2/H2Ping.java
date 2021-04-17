package roj.net.http.h2;

/**
 * @author Roj234
 * @since 2022/10/8 0008 4:23
 */
public class H2Ping {
	long sendTime, recvTime;

	boolean state;

	public boolean isReceived() {
		return state;
	}

	public int getRTT() {
		return (int) (recvTime - sendTime);
	}
}
