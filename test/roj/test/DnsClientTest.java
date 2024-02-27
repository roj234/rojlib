package roj.test;

import roj.plugins.dns.DnsServer;
import roj.util.ByteList;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * @author solo6975
 * @since 2021/7/24 17:28
 */
public class DnsClientTest {
	public static void main(String[] args) throws IOException {
		ByteList w = new ByteList();
		w.putShort(0).putShort(0).putShort(1).putShort(0).putShort(0).putShort(0);
		DnsServer.writeDomain(w, "www.baidu.com");
		w.putShort(DnsServer.Q_ANY).putShort(DnsServer.C_INTERNET);

		DatagramPacket pkt = new DatagramPacket(w.list, w.wIndex());
		pkt.setPort(53);
		pkt.setAddress(InetAddress.getLoopbackAddress());
		DatagramSocket socket = new DatagramSocket(41, InetAddress.getLoopbackAddress());
		socket.send(pkt);


		pkt.setLength(w.list.length);
		socket.receive(pkt);
		w.wIndex(pkt.getLength());
		System.out.println(w);
	}
}