package roj.plugins.web.flow;

import roj.collect.HashMap;
import roj.collect.Hasher;
import roj.collect.LFUCache;
import roj.config.auto.SerializerFactory;
import roj.http.server.Request;
import roj.net.util.SpeedLimiter;
import roj.plugin.*;
import roj.util.TypedKey;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/11/26 17:03
 */
@SimplePlugin(id = "flowControl", loadAfter = "EasySSO")
public class FlowControl extends Plugin {
	LFUCache<byte[], FlowController> addressLimiters = new LFUCache<>(32767);
	LFUCache<String, FlowController> groupLimiters = new LFUCache<>(4096);

	public FlowControl() {
		addressLimiters.setHasher(Hasher.array(byte[].class));
	}

	LimitGroup guestGroup;
	Map<String, LimitGroup> limitGroup = new HashMap<>();
	static final class LimitGroup extends SpeedLimiter.Setting {
		int maxConnections, maxConcurrentConnections;
		transient String name;

		public LimitGroup() {}
	}

	Function<byte[], FlowController> newAddressLimiter = x -> createLimiter(guestGroup);

	boolean hasEasySSO;

	@Override
	protected void onEnable() throws Exception {
		var desc = getPluginManager().getPlugin("EasySSO");
		hasEasySSO = desc != null && desc.getState() == PluginManager.ENABLED;

		var ser = SerializerFactory.getInstance(SerializerFactory.GENERATE|SerializerFactory.CHECK_INTERFACE|SerializerFactory.SERIALIZE_PARENT).mapOf(LimitGroup.class);
		getConfig().accept(ser);
		limitGroup = ser.get();
		for (Map.Entry<String, LimitGroup> entry : limitGroup.entrySet()) {
			entry.getValue().name = entry.getKey();
		}
		guestGroup = Objects.requireNonNull(limitGroup.get("guest"), "Guest group cannot be null");

		Jocker.addChannelInitializator(ch -> {
			var addr = ((InetSocketAddress) ch.remoteAddress()).getAddress().getAddress();
			ch.addAfter("http:server", "http:flowControl", getController(addr));
		});
	}

	// /24 or /64
	private FlowController getController(byte[] addr) {
		synchronized (addressLimiters) {
			return addressLimiters.computeIfAbsent(Arrays.copyOf(addr, addr.length == 4 ? 3 : 8), newAddressLimiter);
		}
	}

	FlowController loginCheck(FlowController fc, Request request) {
		var proxiedAddr = request.proxyRemoteAddress();
		if (proxiedAddr == null) return fc;
		fc = getController(proxiedAddr.getAddress().getAddress());

		if (!hasEasySSO) return fc;

		var sso = getPluginManager().getPluginInstance(PluginDescriptor.Role.PermissionManager);
		var user = sso.ipc(new TypedKey<PermissionHolder>("getUser"), request);
		if (user != null) {
			var group = user.getGroupName();
			var id = user.getName()+'\1'+group;

			fc = groupLimiters.get(id);
			if (fc == null) {
				synchronized (groupLimiters) {
					var info = limitGroup.get(group);
					if (info == null) {
						getLogger().warn("未找到组{}的流量控制定义", group);
						info = guestGroup;
					}

					fc = createLimiter(info);
					var fc1 = groupLimiters.putIfAbsent(id, fc);
					if (fc1 != null) fc = fc1;
				}
			}

			return fc;
		}

		return fc;
	}

	private FlowController createLimiter(LimitGroup info) {return new FlowController(this, info);}
}
