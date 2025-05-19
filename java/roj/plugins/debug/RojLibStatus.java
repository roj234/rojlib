package roj.plugins.debug;

import roj.plugins.debug.api.RojLibStatusMBean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * @author Roj234
 * @since 2025/05/30 00:51
 */
//@Autoload(Autoload.Target.INIT)
public class RojLibStatus implements RojLibStatusMBean {
	public static void registerMBean() throws Exception {
		// 获取平台MBeanServer
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

		// 定义MBean的唯一标识符（ObjectName）
		ObjectName name = new ObjectName("roj.debug:type=Status,name=debug");

		// 创建MBean实例并注册
		RojLibStatus mBean = new RojLibStatus();
		mbs.registerMBean(mBean, name);
	}
}
