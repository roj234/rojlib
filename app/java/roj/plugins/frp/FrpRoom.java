package roj.plugins.frp;

import org.jetbrains.annotations.Nullable;
import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.collect.XashMap;
import roj.http.Headers;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Net;
import roj.net.mss.MSSHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/11/20 3:02
 */
public class FrpRoom implements ChannelHandler {
	static final XashMap.Builder<String, PortMapEntry> setMaker = XashMap.noCreation(PortMapEntry.class, "name");

	public byte[] hash;

	public String friendlyName;
	public XashMap<String, PortMapEntry> portMaps = setMaker.create();
	@Nullable
	public FrpServerConnection remote;
	public Set<FrpServerConnection> connections = Collections.synchronizedSet(new HashSet<>());

	public String motd = "~o( =∩ω∩= )m 欢迎使用船新通信协议！";
	public Set<byte[]> cidWhitelist = new HashSet<>(Hasher.array(byte[].class));
	public Set<byte[]> cidBlacklist = new HashSet<>(Hasher.array(byte[].class));
	public boolean ready;

	{
		portMaps.add(new PortMapEntry((char) 80, "test", false));
		PortMapEntry value = new PortMapEntry((char) 8887, "test-udp", true);
		portMaps.add(value);
	}

	public FrpRoom(String friendlyName) {this.friendlyName = friendlyName;}
	FrpRoom() {this((FrpServerConnection) null);}
	FrpRoom(FrpServerConnection remote) {this.remote = remote;}

	public void close() {

	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		System.out.println("Waiting for re_init");
		((MSSHandler) ctx.channel().handler("mss").handler()).getEngine().close();
	}

	public void addRemoteConnection(FrpServerConnection cc) throws IOException {
		var ch = cc.channel().channel();
		ch.readInactive();
		ch.replace("h2", this);
		cc.addr = Net.toString(ch.remoteAddress());

		assert remote != null;
		remote.control.schedule(() -> {
			var header = new Headers();
			header.put(":status", "200");
			header.put("endpoint-ip", cc.addr);

			var push = (FrpProxy) remote.push(remote.control, header);
			if (push == null) throw new IllegalStateException("Push not enabled [端点配置错误]");

			ch.removeAll();
			push.init(ch, new PortMapEntry((char) 0, "[E2EE] 用户 "+cc, false));
			push.man = remote;
			ch.readActive();
		});
	}
}
