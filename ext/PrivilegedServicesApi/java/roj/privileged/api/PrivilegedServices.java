package roj.privileged.api;

import roj.concurrent.Promise;
import roj.net.ClientLaunch;
import roj.net.handler.Compress;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSHandler;
import roj.net.rpc.RPCClientImpl;
import roj.net.rpc.api.RPCClient;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2025/10/17 15:33
 */
public class PrivilegedServices {
	public static Promise<RPCClient> getInstance(int localPort) throws IOException {
		var crypto = new MSSContext().setALPN("PrivilegedRPC");
		var client = new RPCClientImpl();

		ClientLaunch.tcp().connect(InetAddress.getLoopbackAddress(), localPort).initializator(channel -> {
			channel.addLast("tls", new MSSHandler(crypto.clientEngine()));
			channel.addLast("compression", new Compress());
			client.attachTo(channel);
		}).launch();

		return client.onOpened();
	}
}
