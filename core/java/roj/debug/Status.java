package roj.debug;

import roj.asmx.launcher.Autoload;
import roj.io.BufferPool;
import roj.text.CharList;
import roj.util.ArrayCache;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * @author Roj234
 * @since 2025/05/30 00:51
 */
@Autoload(group = "debug")
final class Status implements StatusMBean {
	static {
		try {
			registerMBean();
		} catch (Throwable e) {
			System.err.println("Failed to register RojLib StatusMBean");
			e.printStackTrace();
		}
	}
	public static void registerMBean() throws Exception {
		// 获取平台MBeanServer
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

		// 定义MBean的唯一标识符（ObjectName）
		ObjectName name = new ObjectName(NAME);

		// 创建MBean实例并注册
		mbs.registerMBean(new Status(), name);
	}

	@Override
	public String getArrayCacheStatus() {return ArrayCache.status(new CharList()).toStringAndFree();}

	@Override
	public String getBufferPoolStatus() {return BufferPool.globalStatus(new CharList()).toStringAndFree();}
}
