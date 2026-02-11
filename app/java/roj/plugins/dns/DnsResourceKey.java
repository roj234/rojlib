package roj.plugins.dns;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
public final class DnsResourceKey {
	static final byte TYPE_ANSWER = 1, TYPE_AUTHORITY_RECORD = 2, TYPE_ADDITIONAL_RECORD = 4;

	String host;
	byte type;

	public DnsResourceKey(String host) {this(host, TYPE_ANSWER);}
	public DnsResourceKey(String host, byte type) {
		this.host = host;
		this.type = type;
	}
	public DnsResourceKey() {}

	public String getHost() {return host;}
	public byte getType() {return type;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DnsResourceKey that = (DnsResourceKey) o;
		return host.equals(that.host);
	}

	@Override
	public int hashCode() {return host.hashCode();}

	@Override
	public String toString() {return host;}
}