package roj.plugins.ddns;

import roj.config.data.CMapping;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/27 0027 21:56
 */
public interface DDNSService {
	void loadConfig(CMapping config);
	void init(Iterable<Map.Entry<String, List<String>>> monitoringSites) throws Exception;
	void update(Iterable<Map.Entry<String, InetAddress[]>> changed);
	default void cleanup() {}
}
