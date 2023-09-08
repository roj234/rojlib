package roj.misc;

import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.osi.ServerLaunch;
import roj.net.http.srv.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/3/20 22:53
 */
public class Websocketd extends WebsocketManager implements Router {
	List<String> cmd;

	public static void main(String[] args) throws IOException {
		int i;
		InetSocketAddress addr = new InetSocketAddress(8080);
		for (i = 0; i < args.length; i++) {
			String arg = args[i];
			if (!arg.startsWith("--")) break;
			int j = arg.indexOf('=');
			if ("--addr".equals((j < 0 ? arg : arg.substring(0, j)))) {
				addr = NetworkUtil.getListenAddress(arg.substring(j + 1));
			}
		}
		ArrayList<String> cmd = new ArrayList<>(args.length - i);
		while (i < args.length) {
			cmd.add(args[i++]);
		}

		if (cmd.isEmpty()) {
			System.out.println("Websocketd [--addr=?] <command-line>");
			return;
		}

		Websocketd ws = new Websocketd();
		ws.cmd = cmd;

		ServerLaunch hs = HttpServer11.simple(addr, 233, ws);
		hs.launch();
		ws.loop = hs.getLoop();

		System.out.println("监听 " + hs.address());
	}

	@Override
	protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
		try {
			return new CmdWorker(cmd);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	static final MyHashMap<String, String> tmp = new MyHashMap<>();
	private static String res(String name) throws IOException {
		String v = tmp.get(name);
		if (v == null) tmp.put(name, v = IOUtil.readResUTF("META-INF/html/" + name));
		return v;
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		switch (req.path()) {
			case "bundle.min.css":
				return new StringResponse(res("bundle.min.css"), "text/css");
			case "bundle.min.js":
				return new StringResponse(res("bundle.min.js"), "text/javascript");
			case "":
				if ("websocket".equals(req.getField("Upgrade"))) {
					return switchToWebsocket(req, rh);
				}
				return new StringResponse(res("websocketd_ui.html"), "text/html");
			default:
				return rh.code(404).returnNull();
		}
	}

	static final class CmdWorker extends WebsocketHandler {
		Process process;

		CharsetEncoder sysEnc;
		CharsetDecoder sysDec;

		CharBuffer tmp1;
		ByteBuffer tmp2, sndRem;

		CmdWorker(List<String> cmd) throws IOException {
			process = new ProcessBuilder().command(cmd).redirectErrorStream(true)
				.redirectInput(ProcessBuilder.Redirect.PIPE)
				.redirectOutput(ProcessBuilder.Redirect.PIPE).start();

			tmp2 = ByteBuffer.allocate(1024);

			if (TextUtil.DefaultOutputCharset != StandardCharsets.UTF_8) {
				sysEnc = TextUtil.DefaultOutputCharset.newEncoder();
				sysDec = TextUtil.DefaultOutputCharset.newDecoder();

				tmp1 = CharBuffer.allocate(1024);
				sndRem = ByteBuffer.allocate(8);
				sndRem.flip();
			}
			System.out.println("会话 " + Integer.toHexString(hashCode()) + " 开始");
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);

			System.out.println("会话 " + Integer.toHexString(hashCode()) + " 结束: " + errCode + "@" + errMsg);
			process.destroy();
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);

			if (!process.isAlive()) {
				error(ERR_OK, "进程终止");
				return;
			}

			InputStream in = process.getInputStream();

			if (in.available() > 0) {
				CharBuffer tmpChar = this.tmp1;
				ByteBuffer inByte = this.tmp2;
				ByteList outByte = IOUtil.getSharedByteBuf();

				inByte.clear();
				if (sndRem != null) inByte.put(sndRem);

				do {
					int len = Math.min(in.available(), inByte.remaining());
					len = in.read(inByte.array(), inByte.position(), len);
					if (len <= 0) break;

					inByte.limit(inByte.position() + len).position(0);

					if (sysDec != null) {
						while (true) {
							tmpChar.clear();

							CoderResult r = sysDec.decode(inByte, tmpChar, false);
							if (r.isError() || r.isMalformed() || r.isUnmappable()) {
								error(ERR_UNEXPECTED, "charset decode error for " + TextUtil.DefaultOutputCharset);
							}

							if (tmpChar.position() == 0) {
								inByte.compact();
								break;
							}
							tmpChar.flip();

							send(FRAME_TEXT, outByte.putUTFData(tmpChar));
							outByte.clear();

							if (!inByte.hasRemaining()) {
								inByte.clear();
								break;
							}
						}
					} else {
						send(FRAME_TEXT, ByteList.wrap(inByte.array(), 0, len));
					}
				} while (in.available() > 0);

				if (sndRem != null) {
					sndRem.clear();
					inByte.flip();
					sndRem.put(inByte).flip();
				}
			}

			if (in.available() < 0) {
				error(ERR_OK, "进程终止");
			}
		}

		@Override
		protected void onData(int ph, DynByteBuf in) throws IOException {
			OutputStream out = process.getOutputStream();
			if (in.hasArray() && sysDec == null) {
				// UTF-8 Array Stream
				out.write(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
				in.rIndex = in.wIndex();
			} else if (sysDec == null) {
				// UTF-8 Direct Memory
				DynByteBuf tmp = ch.allocate(false, 1024);
				try {
					while (in.isReadable()) {
						int len = Math.min(in.readableBytes(), tmp.capacity());
						in.read(tmp.array(), tmp.arrayOffset(), len);
						out.write(tmp.array(), tmp.arrayOffset(), len);
					}
				} finally {
					ch.reserve(tmp);
				}
			} else {
				// Conversation

				CharBuffer tmp1 = this.tmp1;
				ByteBuffer sndBuf = this.tmp2;

				while (in.isReadable()) {
					tmp1.clear();
					ByteList.decodeUTF(Math.min(in.readableBytes(), tmp1.capacity()), tmp1, in);
					tmp1.flip();

					sndBuf.clear();

					CoderResult r = sysEnc.encode(tmp1, sndBuf, false);
					if (r.isError() || r.isMalformed() || r.isUnmappable()) {
						error(ERR_UNEXPECTED, "charset encode error for " + TextUtil.DefaultOutputCharset);
						return;
					}

					out.write(sndBuf.array(), 0, sndBuf.position());
				}
			}
			out.flush();
		}
	}
}
