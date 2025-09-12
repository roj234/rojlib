package roj.plugins.p2p;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.concurrent.TaskPool;
import roj.config.XmlParser;
import roj.config.node.xml.Element;
import roj.config.node.xml.Node;
import roj.http.HttpHead;
import roj.http.HttpRequest;
import roj.io.CorruptedInputException;
import roj.net.*;
import roj.net.handler.Timeout;
import roj.text.ParseException;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

	public List<Service> getServices() { return services; }
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

	public String getDeviceIp() { return deviceIp.getHostAddress(); }
	public String getLocalIp() { return localIp.getHostAddress(); }

	@Override
	public String toString() {
		return "UPnPDevice{" + "localIp=" + localIp + ", deviceIp=" + deviceIp + ", discoveredBy='" + discoveredBy + '\'' + ", USN='" + unvSerial + '\'' + ", deviceInfo=" + deviceInfo + ", TTL=" + TTL + ", services=" + services + '}';
	}

	UPnPDevice(InetAddress sendTo, InetAddress rcvFrom) {
		this.localIp = sendTo;
		this.deviceIp = rcvFrom;
	}

	public static Supplier<List<UPnPDevice>> discover(List<String> deviceTypes) throws IOException {
		SelectorLoop loop = new SelectorLoop("UPnP discover", 1);

		List<UPnPDevice> devices = new ArrayList<>();

		List<Discoverer> tasks = new ArrayList<>();
		Enumeration<NetworkInterface> itfs = NetworkInterface.getNetworkInterfaces();
		while (itfs.hasMoreElements()) {
			NetworkInterface itf = itfs.nextElement();
			if (!itf.isUp() || itf.isLoopback() || itf.isPointToPoint() || itf.isVirtual()) continue;

			Enumeration<InetAddress> addrs = itf.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if (!(addr instanceof Inet4Address)) continue; // 已经支持ipv6，不过UPnP支持它吗？
				Discoverer task = new Discoverer(addr, deviceTypes, devices);
				ServerLaunch.udp().initializator(task).loop(loop).launch();
				tasks.add(task);
			}
		}

		return () -> {
			for (int i = 0; i < tasks.size(); i++) {
				try {
					Discoverer task = tasks.get(i);
					synchronized (task) {
						while (task.completed < task.tasks) task.wait();
					}
				} catch (InterruptedException ignored) {}
			}

			loop.close();
			return devices;
		};
	}

	static class Discoverer implements Consumer<MyChannel>, ChannelHandler {
		static final String PREFIX = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nUSER-AGENT: UPnPClient 1.1\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ";

		static final InetSocketAddress UPNP_ADDRESS = new InetSocketAddress("239.255.255.250", 1900);

		static final String XML_NS = "urn:schemas-upnp-org:";
		static final int XML_NS_LENGTH = 21;

		static String addNamespace(String s) { return s.startsWith(XML_NS) ? s : XML_NS + s; }
		static String delNamespace(String s) { return s.startsWith(XML_NS) ? s.substring(XML_NS_LENGTH) : s; }

		private static String getURL(String base, String name, Element xml) {
			String url = xml.element(name).textContent();
			return !url.startsWith("/") ? base + "/" + url : base + url;
		}

		private final InetAddress addr;
		private final List<String> requests;
		private final List<UPnPDevice> devices;

		int completed, tasks = 1;

		public Discoverer(InetAddress addr, List<String> types, List<UPnPDevice> devices) {
			this.addr = addr;
			this.requests = types;
			this.devices = devices;
		}

		@Override
		public void accept(MyChannel channel) { channel.addLast("timer", new Timeout(800)).addLast("discover", this); }

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			ByteList list = new ByteList().putAscii(PREFIX);
			int prefix = list.wIndex();
			for (int i = 0; i < requests.size(); i++) {
				list.putAscii(addNamespace(requests.get(i))).putAscii("\r\n\r\n");
				ctx.channelWrite(new DatagramPkt(UPNP_ADDRESS, list));

				list.rIndex = 0;
				list.wIndex(prefix);
			}
		}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			HttpHead head = HttpHead.parse(pkt.data);
			String loc = head.get("location");
			InetAddress addr1 = pkt.address.getAddress();
			UPnPGateway.LOGGER.debug("接收到来自{}的UPnP响应, 数据为{}", addr1, loc);
			if (null == loc) return/*"没有 'Location' 头"*/;

			UPnPDevice device;
			synchronized (devices) {
				for (UPnPDevice dev : devices) {
					if (dev.deviceIp.equals(addr1)) {
						UPnPGateway.LOGGER.debug("该数据包已存在,丢弃");
						return;
					}
				}
				device = new UPnPDevice(addr, addr1);
				devices.add(device);
			}

			device.discoveredBy = head.get("st");
			device.unvSerial = head.get("usn");
			String tmp = head.get("cache-control");
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

			List<Service> services = device.services = new ArrayList<>();
			TaskPool.common().executeUnsafe(() -> {
				UPnPGateway.LOGGER.debug("正在请求设备描述: {}", loc);
				Element xml;
				try {
					xml = XmlParser.parses(HttpRequest.builder().url(loc).header("connection", "close").execute(500).str());

					Node node = xml.querySelector("/root/URLBase");
					String loc1 = node == null ? loc.substring(0, loc.indexOf('/')) : node.textContent();

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

					Map<String, String> deviceInfo = new HashMap<>(8);
					List<Element> xDevice = xml.getElementsByTagName("device");
					for (int i = 0; i < xDevice.size(); i++) {
						Node xDesc = xDevice.get(i);
						if (xDesc.size() > 0) {
							if (xDesc.child(0).nodeType() == Node.ELEMENT) {
								deviceInfo.put(xDesc.asElement().tag, xDesc.child(0).textContent().trim());
							}
						}
					}
					device.deviceInfo = deviceInfo;
				} catch (ParseException e) {
					UPnPGateway.LOGGER.debug("设备描述请求失败: {}", e);
				} finally {
					channelClosed(null);
				}
			});
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			completed++;
			synchronized (this) { notifyAll(); }
		}
	}

	public static final class Service {
		String serviceType, serviceId, controlURL, eventSubURL, SCPDURL;

		Service() {}

		public String getServiceType() { return serviceType; }
		public String getServiceId() { return serviceId; }
		public String getControlURL() { return controlURL; }
		public String getEventSubURL() { return eventSubURL; }
		public String getSCPDURL() { return SCPDURL; }

		public Map<String, String> send(String action, Map<String, String> params) throws IOException {
			ByteList data = new ByteList()
				.putAscii("<?xml version=\"1.0\"?>" +
					"<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
					"<SOAP-ENV:Body><m:").putAscii(action).putAscii(" xmlns:m=\"").putAscii(serviceType).putAscii("\">");

			if (params != null) {
				for (Map.Entry<String, String> entry : params.entrySet()) {
					data.append("<").append(entry.getKey()).append(">").append(entry.getValue()).append("</").append(entry.getKey()).append(">");
				}
			}
			data.append("</m:").append(action).append("></SOAP-ENV:Body></SOAP-ENV:Envelope>");

			HttpRequest query = HttpRequest.builder()
				.url(controlURL)
				.header("Content-Type", "text/xml")
				.header("SOAPAction", "\""+serviceType+"#"+action+"\"")
				.header("Connection", "Close")
				.header("Content-Length", String.valueOf(data.wIndex()))
				.body(data);

			Element header;
			try {
				header = XmlParser.parses(query.execute().str());
			} catch (ParseException e) {
				throw new CorruptedInputException("invalid SOAP response", e);
			}

			Element elm = header.querySelector("/s:Envelope/s:Body/*(0)").asElement();
			List<Node> child = elm.children();
			if (!elm.tag.startsWith(action, 2)) {
				if (!elm.tag.equals("s:Fault"))
					throw new IOException("无效响应类型 "+elm.tag);

				//    <detail>
				//        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
				//            <errorCode>501</errorCode>
				//            <errorDescription>Action Failed</errorDescription>
				//        </UPnPError>
				//    </detail>
				Element detail = elm.element("detail");
				if (detail != null) child = detail.child(0).children();
			}

			Map<String, String> ret = new HashMap<>(child.size());
			for (int i = 0; i < child.size(); i++) {
				Element xKey = child.get(i).asElement();
				ret.put(xKey.tag, xKey.size() == 0 ? "" : xKey.child(0).textContent().trim());
			}
			return ret;
		}

		@Override
		public String toString() { return "Service{type='"+serviceType+"'}"; }
	}
}