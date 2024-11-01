package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.net.ChannelCtx;
import roj.net.EmbeddedChannel;
import roj.net.Event;
import roj.net.MyChannel;
import roj.net.handler.MSSCrypto;
import roj.net.handler.Timeout;
import roj.net.http.Headers;
import roj.net.http.HttpHead;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2Exception;
import roj.net.http.h2.H2Setting;
import roj.net.http.h2.H2Stream;
import roj.text.Escape;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0015 0:23
 */
class FrpHostConnection extends FrpCommon implements Consumer<MyChannel> {
	private final FrpServer server;
	private final FrpRoom room;

	public FrpHostConnection(FrpServer server, FrpRoom room) {
		super(false);
		this.server = server;
		this.room = room;
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSCrypto(server.clientEngine()))
		  .addLast("timeout", new Timeout(60000, 1000))
		  .addLast("h2", this);
	}

	@Override
	protected void onOpened(ChannelCtx ctx) throws IOException {
		H2Setting setting = getLocalSetting();
		setting.push_enable = true;
		setting.max_frame_size = 65535;

		super.onOpened(ctx);

		var connect = new Headers();
		connect.put(":method", "CREATE");
		connect.put(":authority", room.name);
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

			String status = head.getField(":status");
			if (!status.equals("200")) {
				try {
					FrpServer.LOGGER.warn("登录失败({}): {}", status, Escape.decodeURI(head.getField(":error")));
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}

				man.streamError(id, H2Exception.ERROR_OK);
			} else {
				System.out.println(head);
				System.out.println("Host认证成功");
			}

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {
			switch (buf.readAscii(buf.readUnsignedByte())) {
				case "frp:motd":
					System.out.println("服务器MOTD:"+buf.readUTF(buf.readableBytes()));
					break;
			}
		}
	}

	final class Client extends FrpProxy {
		Client(int id) {super(id);}

		@Override
		protected void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException {
			//byte[] certHash = Base64.decode(head.getField("endpoint-id"), IOUtil.getSharedByteBuf(), Base64.B64_URL_SAFE_REV).toByteArray();
			String ipAddress = head.getField("endpoint-ip");

			var pair = EmbeddedChannel.createPair();

			init(pair[0], null);
			server.addHostConnection(ipAddress, pair[1]);

			super.onHeaderDone(man, head, hasData);
		}
	}
}
