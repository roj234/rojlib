package roj.staging.tls;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.EmbeddedChannel;
import roj.net.mss.*;
import roj.util.ByteList;
import roj.util.JVM;

import java.io.IOException;
import java.security.KeyPairGenerator;

/**
 * @author Roj234
 * @since 2025/09/04 15:25
 */
public class TLSTest {
	public static void main(String[] args) throws Exception {
		var ch = EmbeddedChannel.createPair();

		var server = ch[0];
		var client = ch[1];

		var context = new MSSContext();
		context.setCertificate(new MSSKeyPair(KeyPairGenerator.getInstance("EdDSA").generateKeyPair()));

		server.addLast("", new TLSHandler(new TLSEngineServer(context)));
		client.addLast("", new TLSHandler(new TLSEngineClient(context)));
		server.addLast("s", new ChannelHandler() {
			@Override
			public void channelOpened(ChannelCtx ctx) throws IOException {
				System.out.println("server open");
				ctx.channelWrite(new ByteList().putUTFData("server data"));
			}

			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				System.out.println("read: "+msg);
				new Throwable().printStackTrace();
			}
		});
		client.addLast("c", new ChannelHandler() {
			@Override
			public void channelOpened(ChannelCtx ctx) throws IOException {
				System.out.println("client open");
				ctx.channelWrite(new ByteList().putUTFData("client data"));
			}

			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				System.out.println("read: "+msg);
				new Throwable().printStackTrace();
			}
		});

		client.open();
		JVM.AccurateTimer.parkForMe();
	}
}
