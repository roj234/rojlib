package roj.net.cross;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.Pipe;
import roj.net.cross.AEHost.Client;
import roj.net.http.srv.HttpServer11;
import roj.net.http.srv.StringResponse;
import roj.text.logging.LoggingStream;
import roj.ui.DelegatedPrintStream;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author Roj233
 * @since 2021/9/11 2:00
 */
public class AEGuiHost {
	public static void main(String[] args) throws IOException, ParseException {
		if (!NIOUtil.available()) {
			JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
			return;
		}

		String serv = null, cfgFile = "host.json";
		int webPort = -1;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-cfg":
					cfgFile = args[++i];
					break;
				case "-server":
					serv = args[++i];
					break;
				case "-webport":
					webPort = Integer.parseInt(args[++i]);
					break;
			}
		}
		CMapping cfg = JSONParser.parses(IOUtil.readUTF(new FileInputStream(cfgFile)), JSONParser.LITERAL_KEY).asMap();

		InetSocketAddress addr = NetworkUtil.getConnectAddress(serv);

		client = new AEHost(addr, cfg.getString("room"), cfg.getString("pass"));
		CList ports = cfg.getOrCreateList("ports");
		char[] chars = new char[ports.size()];
		if (chars.length == 0) System.err.println("ports.length is 0");
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) ports.get(i).asInteger();
		}
		client.setPortMap(chars);
		client.motd = cfg.getString("motd");
		client.setDaemon(false);
		client.start();

		if (webPort != -1) {
			try {
				runServer(webPort);
			} catch (BindException e) {
				System.err.println("HTTP端口(" + webPort + ")已被占用: " + e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		System.out.println("登录中......");
		if (cfg.getBool("log")) {
			Util.out = new LoggingStream();
		} else {
			Util.out = new DelegatedPrintStream(1);
		}
		System.setOut(Util.out);
		System.setErr(Util.out);

		Util.registerShutdownHook(client);
	}

	static MyHashMap<String, String> tmp = new MyHashMap<>();

	private static String res(String name) throws IOException {
		String v = tmp.get(name);
		if (v == null) tmp.put(name, v = IOUtil.readUTF("META-INF/html/" + name));
		return v;
	}

	private static void runServer(int port) throws IOException {
		HttpServer11.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (request, rh) -> {
			switch (request.path()) {
				case "/bundle.min.css":
					return new StringResponse(res("bundle.min.css"), "text/css");
				case "/bundle.min.js":
					return new StringResponse(res("bundle.min.js"), "text/javascript");
				case "/":
					return new StringResponse(res("client_owner.html"), "text/html");
				case "/user_list":
					CList lx = new CList();
					for (IntMap.Entry<Client> entry : client.clients.selfEntrySet()) {
						CMapping map = new CMapping();
						map.put("id", entry.getIntKey());
						map.put("ip", entry.getValue().addr);
						map.put("time", entry.getValue().connect);
						CList pipes = map.getOrCreateList("pipes");
						for (Pipe pipe : client.socketsById.values()) {
							SpAttach att = (SpAttach) pipe.att;
							if (att.clientId == entry.getIntKey()) {
								CMapping map1 = new CMapping();
								map1.put("up", pipe.downloaded);
								map1.put("down", pipe.uploaded);
								map1.put("idle", pipe.idleTime);
								map1.put("id", att.channelId);
								map1.put("port", client.portMap[att.portId]);
								pipes.add(map1);
							}
						}
						lx.add(map);
					}
					return new StringResponse(lx.toShortJSONb(), "application/json");
				case "/kick_user":
					int count = 0;
					String[] arr = request.postFields().get("users").split(",");
					int[] arrs = new int[arr.length];
					for (int i = 0; i < arr.length; i++) {
						arrs[i] = Integer.parseInt(arr[i]);
					}
					client.kickSome(arrs);
					return new StringResponse("{\"count\":" + arr.length + "}", "application/json");
			}
			return null;
		}).launch();
	}

	static AEHost client;
}
