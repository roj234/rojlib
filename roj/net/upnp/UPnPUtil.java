package roj.net.upnp;

import java.net.InetSocketAddress;

/**
 * @author solo6975
 * @since 2022/1/15 17:55
 */
public class UPnPUtil {
	public static final InetSocketAddress UPNP_ADDRESS = new InetSocketAddress("239.255.255.250", 1900);

	public static final int MTU = 1536;

	public static final String XML_NS = "urn:schemas-upnp-org:";
	public static final int XML_NS_LENGTH = 21;

	public static String trimNamespace(String s) {
		return s.startsWith(XML_NS) ? s.substring(XML_NS_LENGTH) : s;
	}

	public static String addNamespace(String s) {
		return s.startsWith(XML_NS) ? s : XML_NS + s;
	}
}
