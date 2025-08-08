package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.http.Headers;
import roj.http.h2.H2Connection;
import roj.http.h2.H2Exception;
import roj.http.h2.H2Setting;
import roj.http.h2.H2Stream;
import roj.net.ChannelCtx;
import roj.net.EmbeddedChannel;
import roj.net.Event;
import roj.net.MyChannel;
import roj.net.mss.MSSHandler;
import roj.net.handler.Timeout;
import roj.text.URICoder;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0:23
 */
class FrpHostConnection extends FrpCommon implements Consumer<MyChannel> {
	static final Logger LOGGER = Logger.getLogger("FRPHost");

	private final FrpServer server;
	private final FrpRoom room;

	public FrpHostConnection(FrpServer server, FrpRoom room) {
		super(false);
		this.server = server;
		this.room = room;
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSHandler(server.clientEngine()))
		  .addLast("timeout", new Timeout(2000, 1000))
		  .addLast("h2", this);
	}

	@Override
	protected void initSetting(H2Setting setting) {
		super.initSetting(setting);
		setting.push_enable = true;
		setting.max_frame_size = 65535;
	}

	@Override
	protected void onOpened() throws IOException {
		var connect = new Headers();
		connect.put(":method", "CREATE");
		//connect.put(":authority", room.name);
		connect.put(":scheme", "Frp");

		sendHeaderClient(connect, false);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		System.out.println("Host Connection Closed");
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(Timeout.READ_TIMEOUT) && ping(ping -> System.out.println("新的延迟: "+ping.getRTT()+"ms"))) {
			event.setResult(Event.RESULT_DENY);
		}
	}

	protected @NotNull H2Stream newStream(int id) {
		var stream = id == 1 ? new Control() : new Client(id);
		initStream(stream);
		return stream;
	}

	private static class Control extends FrpCommon.Control {
		@Override
		protected String headerEnd(H2Connection man) {
			Headers head = _header;

			String status = head.header(":status");
			if (!status.equals("200")) {
				try {
					LOGGER.warn("登录失败({}): {}", status, URICoder.decodeURI(head.header(":error")));
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}

				man.streamError(id, H2Exception.ERROR_OK);
			} else {
				LOGGER.info("房主登录成功: ILFrp/"+head.header("server"));
			}

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {
			switch (buf.readAscii(buf.readUnsignedByte())) {
				case "frp:motd":
					LOGGER.info("服务器欢迎消息:"+buf.readUTF(buf.readableBytes()));
					break;
			}
		}
	}

	final class Client extends FrpProxy {
		Client(int id) {super(id);}

		@Override
		protected String headerEnd(H2Connection man) throws IOException {
			super.headerEnd(man);

			String ipAddress = _header.header("endpoint-ip");

			var pair = EmbeddedChannel.createPair();

			init(pair[0], new PortMapEntry((char) 0, "RemoteIp["+ipAddress+"] via Bridge", false));
			server.addHostConnection(ipAddress, pair[1]);
			return null;
		}
	}
}
