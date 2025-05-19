package roj.plugins.bittorrent;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.Promise;
import roj.concurrent.TaskPool;
import roj.net.*;
import roj.net.util.SpeedLimiter;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会话 (下载任务)
 * @author Roj234
 * @since 2024/11/29 3:15
 */
class Session {
	public final Client owner;
	public final Torrent.Info torrent;
	public final File[] files;
	public final byte[] peerId = new byte[20];

	private static String[] EventName = {"empty", "completed", "started", "stopped"};
	private int newEvent;

	private long uploaded, downloaded, selected;
	private List<Tracker> trackers = new SimpleList<>();

	public Session(Client owner, Torrent torrent) {
		this.owner = owner;
		this.torrent = torrent.info;
		this.files = new File[torrent.info.fileCount()];
		this.selected = torrent.info.getSize();
		ThreadLocalRandom.current().nextBytes(peerId);
	}

	public void addPeer(@Nullable String peerId, InetSocketAddress address) {
		System.out.println("peerId = " + peerId + ", address = " + address);
	}

	public boolean isDirty() {
		return newEvent != 0;
	}

	public void start() {
		this.newEvent = 2;
		notifyTrackers();
	}
	public void stop() {
		this.newEvent = 3;
		notifyTrackers();
	}

	private void notifyTrackers() {
		List<Promise<?>> result = new SimpleList<>();
		for (Tracker tracker : trackers) {
			result.add(tracker.update(this));
		}

		Promise.all(TaskPool.Common(), result).thenRun(() -> {
			this.newEvent = 0;
			System.out.println("all done");
		});
	}

	List<Map.Entry<String, String>> getHttpParameter() {
		List<Map.Entry<String, String>> list = new SimpleList<>();
		//event, 可选, 该参数的值可以是 started, completed, stopped, empty 其中的一个, 该参数的值为 empty 与该参数不存在是等价的, 当开始下载时, 该参数的值设置为 started, 当下载完成时, 该参数的值设置为 completed, 当下载停止时, 该参数的值设置为 stopped
		if (newEvent > 0) list.add(new SimpleEntry<>("event", EventName[newEvent]));
		//info_hash, 20 字节, 将 .torrent 文件中的 info 键对应的值生成的 SHA1 哈希, 该哈希值可作为所要请求的资源的标识符
		list.add(new SimpleEntry<>("info_hash", new String(torrent.getInfoHash(), StandardCharsets.ISO_8859_1)));
		//peer_id, 终端生成的 20 个字符的唯一标识符, 每个进行 BT 下载的终端随机生成的 20 个字符的字符串作为其标识符 (终端应在每次开始一个新的下载任务时重新随机生成一个新的 peer_id)
		list.add(new SimpleEntry<>("peer_id", new String(peerId, StandardCharsets.ISO_8859_1)));
		//IP (可选), 该终端的 IP 地址, 一般情况下该参数没有必要, 因为传输层 (Transport Layer, 如 TCP) 本身可以获取 IP 地址, 但比如 BT 下载器通过 Proxy 与 Tracker 交互时, 该在该字段中设置源端的真实 IP
		//list.add(new SimpleEntry<>("IP", "123.45.67.89"));
		//Port, 该终端正在监听的端口 (因为 BT 协议是 P2P 的, 所以每一个下载终端也都会暴露一个端口, 供其它结点下载), BT 下载器首先尝试监听 6881 端口, 若端口被占用被继续尝试监听 6882 端口, 若仍被占用则继续监听 6883, 6884 ... 直到 6889 端口, 若以上所有端口都被占用了, 则放弃尝试
		list.add(new SimpleEntry<>("Port", "12345"));
		//uploaded, 当前已经上传的文件的字节数 (十进制数字表示)
		list.add(new SimpleEntry<>("uploaded", Long.toString(uploaded)));
		//downloaded, 当前已经下载的文件的字节数 (十进制数字表示)
		list.add(new SimpleEntry<>("downloaded", Long.toString(downloaded)));
		//left, 当前仍需要下载的文件的字节数 (十进制数字表示)
		list.add(new SimpleEntry<>("left", Long.toString(selected - downloaded)));
		//numwant, 可选, 希望 BT Tracker 返回的 peer 数目, 若不填, 默认返回 50 个 IP 和 Port
		//list.add(new SimpleEntry<>("numwant", "50"));
		return list;
	}

	void getUdpParameter(DynByteBuf out) {
		Inet4Address ipV4 = owner.getIpV4();
		out.put(torrent.getInfoHash()).put(peerId).putLong(downloaded).putLong(selected - downloaded).putLong(uploaded)
		   .putInt(newEvent).put(ipV4 == null ? new byte[4] : ipV4.getAddress()).put(peerId, 0, 4).putInt(-1).putShort(owner.getPort());
	}

	interface UDPHandler {
		DynByteBuf next(DynByteBuf buf) throws IOException;
	}

	private static class UDPDispatcher implements ChannelHandler {
		Map<InetSocketAddress, UDPHandler> addressMap = new MyHashMap<>();

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			InetSocketAddress key = new InetSocketAddress(pkt.addr, pkt.port);
			UDPHandler channelHandler = addressMap.get(key);
			if (channelHandler != null) {
				if (null == (pkt.buf = channelHandler.next(pkt.buf))) {
					addressMap.remove(key);
				} else {
					if (pkt.buf.isReadable()) ctx.channelWrite(pkt);
				}
			}
		}
	}

	private MyChannel channel;
	private UDPDispatcher dispatcher;
	void registerUdpHandler(InetSocketAddress address, UDPHandler handler) throws IOException {
		if (channel == null) {
			dispatcher = new UDPDispatcher();
			ServerLaunch udp = ServerLaunch.udp();
			channel = udp.udpCh().addLast("addressed dispatcher", dispatcher);
			udp.launch();
		}
		dispatcher.addressMap.putIfAbsent(address, handler);
		DynByteBuf next = handler.next(null);
		DatagramPkt pkt = new DatagramPkt(address, next);
		channel.fireChannelWrite(pkt);
	}

	public void setSpeedLimit(SpeedLimiter speedLimiter) {

	}

	public void setAllowUpload(boolean b) {

	}

	public void setAllowDownload(boolean b) {

	}

	public boolean isCompleted() {
		return false;
	}

	public void addTracker(Tracker tracker) {
		trackers.add(tracker);
	}

	public List<Tracker> getTrackers() {
		return trackers;
	}

}
