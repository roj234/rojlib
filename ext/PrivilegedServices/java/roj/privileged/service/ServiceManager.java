package roj.privileged.service;

import roj.concurrent.TaskPool;
import roj.crypt.CryptoFactory;
import roj.crypt.KeyType;
import roj.net.ServerLaunch;
import roj.net.handler.Compress;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSHandler;
import roj.net.mss.MSSKeyPair;
import roj.net.rpc.RPCServerImpl;
import roj.privileged.api.FirewallManager;
import roj.util.JVM;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2025/10/17 13:43
 */
public class ServiceManager {
	public static void main(String[] args) throws Exception {
		JVM.AccurateTimer.setEventDriven();
		if (!JVM.isRoot()) {
			System.out.println("You need root permission to run PrivilegedServices");
			return;
		}

		CryptoFactory.register();

		var crypto = new MSSContext().setALPN("PrivilegedRPC");
		var server = new RPCServerImpl(TaskPool.cpu());

		var manager = new FirewallManagerImpl();
		Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown));

		crypto.setCertificate(new MSSKeyPair(KeyType.getInstance("EdDSA").generateKey()));
		server.registerImplementation(FirewallManager.class, manager);

		int port = Integer.parseInt(args[0]);

		ServerLaunch.tcp().bind2(InetAddress.getLoopbackAddress(), port).initializator(channel -> {
			channel.addLast("tls", new MSSHandler(crypto.serverEngine()));
			channel.addLast("compression", new Compress());
			server.attachTo(channel);
		}).launch();

		System.out.println("RPC server now listening on localhost:"+port);
	}
}
