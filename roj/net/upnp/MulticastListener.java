package roj.net.upnp;

import roj.concurrent.Shutdownable;
import roj.net.http.HttpHead;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

/**
 * @author solo6975
 * @since 2022/1/16 0:34
 */
class MulticastListener extends Thread implements Shutdownable {
	DeviceHandler handler;

	MulticastListener(DeviceHandler handler) {
		this.handler = handler;
		setName("UPnP Multicast listener");
		setDaemon(true);
		start();
	}

	@Override
	public void run() {
		handler.threadStart(this);

		UTFCoder coder = new UTFCoder();
		ByteList bl = coder.byteBuf;
		bl.ensureCapacity(UPnPUtil.MTU);
		DatagramPacket pkt = new DatagramPacket(bl.list, UPnPUtil.MTU);

		try (DatagramSocket rcv = new DatagramSocket(UPnPUtil.UPNP_ADDRESS)) {
			rcv.setSoTimeout(500);
			while (!Thread.interrupted()) {
				try {
					rcv.receive(pkt);
					UPnPDevice device = new UPnPDevice(null, pkt.getAddress());

					bl.wIndex(pkt.getLength());
					HttpHead head = HttpHead.parse(bl);

					String error;
					if (!"*".equals(head.getPath())) {error = "Illegal HTTP url " + head.getPath();} else if (!"NOTIFY".equals(head.getMethod())) {
						error = "Illegal HTTP method " + head.getMethod();
					} else error = UPnPDevice.init(head, device);
					if (error == null) {
						handler.onDeviceJoin(device);
					} else {
						handler.onError(error);
					}
				} catch (SocketTimeoutException ignored) {
					if (Thread.interrupted()) return;
				} catch (Throwable e) {
					handler.onException(e);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			handler.threadStop();
			UPnPDevice.multicastListener = null;
		}
	}

	@Override
	public boolean wasShutdown() {
		return !isAlive();
	}

	@Override
	public void shutdown() {
		interrupt();
	}
}
