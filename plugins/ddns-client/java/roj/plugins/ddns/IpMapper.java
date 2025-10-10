package roj.plugins.ddns;

import org.jetbrains.annotations.Nullable;
import roj.config.node.MapValue;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/27 21:56
 */
public interface IpMapper {
	void init(MapValue config);
	void update(@Nullable InetAddress addr4, @Nullable InetAddress addr6);
	default void close() {}
}