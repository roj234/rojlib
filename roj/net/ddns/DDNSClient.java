package roj.net.ddns;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.YAMLParser;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.io.IOUtil;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:41
 */
public class DDNSClient implements Runnable {
	public static void main(String[] args) throws Exception {
		CMapping cfg = YAMLParser.parses(IOUtil.readUTF(new File("D:\\Desktop\\DDNS.yaml")));
		new DDNSClient(cfg).run();
	}

	private boolean hasV6;
	private final IpGetter ip;
	private final DDNSService ddns;
	private final ToIntMap<String> monitorIps;

	private boolean running = true;

	public DDNSClient(CMapping cfg) throws Exception {
		ip = (IpGetter) Class.forName(cfg.getString("GetIp")).newInstance();
		ip.loadConfig(cfg);

		ddns = (DDNSService) Class.forName(cfg.getString("DDNS")).newInstance();
		ddns.loadConfig(cfg);

		hasV6 = hasIpV6Adapter();

		Map<String, List<String>> sites = new MyHashMap<>();
		monitorIps = new ToIntMap<>();
		for (Map.Entry<String, CEntry> subSites : cfg.get("Sites").asMap().entrySet()) {
			CMapping map1 = subSites.getValue().asMap();
			for (Iterator<Map.Entry<String, CEntry>> itr = map1.entrySet().iterator(); itr.hasNext(); ) {
				Map.Entry<String, CEntry> entry = itr.next();
				int e = entry.getValue().asInteger();
				if ((e & 3) != 0) monitorIps.putInt(entry.getKey(), e);
				else itr.remove();
			}

			sites.put(subSites.getKey(), new SimpleList<>(map1.keySet()));
		}

		ddns.init(sites.entrySet());
	}

	public void run() {
		InetAddress[] prev = null;
		do {
			// another url http://ipv6.pingipv6.com/ip/
			InetAddress[] address = ip.getAddress(hasV6);
			if (!Arrays.equals(prev, address)) {
				prev = address;

				List<Map.Entry<String, InetAddress[]>> changes = new SimpleList<>(monitorIps.size());
				for (ToIntMap.Entry<String> entry : monitorIps.selfEntrySet()) {
					InetAddress[] addr1 = new InetAddress[2];
					if ((entry.v & 1) == 0) addr1[0] = address[0];
					if ((entry.v & 2) == 0 && hasV6) addr1[1] = address[1];

					// both null
					if (addr1[0] == addr1[1]) continue;

					changes.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), addr1));
				}

				try {
					ddns.update(changes);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			try {
				Thread.sleep(300000);
			} catch (InterruptedException e) {
				running = false;
			}
		} while (running);
	}

	public void stop()  {
		running = false;
	}

	private static boolean hasIpV6Adapter() throws SocketException {
		Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();
		while (itfs.hasMoreElements()) {
			NetworkInterface itf = itfs.nextElement();
			if (!itf.isUp() || itf.isLoopback() || itf.isPointToPoint() || itf.isVirtual()) continue;

			Enumeration<InetAddress> addrs = itf.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if (!(addr instanceof Inet4Address)) continue; // maybe check IPv6
				return true;
			}
		}
		return false;
	}
}
