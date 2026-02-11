package roj.plugins.dns;

import org.intellij.lang.annotations.MagicConstant;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public final class DnsQuestion {
	/**
	 * 由域名获得IPv4地址
	 */
	public static final short Q_A = 1;
	/**
	 * 查询授权的域名服务器
	 */
	public static final short Q_NS = 2;
	/**
	 * mail destination
	 *
	 * @deprecated Obsolete, use MX
	 */
	@Deprecated
	public static final short Q_MD = 3;
	/**
	 * mail forwarder
	 *
	 * @deprecated Obsolete, use MX
	 */
	@Deprecated
	public static final short Q_MF = 4;
	/**
	 * 查询规范名称, alias
	 */
	public static final short Q_CNAME = 5;
	/**
	 * Start of authority
	 */
	public static final short Q_SOA = 6;
	/**
	 * mailbox domain name (experimental)
	 */
	public static final short Q_MB = 7;
	/**
	 * mail group member (experimental)
	 */
	public static final short Q_MG = 8;
	/**
	 * mail rename domain name (experimental)
	 */
	public static final short Q_MR = 9;
	/**
	 * null response record (experimental)
	 */
	public static final short Q_NULL = 10;
	/**
	 * Well known service
	 */
	public static final short Q_WKS = 11;
	/**
	 * 把IP地址转换成域名（指针记录，反向查询）
	 */
	public static final short Q_PTR = 12;
	/**
	 * Host information
	 */
	public static final short Q_HINFO = 13;
	/**
	 * Mail information
	 */
	public static final short Q_MINFO = 14;
	/**
	 * 邮件交换记录
	 */
	public static final short Q_MX = 15;
	public static final short Q_TXT = 16;
	/**
	 * 由域名获得IPv6地址
	 */
	public static final short Q_AAAA = 28;
	/**
	 * related: mailbox (MB, MG or MR)
	 */
	public static final short Q_MAILB = 253;
	/**
	 * 所有记录
	 */
	public static final short Q_ANY = 255;

	public static final byte C_INTERNET = 1, C_ANY = (byte) 255;

	String host;

	/**
	 * 指向消息开头的指针
	 */
	short ptr;

	/**
	 * 查询类别
	 */
	short qType;

	/**
	 * 查询分类, 通常为{@link #C_INTERNET}
	 */
	short qClass;

	public String getHost() {return host;}
	public short getqType() {return qType;}
	@MagicConstant(intValues = {C_INTERNET, C_ANY})
	public short getqClass() {return qClass;}

	@Override
	public String toString() {return host + '@' + qType;}

	public static void encodeDomain(DynByteBuf w, CharSequence domain) {
		int prev = 0, i = 0;
		for (; i < domain.length(); i++) {
			char c = domain.charAt(i);
			assert c < 128;
			if (c == '.') {
				if (i - prev > 63) throw new IllegalArgumentException("Domain length should not larger than 63 characters");
				w.put(i - prev);
				for (int j = prev; j < i; j++) {
					w.put(domain.charAt(j));
				}
				prev = i + 1;
			}
		}
		if (i - prev > 63) throw new IllegalArgumentException("Domain length should not larger than 63 characters");
		w.put(i - prev);
		if (i - prev > 0) {
			for (int j = prev; j < i; j++) {
				w.put(domain.charAt(j));
			}
			w.put(0);
		}
	}
}