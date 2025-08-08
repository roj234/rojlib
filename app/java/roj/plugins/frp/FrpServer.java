package roj.plugins.frp;

import roj.collect.CharMap;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.crypt.Blake3;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.mss.MSSHandler;
import roj.net.handler.Timeout;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSException;
import roj.net.mss.MSSPublicKey;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 1:49
 */
public class FrpServer extends MSSContext implements Consumer<MyChannel> {
	public static final char EXTENSION_TARGET_ROOM = 0xFAFB;
	public static final char EXTENSION_PADDING     = 0xFFFE;

	static final Logger LOGGER = Logger.getLogger("FRPServer");

	HashMap<byte[], FrpRoom> activeRooms = new HashMap<>();
	Set<byte[]> endpointWhitelist = new HashSet<>(Hasher.array(byte[].class));
	Set<byte[]> hostWhitelist = new HashSet<>(Hasher.array(byte[].class));

	private ServerLaunch launch;

	public FrpServer(String connection_id) throws IOException {
		activeRooms.setHasher(Hasher.array(byte[].class));
		flags = VERIFY_CLIENT;
		launch = ServerLaunch.tcp(connection_id).initializator(this);
	}

	public ServerLaunch launch() {return launch;}
	public void stop() {IOUtil.closeSilently(launch);}

	public FrpRoom addLocalRoom() {
		var digest = new Blake3(32);
		digest.update(cert.encode());
		byte[] cfp = digest.digestShared();

		var room = activeRooms.computeIfAbsent(cfp, x -> new FrpRoom());
		room.hash = cfp;
		return room;
	}
	public FrpRoom addRemoteRoom(FrpRoom room, InetSocketAddress address) throws IOException {
		var digest = new Blake3(32);
		digest.update(cert.encode());
		room.hash = digest.digestShared();

		var connection = new FrpHostConnection(this, room);
		ClientLaunch.tcp().initializator(connection).connect(address).launch();
		activeRooms.put(room.hash, room);
		return room;
	}
	public void addHostConnection(String address, EmbeddedChannel channel) throws IOException {
		channel.setLocal(new SocketAddress() {
			@Override
			public String toString() {
				return "AddHostConnection";
			}
		});
		channel.setRemote(Net.parseAddress(address, 0));
		launch.addTCPConnection(channel);
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSHandler(this.serverEngine()))
		  .addLast("timeout", new Timeout(9000, 1000))
		  .addLast("h2", new FrpServerConnection(this));
	}

	private static FrpServerConnection manager(ChannelCtx ctx) {return (FrpServerConnection) ctx.channel().handler("h2").handler();}

	@Override
	protected MSSPublicKey checkCertificate(Object ctx, int type, DynByteBuf data, boolean isServerCert) throws MSSException, GeneralSecurityException {
		var key = super.checkCertificate(ctx, type, data, isServerCert);

		var digest = new Blake3(32);
		digest.update(data.slice());
		byte[] cfp = digest.digestShared();

		if (isServerCert) {
			System.out.println("ignore server cert "+ TextUtil.dumpBytes(cfp));
		} else {
			var connection = manager((ChannelCtx) ctx);
			connection.hash = cfp;

			if (!endpointWhitelist.isEmpty() && !endpointWhitelist.contains(cfp)) connection.fail("system:not_allowed");
		}
		return key;
	}

	@Override
	protected boolean processExtensions(Object ctx, CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int stage) throws MSSException {
		if (stage == 3) {
			var connection = manager((ChannelCtx) ctx);

			var room = activeRooms.get(connection.hash);
			if (room != null) connection.fail("room:already_exist");

			var connectToRoom = extIn.remove(EXTENSION_TARGET_ROOM);
			if (connectToRoom != null) {
				room = activeRooms.get(connectToRoom.toByteArray());
				if (room == null) connection.fail("room:not_found");
				if (!room.ready) connection.fail("room:not_ready");
				if (!room.cidWhitelist.isEmpty() && !room.cidWhitelist.contains(connection.hash)) connection.fail("room:not_allowed");
				if (room.cidBlacklist.contains(connection.hash)) connection.fail("room:blocked");

				// 共识-如果与期待的host hash不同，那么会重新握手
				// redirect to FrpHostConnection
				if (room.remote != null) {
					try {
						room.addRemoteConnection(connection);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					return false;
				}
			} else {
				if (!hostWhitelist.isEmpty() && !hostWhitelist.contains(connection.hash)) connection.fail("room:not_allowed");

				room = new FrpRoom(connection);
				room.hash = connection.hash;
				synchronized (activeRooms) {
					activeRooms.put(room.hash, room);
				}
			}

			connection.success(room);
		}

		return super.processExtensions(ctx, extIn, extOut, stage);
	}

	void exit(FrpServerConnection connection) {
		FrpRoom room = connection.room;
		if (room != null) {
			if (room.remote == connection) {
				synchronized (activeRooms) {
					activeRooms.remove(room.hash);
				}
				room.close();
			} else {
				room.connections.remove(connection);
			}
		}
	}
}
