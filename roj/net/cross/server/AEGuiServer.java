package roj.net.cross.server;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.crypt.KeyFile;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.SelectorLoop;
import roj.net.cross.Util;
import roj.net.mss.JPrivateKey;
import roj.net.mss.SimpleEngineFactory;
import roj.text.logging.LogContext;
import roj.text.logging.Logger;
import roj.text.logging.LoggingStream;
import roj.text.logging.d.LogDestination;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

/**
 * AbyssalEye Server GUI
 *
 * @author Roj233
 * @version 40
 * @since 2021/9/11 12:49
 */
public class AEGuiServer {
	static AEServer server;
	static RingBuffer<String> logBuffer = new RingBuffer<>(1000, false);

	public static void main(String[] args) throws GeneralSecurityException, IOException {
		if (!NIOUtil.available()) {
			JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
			return;
		}

		byte[] keyPass = null;
		String port = null, motd = "任务：拯救世界(1/1)";
		int webPort = -1, maxUsers = 100;
		boolean log = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-consolelog":
					log = true;
					break;
				case "-maxconn":
					maxUsers = Integer.parseInt(args[++i]);
					break;
				case "-port":
					port = args[++i];
					break;
				case "-webport":
					webPort = Integer.parseInt(args[++i]);
					break;
				case "-keypass":
					keyPass = args[++i].getBytes(StandardCharsets.UTF_8);
					break;
				case "-motd":
					motd = args[++i];
					break;
				case "-keyformat":
					break;
			}
		}

		if (keyPass == null) {
			System.out.println("自40版本起不再支持非加密模式,请设定一个密码(-keypass)以生成加密的key");
			return;
		}

		InetSocketAddress addr = NetworkUtil.getListenAddress(port);

		if (maxUsers <= 1) {
			System.out.println("无效的最大连接数");
			return;
		}

		KeyPair pair = KeyFile.getInstance("RSA").setKeySize(2048).getKeyPair(new File("ae_server.key"), new File("ae_client.key"), keyPass);
		if (pair == null) {
			System.out.println("无法解密密钥");
			return;
		}

		try {
			JPrivateKey key = new JPrivateKey(pair);
			server = new AEServer(addr, maxUsers, SimpleEngineFactory.server().key(key).psc(0, key));
		} catch (GeneralSecurityException | IOException e) {
			System.out.println("Invalid certificate / IO Error");
			e.printStackTrace();
			return;
		}
		server.man.getLoop().setDaemon(false);
		server.setMOTD(motd);
		server.start();

		if (webPort != -1) runServer(webPort, server.man.getLoop());

		LogContext ctx = Logger.getRootContext();
		LogDestination prev = log ? ctx.destination() : null;
		ctx.destination(new LogDestination() {
			@Override
			public Appendable getAndLock() {
				Appendable op = prev == null ? null : prev.getAndLock();

				return new Appendable() {
					public Appendable append(char c) { return null; }
					public Appendable append(CharSequence csq, int start, int end) { return null; }

					@Override
					public Appendable append(CharSequence csq) throws IOException {
						if (op != null) op.append(csq);
						logBuffer.ringAddLast(csq.toString());
						return this;
					}
				};
			}

			@Override
			public void unlockAndFlush() throws IOException { if (prev != null) prev.unlockAndFlush(); }
		});

		PrintStream out = Util.out = new LoggingStream();
		System.setOut(Util.out);
		System.setErr(Util.out);

		out.println("    ___    __                          ________         ");
		out.println("   /   |  / /_  __  ________________ _/ / ____/_  _____ ");
		out.println("  / /| | / __ \\/ / / / ___/ ___/ __ `/ / __/ / / / / _ \\");
		out.println(" / ___ |/ /_/ / /_/ (__  |__  ) /_/ / / /___/ /_/ /  __/");
		out.println("/_/  |_/_.___/\\__, /____/____/\\__,_/_/_____/\\__, /\\___/ ");
		out.println("             /____/                        /____/      ");
		out.println(" —— Version " + Util.PROTOCOL_VERSION);
		out.println();
		out.println("服务器已启动");

		Util.registerShutdownHook(server);
	}

	static MyHashMap<String, String> tmp = new MyHashMap<>(4);

	private static String res(String name) throws IOException {
		String v = tmp.get(name);
		if (v == null) tmp.put(name, v = IOUtil.readResUTF("META-INF/html/" + name));
		return v;
	}

	private static void runServer(int port, SelectorLoop man) throws IOException {
		throw new UnsupportedOperationException("not implemented yet");
		/*new FrontServer(FrontServer.res("server.html")) {
			String room;
			int timer;

			@Override
			protected void onWorkerJoin() throws IOException {
				update("join", server.canJoinRoom);
				update("create", server.canCreateRoom);
			}

			@Override
			protected void onWorkerData(CMapping data) throws IOException {
				String id = data.getString("id");
				switch (id) {
					case "power":
						w.get().error(WebsocketHandler.ERR_OK, null);
						server.shutdown();
						break;
					case "join":
						server.canJoinRoom = !server.canJoinRoom;
						update(id, server.canJoinRoom);
						break;
					case "create":
						server.canCreateRoom = !server.canCreateRoom;
						update(id, server.canCreateRoom);
						break;
					case "room":
						room = "";
						break;
				}
			}

			@Override
			protected void onWorkerTick() throws IOException {
				timer++;

				if (timer % 30 == 0 && !logBuffer.isEmpty()) {
					CharList cl = new CharList();
					for (String s : logBuffer) {
						cl.append(s);
					}
					logBuffer.clear();
					append("logs", cl);
				}

				if (timer % 500 == 0) {
					ToJson t = new ToJson();
					t.valueMap();
					t.key("_");
					t.value("update");
					t.key("id");
					t.value("rooms");
					t.key("value");
					t.valueList();

					for (Room room : server.rooms.values()) {
						room.serialize().forEachChild(t);
					}

					send(t.getValue());
				}
			}
		}.simpleRun(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), man);*/
	}
}
