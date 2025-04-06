package roj.plugins.web.flow;

import roj.collect.Hasher;
import roj.collect.LFUCache;
import roj.collect.MyHashMap;
import roj.config.auto.SerializerFactory;
import roj.http.server.Request;
import roj.plugin.*;
import roj.util.TypedKey;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/11/26 0026 17:03
 */
@SimplePlugin(id = "flowControl", loadAfter = "EasySSO")
public class FlowControl extends Plugin {
	LFUCache<byte[], FlowController> addressLimiters = new LFUCache<>(32767);
	LFUCache<String, FlowController> groupLimiters = new LFUCache<>(4096);

	public FlowControl() {
		addressLimiters.setHasher(Hasher.array(byte[].class));
	}

	LimitGroup guestGroup;
	Map<String, LimitGroup> limitGroup = new MyHashMap<>();
	static final class LimitGroup {
		int limitSpeed, limitAfter;
		int maxConnections, maxConcurrentConnections;
		int counterReset;
	}

	Function<byte[], FlowController> newLimiter = x -> createLimiter(guestGroup);

	boolean hasEasySSO;

	@Override
	protected void onEnable() throws Exception {
		var desc = getPluginManager().getPlugin("EasySSO");
		hasEasySSO = desc != null && desc.getState() == PluginManager.ENABLED;

		var ser = SerializerFactory.SAFE.mapOf(LimitGroup.class);
		getConfig().accept(ser);
		limitGroup = ser.get();
		guestGroup = Objects.requireNonNull(limitGroup.get("guest"), "Guest group cannot be null");

		Panger.addChannelInitializator(ch -> {
			var addr = ((InetSocketAddress) ch.remoteAddress()).getAddress().getAddress();
			ch.addAfter("h11@server", "h11@flowControl", getController(addr));
		});

		getScheduler().loop(() -> {
			synchronized (addressLimiters) {
				for (var itr = addressLimiters.values().iterator(); itr.hasNext(); ) {
					if (itr.next().isTimeout()) itr.remove();
				}
			}
			synchronized (groupLimiters) {
				for (var itr = groupLimiters.values().iterator(); itr.hasNext(); ) {
					if (itr.next().isTimeout()) itr.remove();
				}
			}
		}, 60000);
	}

	// /24 or /64
	private FlowController getController(byte[] addr) {
		synchronized (addressLimiters) {
			return addressLimiters.computeIfAbsent(Arrays.copyOf(addr, addr.length == 4 ? 3 : 8), newLimiter);
		}
	}

	public FlowController loginCheck(FlowController fc, Request request) {
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

	private FlowController createLimiter(LimitGroup info) {
		var fc = new FlowController(this, null, info.limitAfter, info.maxConcurrentConnections, info.maxConnections, info.counterReset, 4096, (int) TimeUnit.MILLISECONDS.toNanos(50));
		fc.setBytePerSecond(info.limitSpeed);
		return fc;
	}
}
