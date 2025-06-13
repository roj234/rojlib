package roj.plugins.ddns;

import roj.collect.ArrayList;
import roj.concurrent.ScheduleTask;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.net.Net;
import roj.plugin.Plugin;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/28 1:41
 */
public class DDNSClient extends Plugin {
	private boolean hasV6;
	private IpGetter ip;
	private List<IpMapper> services;

	private ScheduleTask task;

	@Override
	protected void onEnable() throws Exception {
		CMap cfg = getConfig();

		hasV6 = Net.isIPv6();
		ip = (IpGetter) Class.forName(cfg.getString("GetIp")).newInstance();
		ip.loadConfig(cfg);

		services = new ArrayList<>();

		List<CEntry> list = cfg.getList("Services").raw();

		for (int i = 0; i < list.size(); i++) {
			var service = list.get(i).asMap();
			if (!service.getBool("Enable", true)) continue;

			var instance = (IpMapper) Class.forName(service.getString("Type")).newInstance();
			instance.init(service);
			services.add(instance);
		}

		task = getScheduler().loop(this::run, cfg.getInt("Interval", 5) * 60000L);
	}

	@Override
	protected void onDisable() {
		if (task != null) task.cancel();
		for (var service : services) service.close();
	}

	private InetAddress[] prev = null;
	public void run() {
		// another url http://ipv6.pingipv6.com/ip/
		InetAddress[] address;
		try {
			address = ip.getAddress(hasV6);
		} catch (Exception e) {
			getLogger().error("无法获取IP", e);
			return;
		}

		if (!Arrays.equals(prev, address)) {
			prev = address;

			for (IpMapper service : services) {
				try {
					service.update(address[0], address[1]);
				} catch (Exception e) {
					getLogger().error(e);
				}
			}
		}
	}
}