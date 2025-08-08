package roj.plugins.ddns;

import org.jetbrains.annotations.Nullable;
import roj.concurrent.TimerTask;
import roj.concurrent.Timer;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.plugins.bittorrent.Client;
import roj.plugins.bittorrent.Session;
import roj.plugins.bittorrent.Torrent;
import roj.plugins.bittorrent.Tracker;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2025/06/08 16:27
 */
class BT extends Client implements IpMapper {
	private Session session;
	private Inet4Address addr4;
	private Inet6Address addr6;
	private TimerTask task;

	@Override
	public void init(CMap config) {
		Torrent torrent;
		try {
			torrent = Torrent.read(new File(config.getString("torrent")));
		} catch (IOException | ParseException e) {
			throw new RuntimeException(e);
		}

		Session session = addTask(torrent);

		for (String[] trackers : torrent.announce_list) {

		}

		session.addTracker(Tracker.udpTracker("udp://exodus.desync.com:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://retracker.hotplug.ru:2710/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.cyberia.is:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.dler.com:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.filemail.com:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.opentrackr.org:1337/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.srv00.com:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://v74853.hosted-by-vdsina.com:6969/announce"));
		session.addTracker(Tracker.udpTracker("udp://tracker.torrent.eu.org:451/announce"));

		session.setSpeedLimit(speedLimiter);
		session.setAllowUpload(false);
		session.setAllowDownload(false);

		session.start();
		task = Timer.getDefault().loop(session::notifyTrackers, 60000);
	}

	@Override
	public void update(@Nullable InetAddress addr4, @Nullable InetAddress addr6) {
		this.addr4 = (Inet4Address) addr4;
		this.addr6 = (Inet6Address) addr6;
	}

	@Override
	public @Nullable Inet4Address getIpV4() {
		return this.addr4;
	}

	@Override
	public @Nullable Inet6Address getIpV6() {
		return this.addr6;
	}

	@Override
	public void close() {
		if (session != null) session.stop();
		if (task != null) task.cancel();
	}
}
