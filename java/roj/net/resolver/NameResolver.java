package roj.net.resolver;

import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 13:53
 */
public interface NameResolver {
	NameResolver JVM = hostname -> {
		try {
			return Arrays.asList(InetAddress.getAllByName(hostname));
		} catch (UnknownHostException e) {
			return null;
		}
	};

	@Nullable List<InetAddress> lookup(String hostname);
}