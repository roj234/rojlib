package roj.plugins.dns;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public final class DnsRecord {
	String url;

	/**
	 * 指向消息开头的指针
	 */
	short ptr;

	/**
	 * 查询类别
	 */
	short qType;

	/**
	 * 查询分类, 通常为{@link #C_IN}
	 */
	short qClass;

	@Override
	public String toString() {
		return url + '@' + qType;
	}
}