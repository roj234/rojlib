package roj.net.upnp;

import roj.collect.MyHashMap;
import roj.concurrent.TaskExecutor;
import roj.concurrent.task.AsyncTask;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.data.Element;
import roj.config.data.Node;
import roj.io.IOUtil;
import roj.net.http.Headers;
import roj.net.http.HttpHead;
import roj.net.http.HttpRequest;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/1/15 15:49
 */
public final class UPnPDevice {
	private final InetAddress localIp, deviceIp;

	private String discoveredBy, unvSerial;
	private Map<String, String> deviceInfo;
	private int TTL;
	private List<Service> services;

	public List<Service> getServices() {
		return services;
	}

	public List<Service> getServices(Collection<String> typeFilter) {
		ArrayList<Service> services1 = new ArrayList<>();
		for (int i = 0; i < services.size(); i++) {
			Service service = services.get(i);
			if (typeFilter.contains(service.serviceType)) {
				services1.add(service);
			}
		}
		return services1;
	}

	public String getDeviceIp() {return deviceIp.getHostAddress();}

	public String getLocalIp() {return localIp.getHostAddress();}

	@Override
	public String toString() {
		return "UPnPDevice{" + "localIp=" + localIp + ", deviceIp=" + deviceIp + ", discoveredBy='" + discoveredBy + '\'' + ", USN='" + unvSerial + '\'' + ", deviceInfo=" + deviceInfo + ", TTL=" + TTL + ", services=" + services + '}';
	}

	UPnPDevice(InetAddress sendTo, InetAddress rcvFrom) {
		this.localIp = sendTo;
		this.deviceIp = rcvFrom;
	}

	public static UPnPDevice discover(List<String> deviceTypes, InetAddress addr) {
		if (addr instanceof Inet4Address) {
			List<UPnPDevice> devices = new ArrayList<>(1);
			Discoverer4 task = new Discoverer4(addr, deviceTypes, devices);
			task.execute();
			try {
				task.get();
			} catch (InterruptedException | ExecutionException e) {
				System.err.println("Failed to discover " + addr);
				e.printStackTrace();
			}
			return devices.isEmpty() ? null : devices.get(0);
		} else {
			return null;
		}
	}

	public static Supplier<List<UPnPDevice>> discover(List<String> deviceTypes, boolean await) throws SocketException {
		TaskExecutor pool = new TaskExecutor();

		List<UPnPDevice> devices = new ArrayList<>();
		List<Discoverer4> tasks = new ArrayList<>();
		Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();
		while (itfs.hasMoreElements()) {
			NetworkInterface itf = itfs.nextElement();
			if (!itf.isUp() || itf.isLoopback() || itf.isPointToPoint() || itf.isVirtual()) continue;

			Enumeration<InetAddress> addrs = itf.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if (!(addr instanceof Inet4Address)) continue; // maybe check IPv6?
				Discoverer4 task = new Discoverer4(addr, deviceTypes, devices);
				tasks.add(task);
				pool.pushTask(task);
			}
		}

		// todo use ChannelCtx
		Supplier<List<UPnPDevice>> su = () -> {
			for (int i = 0; i < tasks.size(); i++) {
				try {
					tasks.get(i).get();
				} catch (InterruptedException ignored) {
					i--;
				} catch (ExecutionException e) {
					System.err.println("Failed to discover " + tasks.get(i).addr);
					e.printStackTrace();
				}
			}
			pool.shutdown();
			tasks.clear();
			return devices;
		};
		if (await) su.get();
		return su;
	}

	static Thread multicastListener;

	public static synchronized void listenMulticast(DeviceHandler handler) {
		if (multicastListener != null) throw new IllegalStateException("Already listening");
		multicastListener = new MulticastListener(handler);
	}

	static class Discoverer4 extends AsyncTask<List<UPnPDevice>> {
		static final int TIMEOUT = 2;
		static final String SEARCH_PREFIX = "M-SEARCH * HTTP/1.1\r\n" + "HOST: 239.255.255.250:1900\r\n" + "ST: ";
		static final String SEARCH_POSTFIX = "\r\n" + "MAN: \"ssdp:discover\"\r\n" + "MX: " + TIMEOUT + "\r\n" + "USER-AGENT: JavaUPnP/Roj234 1.1\r\n" + "\r\n";

		final InetAddress addr;
		final List<byte[]> requests;
		final List<UPnPDevice> devices;

		public Discoverer4(InetAddress addr, List<String> types, List<UPnPDevice> devices) {
			this.addr = addr;
			this.requests = new ArrayList<>(types.size());

			UTFCoder coder = IOUtil.SharedCoder.get();
			coder.charBuf.clear();
			for (int i = 0; i < types.size(); i++) {
				String s = types.get(i);
				coder.charBuf.append(SEARCH_PREFIX).append(UPnPUtil.addNamespace(s)).append(SEARCH_POSTFIX);
				requests.add(coder.encode());
			}
			this.devices = devices;
		}

		@Override
		public void execute() {
			executing = true;

			ByteList bl = IOUtil.getSharedByteBuf();
			bl.ensureCapacity(UPnPUtil.MTU);
			DatagramPacket pkt = new DatagramPacket(bl.list, UPnPUtil.MTU);

			try (DatagramSocket rcv = new DatagramSocket(0, addr)) {
				rcv.setSoTimeout(TIMEOUT * 1000);

				for (int i = 0; i < requests.size(); i++) {
					byte[] req = requests.get(i);
					rcv.send(new DatagramPacket(req, req.length, UPnPUtil.UPNP_ADDRESS));
					try {
						rcv.receive(pkt);
						UPnPDevice device = new UPnPDevice(addr, pkt.getAddress());

						bl.wIndex(pkt.getLength());
						HttpHead head = HttpHead.parse(bl);
						String error = init(head, device);
						if (error == null) {
							synchronized (devices) {
								devices.add(device);
							}
						} else {
							System.err.println("出错了: " + error);
						}
						// 一个就够了，cfg地址总不会不一样吧，都是一个设备...
						break;
					} catch (SocketTimeoutException ignored) {
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			} catch (Throwable e) {
				exception = new ExecutionException(e);
			}
			out = devices;
			executing = false;

			synchronized (this) {
				notifyAll();
			}
		}
	}

	static String init(Headers headers, UPnPDevice device) throws Exception {
		String loc = headers.get("Location");
		if (null == loc) return "没有 'Location' 头";

		device.discoveredBy = headers.get("St");
		device.unvSerial = headers.get("Usn");
		String tmp = headers.get("Cache-Control");
		if (tmp == null) {
			device.TTL = -1;
		} else {
			List<String> cache = TextUtil.split(new ArrayList<>(), tmp, ',');
			for (int i = 0; i < cache.size(); i++) {
				String s = cache.get(i).trim().toLowerCase();
				if (s.equals("no-cache")) {
					device.TTL = -1;
					break;
				} else if (s.startsWith("max-age=")) {
					device.TTL = Integer.parseInt(s.substring(8));
				}
			}
		}

		HttpRequest query = HttpRequest.nts().url(new URL(loc)).header("Connection", "close");

		int slash = loc.indexOf('/', 7);
		if (slash >= 0) loc = loc.substring(0, slash);

		// todo should move up ?
		String loc1 = loc;

		List<Service> services = device.services = new ArrayList<>();
		Element xml = XMLParser.parses(query.execute().str());
		List<Element> xmlServices = xml.getElementsByTagName("service");
		for (int i = 0; i < xmlServices.size(); i++) {
			Element xServ = xmlServices.get(i);
			Service srv = new Service();
			srv.serviceType = xServ.element("serviceType").textContent();
			srv.serviceId = xServ.element("serviceId").textContent();

			srv.controlURL = getURL(loc1, "controlURL", xServ);
			srv.eventSubURL = getURL(loc1, "eventSubURL", xServ);
			srv.SCPDURL = getURL(loc1, "SCPDURL", xServ); // Service Descriptor

			services.add(srv);
		}

		Map<String, String> deviceInfo = new MyHashMap<>(8);
		List<Node> xDevice = xml.querySelector(".root.device").children();
		for (int i = 0; i < xDevice.size(); i++) {
			Node xDesc = xDevice.get(i);
			if (xDesc.size() > 0) {
				if (xDesc.child(0).nodeType() == Node.ELEMENT) {
					deviceInfo.put(xDesc.asElement().tag, xDesc.child(0).textContent().trim());
				}
			}
		}
		device.deviceInfo = deviceInfo;
		return null;
	}

	private static String getURL(String base, String name, Element xml) {
		String url = xml.element(name).textContent();
		return !url.startsWith("/") ? base + "/" + url : base + url;
	}

	public static final class Service {
		String serviceType, serviceId, controlURL, eventSubURL, SCPDURL;

		Service() {}

		public String getServiceType() {
			return serviceType;
		}

		public String getServiceId() {
			return serviceId;
		}

		public String getControlURL() {
			return controlURL;
		}

		public String getEventSubURL() {
			return eventSubURL;
		}

		public String getSCPDURL() {
			return SCPDURL;
		}

		public Map<String, String> send(String action, Map<String, String> params) throws IOException, ParseException {
			StringBuilder sb = new StringBuilder(
				"<?xml version=\"1.0\"?><SOAP-ENV:Envelope " + "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"" + "http://schemas.xmlsoap.org/soap/encoding/\"><SOAP-ENV:Body><m:" + action + " xmlns:m=\"" + serviceType + "\">");

			if (params != null) {
				for (Map.Entry<String, String> entry : params.entrySet()) {
					sb.append("<").append(entry.getKey()).append(">").append(entry.getValue()).append("</").append(entry.getKey()).append(">");
				}
			}
			sb.append("</m:").append(action).append("></SOAP-ENV:Body></SOAP-ENV:Envelope>");
			ByteList data = IOUtil.getSharedByteBuf().putUTFData(sb);

			HttpRequest query = HttpRequest.nts()
				.url(new URL(controlURL))
				.header("Content-Type", "text/xml")
				.header("SOAPAction", "\"" + serviceType + "#" + action + "\"")
				.header("Connection", "Close")
				.header("Content-Length", String.valueOf(data.wIndex()))
				.body(new ByteList(data.toByteArray()));

			Element header = XMLParser.parses(query.execute().bytes(), XMLParser.LENIENT);

			Element elm = header.child(0).child(0).child(0).asElement();
			if (!elm.tag.startsWith(action, 2)) {
				if (!elm.tag.equals("s:Fault")) {
					throw new IOException("无效响应类型 " + elm.tag);
				} else {
					//    <faultcode>s:Client</faultcode>
					//    <faultstring>UPnPError</faultstring>
					//    <detail>
					//        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
					//            <errorCode>501</errorCode>
					//            <errorDescription>Action Failed</errorDescription>
					//        </UPnPError>
					//    </detail>
					List<Node> child = elm.children();
					for (int i = 0; i < child.size(); i++) {
						Element xKey = child.get(i).asElement();
						if (xKey.tag.equals("detail")) {
							child = xKey.child(0).children();
							break;
						}
					}
					Map<String, String> ret = new MyHashMap<>(child.size());
					for (int i = 0; i < child.size(); i++) {
						Element xKey = child.get(i).asElement();
						ret.put(xKey.tag, xKey.child(0).textContent().trim());
					}
					return ret;
				}
			}

			List<Node> child = elm.children();
			Map<String, String> ret = new MyHashMap<>(child.size());
			for (int i = 0; i < child.size(); i++) {
				Element xKey = child.get(i).asElement();
				ret.put(xKey.tag, xKey.size() == 0 ? "" : xKey.child(0).textContent().trim());
			}
			return ret;
		}

		@Override
		public String toString() {
			return "Service{type='" + serviceType + "'}";
		}
	}
}
