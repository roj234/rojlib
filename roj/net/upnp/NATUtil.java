package roj.net.upnp;

import roj.collect.MyHashMap;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gateway Helper
 *
 * @author solo6975
 * @link https://blog.csdn.net/kejiazhw/article/details/9134333
 * @since 2022/1/15 17:36
 */
public class NATUtil {
	private static UPnPDevice gateway;
	private static UPnPDevice.Service service;
	private static List<UPnPDevice> gateways;

	public static UPnPDevice getGateway() {
		return gateway;
	}

	public static UPnPDevice.Service getGatewayService() {
		return service;
	}

	public static void setGateway(UPnPDevice device) {
		List<String> types = Arrays.asList("urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1");
		List<UPnPDevice.Service> services = device.getServices(types);
		if (services.isEmpty()) throw new IllegalArgumentException("No specified service available");
		service = services.get(0);
		gateway = device;
	}

	public static List<UPnPDevice> getGateways() {
		return gateways;
	}

	static {init();}

	public static void init() {
		if (gateways == null) {
			try {
				List<String> types = Arrays.asList(
					"urn:schemas-upnp-org:device:InternetGatewayDevice:1",
					"urn:schemas-upnp-org:service:WANIPConnection:1",
					"urn:schemas-upnp-org:service:WANPPPConnection:1");
				gateways = UPnPDevice.discover(types, true).get();
				if (!gateways.isEmpty()) {
					setGateway(gateways.get(0));
				}
			} catch (SocketException e) {
				System.err.println("无法找到网关");
				e.printStackTrace();
				gateways = Collections.emptyList();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		char PORT = 3388;

		System.out.println("正在等待初始化...");
		if (NATUtil.available()) {
			if (NATUtil.isMapped(PORT, true)) {
				System.out.println("UPnP 端口已映射，准备关闭");
				Thread.sleep(2000);
				System.out.println("关闭成功: " + NATUtil.closePort(PORT, true));
				System.exit(0);
			} else if (NATUtil.openPort("Roj234/AbyssalEye", PORT, PORT, true, 43200000)) {
				System.out.println("UPnP 映射完毕");
				System.out.println("本地IP: " + NATUtil.getLocalIP());
				System.out.println("外部IP: " + NATUtil.getExternalIP());
				System.out.println("网关IP: " + NATUtil.getGatewayIP());
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

	public static boolean available() {
		return gateway != null;
	}

	public static boolean openPort(String desc, char inPort, char outPort, boolean tcp, int durationMs) throws Exception {
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

	public static boolean closePort(char outPort, boolean tcp) throws Exception {
		Map<String, String> p = new MyHashMap<>();
		p.put("NewRemoteHost", "");
		p.put("NewProtocol", tcp ? "TCP" : "UDP");
		p.put("NewExternalPort", Integer.toString(outPort));
		Map<String, String> r = service.send("DeletePortMapping", p);
		return !r.containsKey("errorCode");
	}

	public static boolean isMapped(char outPort, boolean tcp) throws Exception {
		Map<String, String> p = new MyHashMap<>();
		p.put("NewRemoteHost", "");
		p.put("NewProtocol", tcp ? "TCP" : "UDP");
		p.put("NewExternalPort", Integer.toString(outPort));
		Map<String, String> r = service.send("GetSpecificPortMappingEntry", p);
		return !r.containsKey("errorCode") && r.containsKey("NewInternalPort");
	}

	public static String getExternalIP() throws Exception {
		Map<String, String> r = service.send("GetExternalIPAddress", null);
		return r.get("NewExternalIPAddress");
	}

	public static String getLocalIP() {
		return gateway.getLocalIp();
	}

	public static String getGatewayIP() {
		return gateway.getDeviceIp();
	}
}
