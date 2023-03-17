package roj.net.cross;

/**
 * @author Roj233
 * @since 2021/12/25 17:24
 */
final class SpAttach {
	int channelId;
	byte portId;
	int clientId;

	@Override
	public String toString() {
		return "管道描述{ID=" + channelId + ", 端口ID=" + portId + ", 客户端=" + clientId + '}';
	}
}
