package roj.net.cross;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.text.TextUtil;
import roj.text.logging.LoggingStream;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author Roj233
 * @since 2021/9/11 2:00
 */
public class AEGuiClient {
	public static void main(String[] args) throws IOException, ParseException {
		if (!NIOUtil.available()) {
			JOptionPane.showMessageDialog(null, "请使用Java8!");
			return;
		}

		if (args.length == 0) {
			args = new String[] {"asc.json"};
		}

		CMapping cfg = JSONParser.parses(IOUtil.readUTF(new FileInputStream(args[0])), JSONParser.LITERAL_KEY).asMap();

		String[] text = TextUtil.split1(cfg.getString("url"), ':');
		if (text.length == 0) {
			JOptionPane.showMessageDialog(null, "服务器端口有误");
			return;
		}

		InetAddress host;
		try {
			host = text.length == 1 ? null : InetAddress.getByName(text[0]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "未知的主机");
			return;
		}

		InetSocketAddress addr;
		try {
			addr = new InetSocketAddress(host, Integer.parseInt(text[text.length - 1]));
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(null, "服务器端口有误");
			return;
		}

		System.out.println("登录中......");

		Util.out = new LoggingStream();
		System.setOut(Util.out);
		System.setErr(Util.out);

		client = new AEClient(addr, cfg.getString("room"), cfg.getString("pass"));
		client.start();

		Util.registerShutdownHook(client);

		Thread.currentThread().setName("Set PortMap");
		try {
			client.awaitLogin();
			CList list = cfg.getOrCreateList("ports");
			char[] ports = client.portMap;
			if (ports.length != list.size()) {
				CList list2 = new CList(ports.length);
				for (char p : ports) {
					list2.add(p);
				}
				client.shutdown();
				System.out.println("端口映射不匹配: ");
				System.out.println("您的定义: " + list.toShortJSONb());
				System.out.println("服务端的定义: " + list2.toShortJSONb());
				return;
			}
			for (int i = 0; i < ports.length; i++) {
				ports[i] = (char) Math.max(list.get(i).asInteger(), 0);
			}
			client.notifyPortMapModified();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	static AEClient client;
}
