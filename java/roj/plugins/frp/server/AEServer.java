package roj.plugins.frp.server;

import roj.collect.Hasher;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.concurrent.Shutdownable;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.crypt.Blake3;
import roj.crypt.KeyType;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetUtil;
import roj.net.ch.*;
import roj.net.handler.Fail2Ban;
import roj.net.handler.MSSCipher;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.net.mss.MSSEngineServer;
import roj.net.mss.MSSKeyPair;
import roj.net.mss.MSSPublicKey;
import roj.plugins.frp.Constants;
import roj.ui.DelegatedPrintStream;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.plugins.frp.Constants.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @since 2021/8/17 22:17
 */
public class AEServer implements Shutdownable, Consumer<MyChannel> {
	public static AEServer server;
	public static byte[] localUserId;

	public MyHashMap<byte[], String> userWhiteList;

	MyHashMap<byte[], Client> session = new MyHashMap<>();
	{
		session.setHasher(Hasher.array(byte[].class));
	}

	static RingBuffer<String> logBuffer = new RingBuffer<>(1000, false);
	static final Fail2Ban fail2Ban = new Fail2Ban();

	public static void main(String[] args) throws GeneralSecurityException, IOException {
		assert NIOUtil.available();

		byte[] keyPass = null;
		String port = null;
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
			System.out.println("请设定一个密码(-keypass)以加密key");
			return;
		}

		InetSocketAddress addr = NetUtil.parseListeningAddress(port);

		KeyPair pair = KeyType.getInstance("EdDSA").loadOrGenerateKey(new File("ae_server.key"), keyPass);
		if (pair == null) {
			System.out.println("无法解密密钥");
			return;
		}

		try {
			server = new AEServer(addr, 1000, new MSSKeyPair(pair));
		} catch (GeneralSecurityException | IOException e) {
			System.out.println("Invalid certificate / IO Error");
			e.printStackTrace();
			return;
		}

		server.launch.loop().setDaemon(false);
		server.start();

		if (webPort != -1) runServer(webPort, server.launch.loop());

		PrintStream o = System.out;

		System.setOut(new DelegatedPrintStream() {
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
		if (v == null) tmp.put(name, v = IOUtil.getTextResource("META-INF/html/" + name));
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
	ConcurrentHashMap<DynByteBuf, PipeInfo> pipes = new ConcurrentHashMap<>();
	final Random rnd;

	public final ServerLaunch launch;
	private final MSSKeyPair key;

	ScheduleTask task;

	public AEServer(InetSocketAddress addr, int conn, MSSKeyPair key) throws IOException {
		server = this;

		this.launch = ServerLaunch.tcp().bind(addr, conn).option(StandardSocketOptions.SO_REUSEADDR, true).initializator(this);
		if (conn > 0) launch.option(ServerLaunch.TCP_MAX_CONNECTION, conn);

		this.key = key;
		this.rnd = new SecureRandom();
	}

	public void addLocalConnection(EmbeddedChannel ch) throws IOException {ch.addLast("state", Handshake.HANDSHAKE);ch.open();}
	public MyChannel addLocalPipe(ChannelCtx ctx, DynByteBuf rb) throws IOException {return Handshake.HANDSHAKE.doPipeLoginHostLocal(ctx, rb);}

	@Override
	public void accept(MyChannel ctx) {
		try {
			initSocketPref(ctx);

			MSSEngineServer engine = new ProtocolVerify();
			engine.setDefaultCert(key);
			engine.switches(MSSEngineServer.VERIFY_CLIENT);
			ctx.addLast("handshake_logger", fail2Ban)
			   .addLast("tls", new MSSCipher(engine))
			   .addLast("splitter", VarintSplitter.twoMbVLUI())
			   .addLast("timeout", new Timeout(SERVER_TIMEOUT, 500))
			   .addLast("state", Handshake.HANDSHAKE);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public boolean canCreateRoom = true;

	protected Object clientLogin(byte[] user, String roomName) {
		if (userWhiteList != null && !userWhiteList.containsKey(user)) return PS_ERROR_AUTH;

		Host room = rooms.get(roomName);
		if (room == null) return PS_ERROR_ROOM_EXISTENCE;

		Client client = new Client(room);
		synchronized (room) {
			if (room.blacklist.contains(user) != room.whitelist) return PS_ERROR_PERMISSION;
			room.clients.putInt(client.clientId = room.nextId++, client);
		}
		return client;
	}

	protected Object hostLogin(byte[] user, String roomName) {
		if (userWhiteList != null && !userWhiteList.containsKey(user)) return PS_ERROR_AUTH;

		Host room = rooms.get(roomName);
		if (room != null) return PS_ERROR_ROOM_EXISTENCE;
		if (!canCreateRoom) return PS_ERROR_PERMISSION;

		room = new Host(roomName);
		rooms.put(roomName, room);
		return room;
	}

	public void start() throws IOException {
		launch.launch();
		task = Scheduler.getDefaultScheduler().loop(() -> {
			long time = System.currentTimeMillis();
			for (Iterator<PipeInfo> itr = pipes.values().iterator(); itr.hasNext(); ) {
				PipeInfo pi = itr.next();
				if (time > pi.timeout) {
					itr.remove();
					pi.close(null, "creation_timeout");
				}
			}
		}, 200);
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

		for (Host room : rooms.values()) {
			room.kickAll(PS_ERROR_SHUTDOWN);
			room.kickWithMessage(PS_ERROR_SHUTDOWN);
		}

		LockSupport.parkNanos(1_000_000L);
		shutdown = true;

		System.out.println("服务器关闭");
	}

	static final class ProtocolVerify extends MSSEngineServer {
		byte[] userId;

		@Override
		protected MSSPublicKey checkCertificate(int type, DynByteBuf data) throws GeneralSecurityException {
			Blake3 md = new Blake3(32);
			md.update(data.slice());
			userId = md.digestShared();
			return super.checkCertificate(type, data);
		}
	}
}