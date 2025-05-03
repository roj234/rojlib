package roj.http.h2;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/10/7 23:12
 */
public final class H2Setting {
	public int header_table_size = 4096;
	public boolean push_enable;
	//This limit is directional: it applies to the number of streams that the sender permits the receiver to create.
	//也就是说，通过PUSH_PROMISE创建的，会记在客户端上？反之记在服务端
	public int max_streams = -1;
	public int initial_window_size = 65535;
	public int max_frame_size = 16384;
	public int max_header_list_size = -1;

	public void write(DynByteBuf buf, boolean isServer) {
		if (header_table_size != 4096) buf.putShort(1).putInt(header_table_size);
		if (!isServer && push_enable) buf.putShort(2).putInt(push_enable ? 1 : 0);
		if (max_streams != -1) buf.putShort(3).putInt(max_streams);
		if (initial_window_size != 65535) buf.putShort(4).putInt(initial_window_size);
		if (max_frame_size != 16384) buf.putShort(5).putInt(max_frame_size);
		if (max_header_list_size != -1) buf.putShort(6).putInt(max_header_list_size);
	}

	public void read(DynByteBuf buf, boolean isServer) throws H2Exception {
		while (buf.isReadable()) {
			int key = buf.readChar();
			int val = buf.readInt();
			switch (key) {
				default -> H2Connection.LOGGER.debug("vendor_specific flag {}={}", key, val);
				case 1 -> header_table_size = val;
				case 2 -> {
					if ((push_enable = val != 0) && !isServer) throw new H2Exception(H2Exception.ERROR_PROTOCOL, "Illegal PUSH_ENABLE");
				}
				case 3 -> max_streams = val;
				case 4 -> {
					if (val < 0) throw new H2Exception(H2Exception.ERROR_FLOW_CONTROL, "Illegal WINDOW_SIZE");
					initial_window_size = val;
				}
				case 5 -> {
					if (val < 16384 || val > 16777215) throw new H2Exception(H2Exception.ERROR_PROTOCOL, "Illegal MAX_FRAME_SIZE");
					max_frame_size = val;
				}
				case 6 -> max_header_list_size = val;
			}
		}
	}

	public void sanityCheck() throws H2Exception {
		if (max_streams == -1) max_streams = 256;
		if (max_frame_size < 1024 || header_table_size > 65536 || initial_window_size < 512 || max_streams < 2) throw new H2Exception(H2Exception.ERROR_REFUSED, "Insanity setting");
		//if (max_frame_size > 65536) max_frame_size = 65536;
	}
}