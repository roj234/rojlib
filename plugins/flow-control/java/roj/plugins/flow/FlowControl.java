package roj.plugins.flow;

import roj.annotation.MayMutate;
import roj.collect.HashMap;
import roj.collect.Hasher;
import roj.collect.LFUCache;
import roj.compiler.LambdaLinker;
import roj.config.mapper.ObjectMapper;
import roj.http.server.Request;
import roj.io.IOUtil;
import roj.net.util.SpeedLimiter;
import roj.plugin.Jocker;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.plugin.PluginDescriptor;
import roj.text.TextUtil;
import roj.util.TypedKey;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/11/26 17:03
 */
public class FlowControl extends Plugin {
	private final LFUCache<byte[], FlowController> addressLimiters = new LFUCache<>(32767);
	private final LFUCache<String, FlowController> groupLimiters = new LFUCache<>(4096);

	public FlowControl() {
		addressLimiters.setHasher(Hasher.array(byte[].class));
	}

	int v4GroupSize, v6GroupSize;
	GroupRule rule;
	LimitGroup guestGroup;
	Map<String, LimitGroup> limitGroup = new HashMap<>();
	Plugin sso;
	static final class LimitGroup extends SpeedLimiter.Setting {
		int maxConnections, maxConcurrentConnections;
		transient String name;

		public LimitGroup() {}
	}

	Function<byte[], FlowController> newAddressLimiter = x -> createLimiter(guestGroup);

	@Override
	protected void onEnable() throws Exception {
		sso = getPluginManager().getPluginInstance(PluginDescriptor.Role.PermissionManager);

		v4GroupSize = getConfig().getInt("ipGroupSize");
		v6GroupSize = getConfig().getInt("ipGroupSize6");

		var ser = ObjectMapper.getInstance(ObjectMapper.GENERATE|ObjectMapper.CHECK_INTERFACE|ObjectMapper.SERIALIZE_PARENT).mapOf(LimitGroup.class);
		getConfig().get("groups").accept(ser);
		limitGroup = ser.get();
		for (Map.Entry<String, LimitGroup> entry : limitGroup.entrySet()) {
			entry.getValue().name = entry.getKey();
		}

		guestGroup = Objects.requireNonNull(limitGroup.get("guest"), "Guest group cannot be null");

		var compiler = new LambdaLinker();
		String string = getConfig().getString("groupRule");
		if (string.isBlank()) {
			rule = (user, request) -> user == null ? null : user.getGroupName();
		} else {
			rule = compiler.compile(string, "[groupRule]", GroupRule.class, "user", "request");
		}

		Jocker.addChannelInitializator(ch -> {
			var addr = ((InetSocketAddress) ch.remoteAddress()).getAddress().getAddress();
			ch.addAfter("http:server", "http:flowControl", getController(addr));
		});
	}

	// /24 or /64
	private FlowController getController(byte[] addr) {
		byte[] key = getAddressKey(addr);
		synchronized (addressLimiters) {
			return addressLimiters.computeIfAbsent(key, newAddressLimiter);
		}
	}
	private byte[] getAddressKey(@MayMutate byte[] addr) {
		var buf = IOUtil.SharedBuf.get().wrap(addr);

		if (addr.length == 4) {
			buf.setInt(0, buf.getInt(0) & (-1 << v4GroupSize));
		} else {
			buf.setLong(0, buf.getLong(0) & (-1L << Math.max(0, v6GroupSize-64)));
			buf.setLong(8, buf.getLong(8) & (-1L << v6GroupSize));
		}

		return addr;
	}

	FlowController loginCheck(FlowController controller, Request request) {
		var proxiedAddr = request.remoteAddress();
		if (proxiedAddr == null) return controller;

		byte[] address = proxiedAddr.getAddress().getAddress();
		controller = getController(address);

		var user = sso == null ? null : sso.ipc(new TypedKey<PermissionHolder>("getUser"), request);

		String group = rule.apply(user, request);
		if (group != null) {
			var id = (user == null ? TextUtil.bytes2hex(address)+"\2" : user.getName()+'\1')+group;

			controller = groupLimiters.get(id);
			if (controller == null) {
				synchronized (groupLimiters) {
					var info = limitGroup.get(group);
					if (info == null) {
						getLogger().warn("未找到组{}的流量控制定义", group);
						info = guestGroup;
					}

					controller = createLimiter(info);
					var fc1 = groupLimiters.putIfAbsent(id, controller);
					if (fc1 != null) controller = fc1;
				}
			}
		}

		return controller;
	}

	private FlowController createLimiter(LimitGroup info) {return new FlowController(this, info);}
}
