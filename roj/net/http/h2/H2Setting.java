package roj.net.http.h2;

import roj.collect.Int2IntMap;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:12
 */
public class H2Setting {
	public int header_table_size = 4096;
	public boolean push_enable;
	public int max_streams = 127;
	public int init_window_size = 65535;
	public int max_frame_size = 16384;
	public int max_header_list_size = -1;

	public Int2IntMap vendor_specific;

	public int r_header_table_size = 4096;
	public int r_max_streams = 127;
	public int r_init_window_size = 65535;
	public int r_max_frame_size = 16384;
	public int r_max_header_list_size = 1024;

	private Int2IntMap r_vendor_specific;

	public void write(DynByteBuf buf, boolean role_server) {
		if (header_table_size != 4096) {
			buf.putShort(1).putInt(header_table_size);
		}
		if (!role_server && push_enable) {
			buf.putShort(2).putInt(push_enable ? 1 : 0);
		}
		if (max_streams != -1) {
			buf.putShort(3).putInt(max_streams);
		}
		if (init_window_size != 65535) {
			buf.putShort(4).putInt(init_window_size);
		}
		if (max_frame_size != 16384) {
			buf.putShort(5).putInt(max_frame_size);
		}
		if (max_header_list_size != -1) {
			buf.putShort(6).putInt(max_header_list_size);
		}

		if (vendor_specific != null && !vendor_specific.isEmpty()) {
			for (Int2IntMap.Entry entry : vendor_specific.selfEntrySet()) {
				buf.putShort(entry.getIntKey()).putInt(entry.v);
			}
		}
	}

	public void read(DynByteBuf buf, boolean role_server) throws H2Error {
		while (buf.isReadable()) {
			int key = buf.readChar();
			int val = buf.readInt();
			switch (key) {
				case 1: r_header_table_size = val; break;
				case 2:
					if (!role_server) throw new H2Error(HttpClient20.ERROR_PROTOCOL, "Server sends PUSH_ENABLE setting");
					push_enable = val != 0;
					break;
				case 3: r_max_streams = val; break;
				case 4:
					if (val < 0) throw new H2Error(HttpClient20.ERROR_FLOW_CONTROL, "Illegal WINDOW_SIZE setting");
					r_init_window_size = val;
					break;
				case 5:
					if (val < 16384 || val > 16777215) throw new H2Error(HttpClient20.ERROR_PROTOCOL, "Illegal MAX_FRAME_SIZE setting");
					r_max_frame_size = val;
					break;
				case 6: r_max_header_list_size = val; break;
				default:
					if (r_vendor_specific == null) r_vendor_specific = new Int2IntMap();
					r_vendor_specific.putInt(key, val);
			}
		}
	}
}
