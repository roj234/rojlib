package roj.net.p2p;

import roj.collect.MyHashMap;
import roj.text.logging.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gateway Helper
 * @since 2022/1/15 17:36
 */
public class UPnPGateway {
	private static UPnPDevice gateway;
	private static UPnPDevice.Service service;
	private static List<UPnPDevice> gateways;

	public static List<UPnPDevice> getGateways() { return gateways; }
	public static boolean available() {
		if (gateways == null) {
			try {
				List<String> types = Arrays.asList(
					"urn:schemas-upnp-org:device:InternetGatewayDevice:1",
					"urn:schemas-upnp-org:service:WANIPConnection:1",
					"urn:schemas-upnp-org:service:WANPPPConnection:1");
				gateways = UPnPDevice.discover(types).get();
				if (!gateways.isEmpty())
					setGateway(gateways.get(0));
			} catch (Exception e) {
				Logger.getLogger("P2P").error("无法找到可用的UPnP网关设备", e);
				gateways = Collections.emptyList();
			}
		}
		return !gateways.isEmpty();
	}

	public static UPnPDevice getGateway() { return gateway; }
	public static UPnPDevice.Service getGatewayService() { return service; }
	public static void setGateway(UPnPDevice device) {
		List<String> types = Arrays.asList("urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1");
		List<UPnPDevice.Service> services = device.getServices(types);
		if (services.isEmpty()) throw new IllegalArgumentException("No specified service available");
		service = services.get(0);
		gateway = device;
	}

	public static void main(String[] args) throws Exception {
		char PORT = 20000;

		System.out.println("正在初始化...");
		if (UPnPGateway.available()) {
			if (UPnPGateway.isMapped(PORT, true)) {
				System.out.println("UPnP 端口已映射，准备关闭");
				Thread.sleep(2000);
				System.out.println("关闭成功: " + UPnPGateway.closePort(PORT, true));
				System.exit(0);
			} else if (UPnPGateway.openPort("Test", PORT, PORT, true, 600000)) {
				System.out.println("UPnP 映射完毕");
				System.out.println("本地IP: " + UPnPGateway.getLocalIP());
				System.out.println("外部IP: " + UPnPGateway.getExternalIP());
				System.out.println("网关IP: " + UPnPGateway.getGatewayIP());
			} else {
				System.out.println("UPnP 映射失败");
			}
		} else {
			System.out.println("UPnP 不可用");
		}

		final ServerSocket ss = new ServerSocket(PORT);
		while (!Thread.interrupted()) {
			Socket s = ss.accept();
			System.out.println("Connect: " + s.getInetAddress().getHostAddress());
			s.close();
		}
	}

	public static boolean openPort(String desc, int inPort, int outPort, boolean tcp, int durationMs) throws IOException {
		Map<String, String> p = new MyHashMap<>();
		p.put("NewRemoteHost", "");
		p.put("NewProtocol", tcp ? "TCP" : "UDP");
		p.put("NewInternalClient", gateway.getLocalIp());
		p.put("NewInternalPort", Integer.toString(inPort));
		p.put("NewExternalPort", Integer.toString(outPort));
		p.put("NewEnabled", "1");
		p.put("NewPortMappingDescription", desc);
		p.put("NewLeaseDuration", durationMs <= 0 ? "0" : Integer.toString(durationMs));
		Map<String, String> r = service.send("AddPortMapping", p);
		return !r.containsKey("errorCode") && !r.containsKey("NewPortMappingDescription");
	}

	public static boolean closePort(int outPort, boolean tcp) throws IOException {
		Map<String, String> p = new MyHashMap<>();
		p.put("NewRemoteHost", "");
		p.put("NewProtocol", tcp ? "TCP" : "UDP");
		p.put("NewExternalPort", Integer.toString(outPort));
		Map<String, String> r = service.send("DeletePortMapping", p);
		return !r.containsKey("errorCode");
	}

	public static boolean isMapped(int outPort, boolean tcp) throws IOException {
		Map<String, String> p = new MyHashMap<>();
		p.put("NewRemoteHost", "");
		p.put("NewProtocol", tcp ? "TCP" : "UDP");
		p.put("NewExternalPort", Integer.toString(outPort));
		Map<String, String> r = service.send("GetSpecificPortMappingEntry", p);
		return !r.containsKey("errorCode") && r.containsKey("NewInternalPort");
	}

	public static String getExternalIP() throws IOException {
		Map<String, String> r = service.send("GetExternalIPAddress", null);
		return r.get("NewExternalIPAddress");
	}

	public static String getLocalIP() { return gateway.getLocalIp(); }
	public static String getGatewayIP() { return gateway.getDeviceIp(); }
}