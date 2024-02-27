package roj.plugins.frp;

/**
 * @author Roj234
 * @since 2024/4/6 0006 21:27
 */
public final class PortMapEntry {
	public String name;
	public int from, to;
	public boolean udp;

	public PortMapEntry(char port, String desc) {
		name = desc;
		from = to = port;
	}

	@Override
	public String toString() {return from+" => "+to + (name == null ? "" : "("+name+")") + (udp?" UDP":"");}
}