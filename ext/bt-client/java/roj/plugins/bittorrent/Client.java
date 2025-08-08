package roj.plugins.bittorrent;

import org.jetbrains.annotations.Nullable;
import roj.collect.HashMap;
import roj.net.util.SpeedLimiter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/11/29 3:17
 */
public class Client {
	protected Map<Torrent, Session> tasks = new HashMap<>();
	protected SpeedLimiter.Setting setting = new SpeedLimiter.Setting(1024, 60000, 1048576, 10485760);
	protected SpeedLimiter speedLimiter = new SpeedLimiter(setting);

	public SpeedLimiter getSpeedLimiter() {
		return speedLimiter;
	}

	public Session addTask(Torrent torrent) {
		return tasks.computeIfAbsent(torrent, torrent1 -> new Session(this, torrent1));
	}

	@Nullable public Inet4Address getIpV4() {return null;}
	@Nullable public Inet6Address getIpV6() {return null;}
	public int getPort() {return 12345;}
}
