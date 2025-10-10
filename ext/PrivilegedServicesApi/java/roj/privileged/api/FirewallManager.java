package roj.privileged.api;

import roj.net.rpc.api.RemoteProcedure;

/**
 * @author Roj234
 * @since 2025/10/11 22:46
 */
public interface FirewallManager extends RemoteProcedure {
	/**
	 * 添加防火墙规则
	 * @param source 源IP地址或IP段 (IPv4或IPv6)
	 * @param port 端口号 (0表示所有端口)
	 * @param timeoutSeconds 超时时间(秒)
	 * @return 是否成功添加规则
	 */
	boolean addFirewallRule(String source, int port, int timeoutSeconds);

	/**
	 * 立即移除防火墙规则
	 * @param source 源IP地址或IP段
	 * @param port 端口号
	 * @return 是否成功移除
	 */
	boolean removeFirewallRule(String source, int port);
}
