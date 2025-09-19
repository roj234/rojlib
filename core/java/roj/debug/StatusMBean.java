package roj.debug;

/**
 * @author Roj234
 * @since 2025/05/30 00:51
 */
public interface StatusMBean {
	String NAME = "roj.debug:type=Status,name=debug";

	String getArrayCacheStatus();
	String getBufferPoolStatus();
}
