package roj.net.cross.server;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.concurrent.Shutdownable;
import roj.concurrent.timing.ScheduledTask;
import roj.concurrent.timing.Scheduler;
import roj.crypt.KeyFile;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.*;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.ch.osi.ServerLaunch;
import roj.net.cross.Constants;
import roj.net.mss.JPrivateKey;
import roj.net.mss.MSSEngineServer;
import roj.ui.DelegatedPrintStream;
import roj.util.DynByteBuf;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.net.cross.Constants.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @since 2021/8/17 22:17
 */
public class AEServer implements Shutdownable, Consumer<MyChannel> {
	public static AEServer server;
	public static byte[] localUserId;

	static RingBuffer<String> logBuffer = new RingBuffer<>(1000, false);

	public static void main(String[] args) throws GeneralSecurityException, IOException {
		if (!NIOUtil.available()) {
			JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
			return;
		}

		byte[] keyPass = null;
		String port = null, motd = "任务：拯救世界(1/1)";
		int webPort = -1;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-port":
					port = args[++i];
					break;
				case "-webport":
					webPort = Integer.parseInt(args[++i]);
					break;
				case "-keypass":
					keyPass = args[++i].getBytes(StandardCharsets.UTF_8);
					break;
			}
		}

		if (keyPass == null) {
			System.out.println("自40版本起不再支持非加密模式,请设定一个密码(-keypass)以生成加密的key");
			return;
		}

		InetSocketAddress addr = NetworkUtil.getListenAddress(port);

		KeyPair pair = KeyFile.getInstance("RSA").setKeySize(2048).getKeyPair(new File("ae_server.key"), new File("ae_client.key"), keyPass);
		if (pair == null) {
			System.out.println("无法解密密钥");
			return;
		}

		try {
			server = new AEServer(addr, 1000, new JPrivateKey(pair));
		} catch (GeneralSecurityException | IOException e) {
			System.out.println("Invalid certificate / IO Error");
			e.printStackTrace();
			return;
		}

		server.launch.getLoop().setDaemon(false);
		server.start();

		if (webPort != -1) runServer(webPort, server.launch.getLoop());

		PrintStream o = System.out;

		System.setOut(new DelegatedPrintStream(99999) {
			@Override
			protected synchronized void newLine() {
				String s = sb.toString();
				o.println(s);
				logBuffer.ringAddLast(s);
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
		o.println("服务器已启动");

		Constants.registerShutdownHook(server);
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

	ConcurrentHashMap<String, Host> rooms = new ConcurrentHashMap<>();
	ConcurrentHashMap<Integer, PipeInfo> pipes = new ConcurrentHashMap<>();
	final Random rnd;

	public final ServerLaunch launch;
	private final JPrivateKey key;

	ScheduledTask task;

	public AEServer(InetSocketAddress addr, int conn, JPrivateKey key) throws IOException {
		server = this;

		this.launch = ServerLaunch.tcp().listen_(addr, conn).option(StandardSocketOptions.SO_REUSEADDR, true).initializator(this);
		if (conn > 0) launch.option(ServerSock.TCP_MAX_ALIVE_CONNECTION, conn);

		this.key = key;
		this.rnd = new SecureRandom();
	}

	public void addLocalConnection(CtxEmbedded ch) throws IOException {
		ch.addLast("state", Handshake.HANDSHAKE);
		ch.open();
	}

	public void addLocalPipe(int id, byte[] key, Consumer<Pipe> callback) {
		Handshake.HANDSHAKE.localPipe(id, key, callback);
	}

	@Override
	public void accept(MyChannel ctx) {
		try {
			initSocketPref(ctx);

			MSSEngineServer engine = new ProtocolVerify();
			engine.setDefaultCert(key);
			engine.switches(MSSEngineServer.VERIFY_CLIENT);
			//engine.setPreSharedCertificate();
			ctx.addLast("tls", new MSSCipher(engine))
			   .addLast("splitter", VarintSplitter.twoMbVLUI())
			   .addLast("timeout", new Timeout(SERVER_TIMEOUT, 500))
			   .addLast("state", Handshake.HANDSHAKE);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public boolean canCreateRoom = true, canJoinRoom = true;

	protected Object clientLogin(byte[] user, String roomToken) {
		Host room = rooms.get(roomToken);
		if (room == null) return PS_ERROR_AUTH;
		if (!canJoinRoom || room.locked) return PS_ERROR_SYSTEM_LIMIT;

		Client client = new Client();
		IntMap<Client> clients = room.clients;
		synchronized (clients) {
			if (clients.size() == 1) room.index = 1;
			clients.putInt(client.clientId = room.index++, client);
		}
		client.room = room;

		return client;
	}

	protected Object hostLogin(byte[] user, String roomToken) {
		Host room = rooms.get(roomToken);
		if (room != null) return PS_ERROR_AUTH;
		if (!canCreateRoom) return PS_ERROR_SYSTEM_LIMIT;

		room = new Host(roomToken);
		rooms.put(roomToken, room);
		return room;
	}

	public void start() throws IOException {
		launch.launch();
		task = Scheduler.getDefaultScheduler().executeTimer(() -> {
			long time = System.currentTimeMillis();
			for (Iterator<PipeInfo> itr = pipes.values().iterator(); itr.hasNext(); ) {
				PipeInfo pi = itr.next();
				if (time > pi.timeout) {
					itr.remove();
					pi.close(null, "creation_timeout");
				}
			}
		}, 100);
	}

	boolean shutdown;
	@Override
	public boolean wasShutdown() { return shutdown; }
	@Override
	public void shutdown() {
		if (shutdown) return;
		try {
			launch.close();
		} catch (IOException ignored) {}
		task.cancel();

		for (Host room : rooms.values()) room.kickAll(PS_ERROR_SHUTDOWN);

		LockSupport.parkNanos(1000_000);
		shutdown = true;

		System.out.println("服务器关闭");
	}

	static final class ProtocolVerify extends MSSEngineServer {
		byte[] userId;

		@Override
		protected Object checkCertificate(int type, DynByteBuf data) {
			try {
				userId = MessageDigest.getInstance("SHA-1").digest(data.toByteArray());
			} catch (Throwable e) {
				return e;
			}

			return super.checkCertificate(type, data);
		}
	}
}