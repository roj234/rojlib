package roj.plugins.frp;

import roj.collect.Hasher;
import roj.collect.MyHashSet;
import roj.crypt.Blake3;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.handler.MSSCrypto;
import roj.net.handler.Timeout;
import roj.net.http.Headers;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSPublicKey;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0015 1:49
 */
public class FrpServer extends MSSContext implements Consumer<MyChannel> {
	static final Logger LOGGER = Logger.getLogger("FRP/Server");

	Map<String, FrpRoom> activeRooms = new ConcurrentHashMap<>();
	Set<byte[]> cfpWhitelist = new MyHashSet<>(Hasher.array(byte[].class));

	private ServerLaunch launch;

	public FrpServer(String connection_id) throws IOException {
		flags = VERIFY_CLIENT;
		launch = ServerLaunch.tcp(connection_id).initializator(this);
	}

	public ServerLaunch launch() {return launch;}
	public void stop() {IOUtil.closeSilently(launch);}

	public void addLocalRoom(FrpRoom room) {this.activeRooms.put(room.name, room);}
	public void addRemoteRoom(FrpRoom room, InetSocketAddress address) throws IOException {
		this.activeRooms.put(room.name, room);
		var connection = new FrpHostConnection(this, room);
		ClientLaunch.tcp().initializator(connection).connect(address).launch();
	}
	public void addHostConnection(String address, EmbeddedChannel channel) throws IOException {
		channel.setRemote(NetUtil.parseAddress(address, 0));
		launch.addTCPConnection(channel);
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSCrypto(this.serverEngine()))
		  .addLast("timeout", new Timeout(90000, 1000))
		  .addLast("h2", new FrpServerConnection(this));
	}

	@Override
	protected MSSPublicKey checkCertificate(Object ctx, int type, DynByteBuf data) throws GeneralSecurityException {
		var key = super.checkCertificate(ctx, type, data);

		var digest = new Blake3(32);
		digest.update(data.slice());
		((FrpServerConnection) ((ChannelCtx) ctx).channel().handler("h2").handler()).hash = digest.digestShared();

		return key;
	}

	boolean join(FrpServerConnection frp, Headers req, Headers resp) {
		var cfp = frp.hash;
		if (!cfpWhitelist.isEmpty() && !cfpWhitelist.contains(cfp)) {
			resp.put(":error", "system_whitelist");
			return false;
		}

		var mod = req.getField(":method");
		var room = activeRooms.get(req.getField(":authority"));
		if (mod.equals("JOIN")) {
			if (room == null) {
				resp.put(":error", "not_exist");
				return false;
			}

			if (room.whitelistEnabled && !room.cidWhitelist.contains(cfp)) {
				resp.put(":error", "room_whitelist");
				return false;
			}

			if (room.cidBlacklist.contains(cfp)) {
				resp.put(":error", "blocked");
				return false;
			}

			if (room.host != null) {
				resp.put("endpoint-hash", ByteList.wrap(room.host.hash).base64UrlSafe());
			}

			room.connections.add(frp);
		} else {
			if (room != null) {
				resp.put(":error", "already_exist");
				return false;
			}

			room = new FrpRoom(req.getField(":authority"), frp);
			activeRooms.put(req.getField(":authority"), room);
		}

		frp.room = room;
		return true;
	}

	void exit(FrpServerConnection connection) {
		FrpRoom room = connection.room;
		if (room != null) {
			if (room.host == connection) {
				room.close();
			} else {
				room.connections.remove(connection);
			}
		}
	}
}
