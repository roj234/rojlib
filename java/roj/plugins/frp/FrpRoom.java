package roj.plugins.frp;

import org.jetbrains.annotations.Nullable;
import roj.collect.Hasher;
import roj.collect.MyHashSet;
import roj.collect.XHashSet;
import roj.net.MyChannel;
import roj.net.NetUtil;
import roj.net.http.Headers;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/11/20 0020 3:02
 */
public class FrpRoom {
	static final XHashSet.Shape<String, PortMapEntry> setMaker = XHashSet.noCreation(PortMapEntry.class, "name");

	public String name;
	public boolean isPrivate;
	public XHashSet<String, PortMapEntry> portMaps = setMaker.create();
	public String motd = "~o( =∩ω∩= )m 欢迎使用船新通信协议！";
	@Nullable
	public final FrpServerConnection host;
	public Set<FrpServerConnection> connections = Collections.synchronizedSet(new MyHashSet<>());
	public Set<byte[]> cidWhitelist = new MyHashSet<>(Hasher.array(byte[].class));
	public boolean whitelistEnabled;
	public Set<byte[]> cidBlacklist = new MyHashSet<>(Hasher.array(byte[].class));

	{
		portMaps.add(new PortMapEntry((char) 80, "test", false));
		PortMapEntry value = new PortMapEntry((char) 8887, "test-udp", true);
		portMaps.add(value);
	}

	public FrpRoom(String name) {this(name, null);}
	public FrpRoom(String name, FrpServerConnection host) {
		this.name = name;
		this.host = host;
	}

	public void close() {

	}

	public void addRemoteConnection(MyChannel ch) throws IOException {
		assert host != null;
		host.control.schedule(() -> {
			var header = new Headers();
			header.put(":status", "200");
			String ipAddress = NetUtil.toString(ch.remoteAddress());
			header.put("endpoint-ip", ipAddress);

			var push = (FrpProxy) host.push(host.control, header);
			if (push == null) throw new IllegalStateException("Push not enabled [endpoint]");

			push.init(ch, new PortMapEntry((char) 0, ipAddress+" <=> Server(Local) <=> Host", false));
			push.man = host;
			ch.readActive();
		});
	}
}
