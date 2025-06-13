package roj.plugins.p2p;

import roj.collect.IntSet;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.net.*;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Random;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc5389">Session Traversal Utilities for NAT (STUN)</a>
 * @author Roj234
 * @since 2024/1/6 5:10
 */
public final class STUN implements ChannelHandler {
	private static final int MAGIC = 0x2112A442;
	public static final int DEFAULT_STUN_PORT = 3478;
	public static final int UDP = 1, CHANGE_PORT = 3, CHANGE_IP = 5;

	public static Response request(InetSocketAddress stunServer, int timeout, int flag) throws IOException { return request(stunServer, timeout, flag&1, null); }
	/**
	 * array:
	 * [0] => local address
	 * [1] => remote address
	 * [2] => server address
	 */
	public static Response request(InetSocketAddress stunServer, int timeout, int flag, InetSocketAddress localAddr) {
		Response r = new Response();

		byte[] transactionId = new byte[12];
		new Random().nextBytes(transactionId);

		ByteList b = IOUtil.getSharedByteBuf(); b.ensureCapacity(2048);
		b.putShort(0x0001).putShort((flag&6) != 0 ? 8 : 0).putInt(MAGIC).put(transactionId);

		if ((flag&6) != 0) {
			//   0x0003: CHANGE-REQUEST
			b.putShort(0x0003).putShort(4).putInt(flag & ~1);
		}

		byte[] pkt = b.list;
		int length = b.wIndex();
		if ((flag&UDP) != 0) {
			try (DatagramChannel ch = DatagramChannel.open()) {
				// 不能reusePort

				// 你就说恶不恶心吧... 不然呢？while time < deadline => sleep ?
				ch.configureBlocking(true);
				DatagramSocket so = ch.socket();
				so.setSoTimeout(timeout);

				DatagramPacket p = new DatagramPacket(pkt, 0, length, stunServer);
				so.send(p);

				p = new DatagramPacket(pkt, pkt.length);
				so.receive(p);
				length = p.getLength();

				r.localAddress = (InetSocketAddress) ch.getLocalAddress();
				r.serverAddress = (InetSocketAddress) p.getSocketAddress();
			} catch (SocketTimeoutException e) {
				r.errCode = 999;
				r.errMsg = "no response";
				return r;
			} catch (IOException e) {
				r.errCode = 999;
				r.errMsg = "connect failed: "+e.getMessage();
				return r;
			}
		} else {
			try (SocketChannel ch = SocketChannel.open()) {
				ch.setOption(StandardSocketOptions.SO_REUSEADDR, true);
				if (localAddr != null) {
					Net.setReusePort(ch, true);
					ch.bind(localAddr);
				}

				ch.configureBlocking(true);
				Socket so = ch.socket();
				so.connect(stunServer, timeout);
				so.setSoTimeout(timeout);
				so.getOutputStream().write(pkt, 0, length);

				r.localAddress = (InetSocketAddress) ch.getLocalAddress();
				r.serverAddress = (InetSocketAddress) ch.getRemoteAddress();

				try (DataInputStream in = new DataInputStream(so.getInputStream())) {
					int type = in.readUnsignedShort();
					int len = in.readUnsignedShort();
					b.clear(); b.putShort(type).putShort(len);
					in.readFully(pkt, 4, len+16);
					length = len+20;
				}
			} catch (SocketTimeoutException e) {
				r.errCode = 999;
				r.errMsg = "no response";
				return r;
			} catch (IOException e) {
				r.errCode = 999;
				r.errMsg = "connect failed: "+e.getMessage();
				return r;
			}
		}

		b.wIndex(length);

		int type = b.readUnsignedShort();
		int len = b.readUnsignedShort();
		if (length != len+20 || b.readInt() != MAGIC || !Arrays.equals(transactionId, b.readBytes(12))) {
			r.errCode = 998;
			r.errMsg = "packet error";
			return r;
		}

		//   #define IS_REQUEST(msg_type)       (((msg_type) & 0x0110) == 0x0000)
		//   #define IS_INDICATION(msg_type)    (((msg_type) & 0x0110) == 0x0010)
		//   #define IS_SUCCESS_RESP(msg_type)  (((msg_type) & 0x0110) == 0x0100)
		//   #define IS_ERR_RESP(msg_type)      (((msg_type) & 0x0110) == 0x0110)
		if ((type & 0x0110) != 0x0100) {
			r.errCode = 996;
			r.errMsg = "unsupported operation";
		}

		try {
			InetSocketAddress legacy_address = null, address = null;
			loop:
			while (b.isReadable()) {
				type = b.readUnsignedShort();
				len = b.readUnsignedShort();

				switch (type) {
					default: b.rIndex += len; break;
					case 0x0009: // ERROR
						int errMajor = (b.readInt() >>> 8) & 7;
						int errMinor = errMajor & 0xFF;
						r.errCode = errMajor*100 + errMinor;
						r.errMsg = b.readUTF(len-4);
					break;
					case 0x8022: // SOFTWARE
						r.software = b.readUTF(len);
						if ((len&3) != 0) b.rIndex += 4-(len&3);
					break;
					case 0x802b: // RESPONSE-ORIGIN
						InetSocketAddress addr = readAddress(b, null);
						if (!r.serverAddress.equals(addr)) {
							r.serverSideNAT = addr;
						}
					break;
					case 0x8028: // FINGERPRINT
						int crc = CRC32.crc32(pkt, 0, b.rIndex-4) ^ 0x5354554e;
						int reCrc = b.readInt();
						if (crc != reCrc) {
							r.errCode = 995;
							r.errMsg = "illegal crc32 "+Integer.toHexString(reCrc)+", excepting "+Integer.toHexString(crc);
						}
						break loop;
					case 0x0001: // MAPPED-ADDRESS
						legacy_address = readAddress(b, null);
					break;
					case 0x0020: // XOR-MAPPED-ADDRESS
						address = readAddress(b, transactionId);
					break;
				}
			}

			if (address != null) {
				if (legacy_address != null && !legacy_address.equals(address)) {
					r.errCode = 997;
					r.errMsg = "mangled packet";
				}
				r.internetAddress = address;
			} else {
				r.internetAddress = legacy_address;
			}
		} catch (Exception e) {
			r.errCode = 998;
			r.errMsg = "packet error: "+e.getMessage();
		}

		return r;
	}
	private static InetSocketAddress readAddress(ByteList b, byte[] xor) throws UnknownHostException {
		b.rIndex++;
		int family = b.readUnsignedByte();
		int port = b.readUnsignedShort();
		byte[] ip = b.readBytes(family == 0x01 ? 4 : 16);

		if (xor != null) {
			port ^= (MAGIC>>>16);
			for (int i = 0; i < 4; i++) ip[i] ^= MAGIC >>> ((3-i)<<3);
			if (family == 0x02) for (int i = 4; i < 16; i++) ip[i] ^= xor[i-4];
		}

		return new InetSocketAddress(InetAddress.getByAddress(ip), port);
	}

	public static final class Response {
		public InetSocketAddress
			localAddress, internetAddress,
			serverAddress, serverSideNAT;

		public String software;

		public int errCode;
		public String errMsg;

		public String getErrCodeString() {
			return switch (errCode) {
				case 400 -> "Bad Request";
				case 401 -> "Unauthorized";
				case 420 -> "Unknown Attribute";
				case 430 -> "Stale Credentials";
				case 431 -> "Integrity Check Failure";
				case 432 -> "Missing Username";
				case 433 -> "Use TLS";
				case 500 -> "Server Error";
				case 600 -> "Global Failure";
				default -> Integer.toString(errCode);
			};
		}

		@Override
		public String toString() {
			if (errCode == 0) {
				return "STUN.Response: "+localAddress+" => "+internetAddress+" (from server "+software+" at "+serverAddress+")";
			} else {
				return "STUN.Response: error \""+getErrCodeString()+"\": "+errMsg;
			}
		}
	}

	public static ServerLaunch createServer(ServerLaunch launch) throws IOException {
		InetSocketAddress address = (InetSocketAddress) launch.localAddress();
		if (address == null) throw new IllegalArgumentException("address is null");
		return launch.initializator(ch -> ch.addLast("stun_server", new STUN(address, false)));
	}

	private final InetSocketAddress myLocalAddress;
	private final boolean dispatch;
	public STUN(InetSocketAddress myLocalAddress, boolean dispatch) {
		this.myLocalAddress = myLocalAddress;
		this.dispatch = dispatch;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf;
		InetSocketAddress address;
		if (msg instanceof DatagramPkt pkt) {
			buf = pkt.data;
			address = pkt.address;
		} else {
			buf = (DynByteBuf) msg;
			address = (InetSocketAddress) ctx.remoteAddress();
		}

		if (buf.readableBytes() < 4) {
			error(ctx, msg);
			return;
		}
		int length = buf.readUnsignedShort(buf.rIndex+2);
		if (buf.readableBytes() < length+20) {
			error(ctx, msg);
			return;
		}

		int type = buf.readUnsignedShort();
		buf.rIndex += 2;

		if (type != 0x0001 || buf.readInt() != MAGIC) {
			error(ctx, msg);
			return;
		}

		DynByteBuf slice = buf.slice(length+12);
		ByteList reply = packet(address, slice);
		if (ctx.channel().isTCP()) {
			ctx.channelWrite(reply);
			ctx.channel().closeGracefully();
		} else {
			ctx.channelWrite(new DatagramPkt(address, reply));
		}
	}
	private void error(ChannelCtx ctx, Object msg) throws IOException {
		if (dispatch) ctx.channelRead(msg);
		else if (ctx.channel().isTCP()) ctx.close();
	}
	private ByteList packet(InetSocketAddress addr, DynByteBuf msg) {
		byte[] transactionId = msg.readBytes(12);
		IntSet unknown = new IntSet();

		while (msg.isReadable()) {
			int type = msg.readUnsignedShort();
			int len = msg.readUnsignedShort();
			msg.rIndex += len;

			if (type >= 0x8000) continue;
			//     0x0006: USERNAME
			//     0x0008: MESSAGE-INTEGRITY
			//     0x0014: REALM
			//     0x0015: NONCE
			unknown.add(type);
		}

		// 报文中携带MAPPED-ADDRESS、XOR-MAPPED-ADDRESS、RESPONSE-ORIGIN属性。
		ByteList b = IOUtil.getSharedByteBuf();
		b.putShort(unknown.isEmpty() ? 0x0101 : 0x0111).putShort(0).putInt(MAGIC).put(transactionId);

		// MAPPED-ADDRESS (obsoleted) and XOR-MAPPED-ADDRESS
		byte[] address = addr.getAddress().getAddress();
		if (address.length == 4) {
			// 32-bit ipv4
			b.putShort(0x0001).putShort(8).put(0).put(0x01).putShort(addr.getPort()).put(address);
			for (int i = 0; i < 4; i++) address[i] ^= MAGIC >>> ((3-i)<<3);
			b.putShort(0x0020).putShort(8).put(0).put(0x01).putShort(addr.getPort() ^ (MAGIC>>>16)).put(address);
		} else {
			// 128-bit ipv6
			assert address.length == 16 : "address neither ipv4 nor ipv6";

			for (int i = 0; i < 4; i++) address[i] ^= MAGIC >>> ((3-i)<<3);
			for (int i = 4; i < 16; i++) address[i] ^= transactionId[i-4];
			b.putShort(0x0001).putShort(20).put(0).put(0x02).putShort(addr.getPort()).put(address);
			b.putShort(0x0020).putShort(20).put(0).put(0x02).putShort(addr.getPort() ^ (MAGIC>>>16)).put(address);
		}

		if (myLocalAddress != null) {
			address = myLocalAddress.getAddress().getAddress();
			boolean myipv4 = address.length == 4;
			b.putShort(0x802b).putShort(myipv4 ? 8 : 20).put(0).put(myipv4 ? 0x01 : 0x02).putShort(myLocalAddress.getPort()).put(address);
		}

		b.putShort(0x8022).putUTF("STUNeR");

		if (!unknown.isEmpty()) {
			b.putShort(0x0010).putShort(4).putInt((4 << 8) | 20);
			b.putShort(0x000A).putShort(2*unknown.size());
			for (Integer i : unknown) b.putInt(i);
		}

		b.putShort(2, b.wIndex()-20+8);

		int crc32 = CRC32.crc32(b.list, 0, b.wIndex());
		b.putShort(0x8028).putShort(4).putInt(crc32 ^ 0x5354554e);

		return b;
	}
}