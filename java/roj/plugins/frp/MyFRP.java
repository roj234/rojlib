/*
 * Created by JFormDesigner on Thu Nov 02 01:20:45 CST 2023
 */

package roj.plugins.frp;

import roj.collect.MyHashMap;
import roj.config.ConfigMaster;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.crypt.Blake3;
import roj.crypt.KeyType;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.*;
import roj.net.mss.MSSKeyPair;
import roj.text.TextUtil;
import roj.text.logging.LoggingStream;
import roj.util.HighResolutionTimer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author Roj234
 */
public class MyFRP implements ChannelHandler {
	static IAEClient client;
	static final SelectorLoop loop = new SelectorLoop("AE 网络IO", 1, 30000, 1);

	static KeyType keyType = KeyType.getInstance("EdDSA");
	static byte[] userId;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		/*if (client instanceof AEClient c) {
			System.out.println("MOTD: "+c.roomMotd);

			var m = model;
			System.out.println("udpPortMap="+c.udpPortMap);
			if (m.size() != c.portMap.length) {
				m.clear();
				char[] map = c.portMap;
				for (int i = 0; i < map.length; i++) {
					var entry = new PortMapEntry(map[i], null);
					entry.udp = i >= c.udpPortMap;
					m.addElement(entry);
				}

				for (int i = 0; i < model.size(); i++) {
					var p = model.get(i);
					p.name = null;
					c.portMap[i] = (char) p.to;
				}
			} else {
				for (int i = 0; i < m.size(); i++) {
					var entry = m.get(i);
					entry.from = c.portMap[i];
					entry.udp = i >= c.udpPortMap;
				}
			}

			int errorOn = c.portMapChanged();
			if (errorOn >= 0) throw new IOException("编号为"+model.get(errorOn)+"的监听端口被占用！");
		}*/
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		//if (AEServer.server != null) AEServer.server.shutdown();
		System.out.println("很遗憾，今日屠龙宝刀已送完");
		System.exit(-1);
	}

	public static void main(String[] args) throws Exception {
		System.out.println("程序版本: "+Constants.PROTOCOL_VERSION);
		assert NIOUtil.available();

		PrintStream o = System.out;

		System.setOut(new LoggingStream() {
			@Override
			protected synchronized void newLine() {
				String s = sb.toString();
				o.println(s);
				//logBuffer.ringAddLast(s);
				sb.clear();
			}
		});

		o.println("    ___    __                          ________         ");
		o.println("   /   |  / /_  __  ________________ _/ / ____/_  _____ ");
		o.println("  / /| | / __ \\/ / / / ___/ ___/ __ `/ / __/ / / / / _ \\");
		o.println(" / ___ |/ /_/ / /_/ (__  |__  ) /_/ / / /___/ /_/ /  __/");
		o.println("/_/  |_/_.___/\\__, /____/____/\\__,_/_/_____/\\__, /\\___/ ");
		o.println("             /____/                        /____/      ");
		o.println(" —— Version " + Constants.PROTOCOL_VERSION);
		o.println();

		new MyFRP().start(new File(args[0]));
		HighResolutionTimer.activate();
		Constants.registerShutdownHook(loop);
	}

	private void start(File file) throws Exception {
		CMap yml = ConfigMaster.fromExtension(file).parse(file).asMap();

		var userCert = new KeyPair((PublicKey) keyType.fromPEM(yml.getString("public_key")), (PrivateKey) keyType.fromPEM(yml.getString("private_key")));
		String digest = TextUtil.bytes2hex(userId = new Blake3(32).digest(userCert.getPublic().getEncoded()));
		System.out.println("证书指纹: "+digest);

		MSSKeyPair key = new MSSKeyPair(userCert);
		IAEClient.client_factory.key(key);

		String room = yml.getString("room");

		ClientLaunch launch = ClientLaunch.tcp();
		InetSocketAddress address = NetUtil.parseConnectAddress(yml.getString("server"));
		System.out.println("正在连接"+address.getAddress()+", 端口"+address.getPort());
		launch.loop(loop).connect(address);

		CList list = yml.getList("ports");
		/*if (yml.getBool("room_owner")) {
			var h = new AEHost(loop);
			client = h;

			for (int i = 0; i < list.size(); i++) {
				CMap map = list.getMap(i);
				PortMapEntry c = new PortMapEntry((char) map.getInteger("port"), map.getString("hint", null));
				c.udp = map.getBool("udp");
				model.addElement(c);
			}

			CMap whitelist = yml.getMap("whitelist");
			if (whitelist.size() > 0) {
				var userWhiteList = h.whitelist = new MyHashMap<>(whitelist.size());
				userWhiteList.setHasher(Hasher.array(byte[].class));
				for (Map.Entry<String, CEntry> entry : whitelist.raw().entrySet()) {
					String string = entry.getValue().asString();
					if (string.length() != 64) System.err.println(string+"不是有效的Blake3-32指纹");
					userWhiteList.put(TextUtil.hex2bytes(string), entry.getKey());
				}
			}

			char[] ports = new char[model.size()];
			int udpOffset = 0;

			for (int i = 0; i < model.size(); i++) {
				PortMapEntry entry = model.get(i);
				if (!entry.udp) ports[udpOffset++] = (char) entry.from;
			}
			System.out.println("udp offset="+udpOffset);

			for (int i = 0, j = udpOffset; i < model.size(); i++) {
				PortMapEntry entry = model.get(i);
				if (entry.udp) ports[j++] = (char) entry.from;
			}

			var motd = yml.getString("room_desc");
			if (yml.getBool("local_server")) {
				AEServer server = new AEServer(launch.address(), 512, key);
				server.userWhiteList = h.whitelist;
				server.launch.loop(loop);
				server.start();
				AEServer.localUserId = userId;

				h.init(null, room, motd, ports, udpOffset);
				MyChannel ch = client.handlers;
				ch.addLast("open_check", this);
				ch.open();
			} else {
				h.init(launch, room, motd, ports, udpOffset);
			}

		} else {
			client = new AEClient(loop);
			for (int i = 0; i < list.size(); i++) {
				var map = list.getMap(i);
				var c = new PortMapEntry((char) map.getInteger("port"), map.getString("hint", null));
				if (map.containsKey("local_port")) c.to = map.getInteger("local_port");
				model.addElement(c);
			}

			((AEClient) client).init(launch, yml.getString("nickname"), room);
		}*/


		launch.channel().addLast("open_check", this);
		launch.launch();

		if (yml.getBool("webui")) runServer(1500);
	}

	private DefaultListModel<PortMapEntry> model = new DefaultListModel<>();

	private static final MyHashMap<String, String> res = new MyHashMap<>();
	private static String res(String name) throws IOException {
		String v = res.get(name);
		if (v == null) res.put(name, v = IOUtil.getTextResource("META-INF/html/"+name));
		return v;
	}
	private static void runServer(int port) throws IOException {
		/*HttpServer11.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (request, rh) -> {
			AEHost host = (AEHost) client;
			switch (request.path()) {
				case "bundle.min.css": return new StringResponse(res("bundle.min.css"), "text/css");
				case "bundle.min.js": return new StringResponse(res("bundle.min.js"), "text/javascript");
				case "": return new StringResponse(res("client_owner.html"), "text/html");
				case "ws":// return man.switchToWebsocket(request, rh);
					CList lx = new CList();
					for (IntMap.Entry<AEHost.Client> entry : host.clients.selfEntrySet()) {
						CMap map = new CMap();
						map.put("id", entry.getIntKey());
						map.put("ip", entry.getValue().addr);
						map.put("time", entry.getValue().time);
						CList pipes = map.getOrCreateList("pipes");
						for (Pipe2 pipe : host.pipes) {
//							PipeInfoClient att = (PipeInfoClient) pipe.att;
*//*							if (att.clientId == entry.getIntKey()) {
								CMap map1 = new CMap();
								map1.put("up", pipe.downloaded);
								map1.put("down", pipe.uploaded);
								map1.put("idle", pipe.idleTime);
								map1.put("id", att.pipeId);
								map1.put("port", host.portMap[att.portId]);
								pipes.add(map1);
							}*//*
						}
						lx.add(map);
					}
					return new StringResponse(ConfigMaster.JSON.toString(lx, new CharList()), "application/json");
				case "kick_user":
					int count = 0;
					String[] arr = request.PostFields().get("users").split(",");
					int[] arrs = new int[arr.length];
					for (int i = 0; i < arr.length; i++) {
						arrs[i] = Integer.parseInt(arr[i]);
					}
					host.kickSome(arrs);
					return new StringResponse("{\"count\":"+arr.length+"}", "application/json");
			}
			return null;
		}).launch();*/
	}
}