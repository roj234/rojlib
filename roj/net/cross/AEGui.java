/*
 * Created by JFormDesigner on Thu Nov 02 01:20:45 CST 2023
 */

package roj.net.cross;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.crypt.ILProvider;
import roj.crypt.KeyType;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.math.MutableInt;
import roj.net.NetworkUtil;
import roj.net.ch.*;
import roj.net.ch.osi.ClientLaunch;
import roj.net.cross.server.AEServer;
import roj.net.http.srv.HttpServer11;
import roj.net.http.srv.StringResponse;
import roj.net.mss.JPrivateKey;
import roj.text.TextUtil;
import roj.ui.OnChangeHelper;
import roj.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

import static roj.net.cross.Constants.MAX_PORTS;
import static roj.net.cross.Constants.PHS_UPDATE_MOTD;

/**
 * @author Roj234
 */
public class AEGui extends JFrame implements ChannelHandler {
	static IAEClient client;
	static final SelectorLoop loop = new SelectorLoop(null, "AE 网络IO", 1, 30000, 1);

	static KeyType keyType;
	static KeyPair userCert;
	static byte[] userId;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		uiRefreshPort.setEnabled(true);
		uiLogout.setEnabled(true);

		setEnabled(true);
		setVisible(true);

		uiConnect.dispose();
		if (client instanceof AEClient) {
			AEClient c = (AEClient) client;
			uiCustomMotd.setText(c.roomMotd);

			DefaultListModel<Object> m = (DefaultListModel<Object>) uiPortList.getModel();
			m.clear();
			for (char port : c.portMap) {
				m.addElement(new PortGroupC(port, null));
			}

			uiRefreshPort.setText("启动服务");
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (AEServer.server != null) AEServer.server.shutdown();
		onLogout("很遗憾，今日屠龙宝刀已送完");
	}

	public static void main(String[] args) throws IOException, ParseException {
		ILProvider.register();
		UIUtil.systemLook();
		keyType = KeyType.getInstance("EdDSA");
		AEGui f = new AEGui();

		if (!NIOUtil.available()) {
			JOptionPane.showMessageDialog(null, "请使用Java8!");
			return;
		}

		if (args.length > 0) {
			CMapping cfg = JSONParser.parses(IOUtil.readUTF(new FileInputStream(args[0])), JSONParser.LITERAL_KEY).asMap();

			f.uiServer.setText(cfg.getString("server"));
			f.uiRoom.setText(cfg.getString("room"));
			f.uiUser.setText(cfg.getString("user"));
			f.uiCustomMotd.setText(cfg.getString("motd"));
		}

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		f.onLogout("屠龙宝刀点击就送");
	}

	private void onLogout(String msg) {
		if (!uiCreateRoom.isSelected()) {
			setVisible(false);
			setEnabled(false);
		} else {
			uiLocalPort.setEnabled(true);
			uiDelPort.setVisible(true);
		}

		uiLogout.setEnabled(false);
		uiRefreshPort.setEnabled(false);
		uiConnect.setEnabled(true);
		uiConnect.setVisible(true);
		uiServer.setEnabled(true);
		uiRoom.setEnabled(true);
		uiUser.setEnabled(true);
		uiLogin.setEnabled(true);
		uiLogin.setText(msg);
	}

	public AEGui() {
		initComponents();

		OnChangeHelper evt = new OnChangeHelper(this);
		evt.addRoot(uiConnect);

		uiConnect.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (!isVisible() || uiCreateRoom.isSelected()) System.exit(0);
			}
		});

		DefaultListModel<Object> model = new DefaultListModel<>();
		uiPortList.setModel(model);

		//
		uiRefreshPort.addActionListener(e -> {
			SimpleList<Pipe> pipes;
			synchronized (client.pipes) {
				pipes = new SimpleList<>(client.pipes.values());
			}
			for (Pipe value : pipes) {
				try {
					value.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			if (client instanceof AEClient) {
				AEClient c = (AEClient) client;
				for (int i = 0; i < model.size(); i++) {
					PortGroupC p = (PortGroupC) model.get(i);
					p.name = null;
					c.portMap[i] = (char) p.to;
				}
				int errorOn = c.portMapChanged();
				if (errorOn >= 0) {
					PortGroupC p = (PortGroupC) model.get(errorOn);
					p.name = "端口被占用!";
				}

				// fire update events
				for (int i = 0; i < model.size(); i++)
					model.set(i, model.get(i));

				uiRefreshPort.setText("重启服务");
			}
		});
		// Host
		uiCreateRoom.addActionListener(e -> {
			uiCreateRoom.setEnabled(false);
			uiDirectServer.setVisible(true);

			setEnabled(true);
			setVisible(true);

			uiCustomMotd.setEditable(true);
			uiCustomMotd.setText("在此编辑您房间的介绍");

			uiRemotePort.setVisible(false);
			uiDelPort.setVisible(true);
			uiLocalPort.setEnabled(true);
			Consumer<JTextField> lpcb = e1 -> {
				if (model.size() >= MAX_PORTS) return;
				if (TextUtil.isNumber(uiLocalPort.getText()) != 0) return;

				int value = Integer.parseInt(uiLocalPort.getText());
				if (value < 0 || value > 0xFFFF) return;

				MutableInt elem = new MutableInt(value);
				if (!model.contains(elem)) {
					model.addElement(elem);
					uiLocalPort.setText("");
				}
			};
			evt.addEventListener(uiLocalPort, lpcb);
			uiPortList.addListSelectionListener(e1 -> {
				uiDelPort.setEnabled(uiPortList.getSelectedIndex() >= 0);
			});
			uiDelPort.addActionListener(e1 -> {
				int[] idx = uiPortList.getSelectedIndices();
				for (int j = idx.length-1; j >= 0; j--) {
					model.remove(idx[j]);
				}
				uiDelPort.setEnabled(false);
				lpcb.accept(null);
			});

			evt.addEventListener(uiCustomMotd, e1 -> {
				if (client != null && !client.shutdown) {
					client.writeAsync(IOUtil.getSharedByteBuf().put(PHS_UPDATE_MOTD).putZhCnData(uiCustomMotd.getText()));
				}
			});
		});
		// Host+Direct server
		uiKeyGen.addActionListener(e -> {
			userCert = keyType.generateKey();
			setUserCert();
		});
		uiKeySL.addActionListener(e -> {
			if (uiKeySL.getText().startsWith("读取")) {
				File file = UIUtil.fileLoadFrom("选择密钥文件", uiConnect);
				String pass = JOptionPane.showInputDialog(uiConnect, "请输入保存时设置的密码", "输入密码", JOptionPane.QUESTION_MESSAGE);
				try {
					userCert = keyType.loadKey(pass.getBytes(StandardCharsets.UTF_8), file);
					if (userCert == null) uiKeySL.setText("读取失败");
					else setUserCert();
				} catch (Exception e1) {
					uiKeySL.setText("读取失败");
					e1.printStackTrace();
				}
			} else {
				File file = UIUtil.fileSaveTo("选择密钥文件", "key.bin", uiConnect);
				String pass = JOptionPane.showInputDialog(uiConnect, "您必须设置密码来保护私钥", "设置密码", JOptionPane.INFORMATION_MESSAGE);
				try {
					keyType.saveKey(userCert, pass.getBytes(StandardCharsets.UTF_8), file);
					uiKeySL.setEnabled(false);
					uiKeySL.setText("保存");
				} catch (Exception e1) {
					uiKeySL.setText("保存失败");
					e1.printStackTrace();
				}
			}
		});

		uiLogin.addActionListener(e -> {
			if (userCert == null) {
				JOptionPane.showMessageDialog(uiConnect, "请生成或读取用户ID！");
				return;
			}
			uiKeyGen.setEnabled(false);

			boolean first = client == null;

			if (uiCreateRoom.isSelected()) {
				if (model.size() == 0) {
					JOptionPane.showMessageDialog(uiConnect, "请在弹出的窗口中设置端口！");
					return;
				}
				client = new AEHost(loop);
			} else {
				client = new AEClient(loop);
			}

			if (first) {
				String type;
				if (uiCreateRoom.isSelected()) {
					if (uiDirectServer.isSelected()) type = "Server";
					else type = "Host";
				} else {
					type = "Client";

					uiPortList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
					uiPortList.addListSelectionListener(e1 -> {
						int i = uiPortList.getSelectedIndex();
						if (i >= 0) {
							PortGroupC group = (PortGroupC) model.get(i);
							uiRemotePort.setText("远程端口: "+group.from);
							uiLocalPort.setText(Integer.toString(group.to));
							uiLocalPort.setEnabled(true);
						} else {
							uiLocalPort.setText("");
							uiLocalPort.setEnabled(false);
						}
					});
					evt.addEventListener(uiLocalPort, e1 -> {
						int i = uiPortList.getSelectedIndex();
						if (i >= 0) {
							String text = uiLocalPort.getText();
							if (TextUtil.isNumber(text) != 0) return;
							int value = Integer.parseInt(text);
							if (value < 0 || value > 0xFFFF) return;

							PortGroupC group = (PortGroupC) model.get(i);
							group.to = value;
							model.setElementAt(group, i); // fire event
						}
					});
				}
				setTitle(getTitle()+" | "+type);
			}

			uiServer.setEnabled(false);
			uiRoom.setEnabled(false);
			uiUser.setEnabled(false);
			uiLogin.setEnabled(false);

			// 这两个不会再可用。
			uiCreateRoom.setEnabled(false);
			uiDirectServer.setEnabled(false);

			uiDelPort.setVisible(false);
			uiLocalPort.setEnabled(false);

			uiLogin.setText("登录中");

			try {
				JPrivateKey key = new JPrivateKey(userCert);
				IAEClient.client_factory.key(key);

				ClientLaunch launch = ClientLaunch.tcp();
				launch.loop(loop).connect(NetworkUtil.getConnectAddress(uiServer.getText()));

				if (client instanceof AEHost) {
					char[] ports = new char[model.size()];
					for (int i = 0; i < model.size(); i++) {
						ports[i] = (char) ((Number) model.get(i)).intValue();
					}

					if (uiDirectServer.isSelected()) {
						CtxEmbedded[] pair = CtxEmbedded.createPair();

						AEServer server = new AEServer(launch.address(), 512, key);
						server.launch.loop(loop);
						server.start();
						AEServer.localUserId = userId;

						((AEHost) client).init(null, uiRoom.getText(), uiCustomMotd.getText(), ports);
						MyChannel ch = client.handlers;
						ch.addLast("open_check", this);
						ch.open();
						return;
					}

					((AEHost) client).init(launch, uiRoom.getText(), uiCustomMotd.getText(), ports);
				} else {
					((AEClient) client).init(launch, uiUser.getText(), uiRoom.getText());
				}

				launch.channel().addLast("open_check", this);
				launch.launch();
			} catch (Exception ex) {
				ex.printStackTrace();
				onLogout("登录失败");
			}
		});

		uiLogout.addActionListener(e -> {
			uiLogout.setEnabled(false);
			client.logout(client.handlers.handler("open_check"));
		});

		uiWeb.addActionListener(e -> {
			try {
				JOptionPane.showMessageDialog(this, "但是还不能用！ port=1500");
				runServer(1500);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});
		try {
			runServer(1500);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void setUserCert() {
		try {
			uiKeyGen.setEnabled(false);
			uiKeySL.setText("保存");
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			String digest = TextUtil.bytes2hex(userId = sha.digest(userCert.getPublic().getEncoded()));
			uiKeyDigest.setText(digest);
			uiKeyDigest.setToolTipText(digest);
		} catch (NoSuchAlgorithmException ignored) {}
	}

	private static final MyHashMap<String, String> res = new MyHashMap<>();
	private static String res(String name) throws IOException {
		String v = res.get(name);
		if (v == null) res.put(name, v = IOUtil.readResUTF("META-INF/html/"+name));
		return v;
	}
	private static void runServer(int port) throws IOException {
		HttpServer11.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (request, rh) -> {
			AEHost host = (AEHost) client;
			switch (request.path()) {
				case "bundle.min.css": return new StringResponse(res("bundle.min.css"), "text/css");
				case "bundle.min.js": return new StringResponse(res("bundle.min.js"), "text/javascript");
				case "": return new StringResponse(res("client_owner.html"), "text/html");
				case "user_list":
					CList lx = new CList();
					for (IntMap.Entry<AEHost.Client> entry : host.clients.selfEntrySet()) {
						CMapping map = new CMapping();
						map.put("id", entry.getIntKey());
						map.put("ip", entry.getValue().addr);
						map.put("time", entry.getValue().time);
						CList pipes = map.getOrCreateList("pipes");
						for (Pipe pipe : host.pipes.values()) {
							PipeInfoClient att = (PipeInfoClient) pipe.att;
							if (att.clientId == entry.getIntKey()) {
								CMapping map1 = new CMapping();
								map1.put("up", pipe.downloaded);
								map1.put("down", pipe.uploaded);
								map1.put("idle", pipe.idleTime);
								map1.put("id", att.pipeId);
								map1.put("port", host.portMap[att.portId]);
								pipes.add(map1);
							}
						}
						lx.add(map);
					}
					return new StringResponse(lx.toShortJSONb(), "application/json");
				case "kick_user":
					int count = 0;
					String[] arr = request.postFields().get("users").split(",");
					int[] arrs = new int[arr.length];
					for (int i = 0; i < arr.length; i++) {
						arrs[i] = Integer.parseInt(arr[i]);
					}
					host.kickSome(arrs);
					return new StringResponse("{\"count\":" + arr.length + "}", "application/json");
			}
			return null;
		}).launch();
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		uiLogout = new JButton();
		uiWeb = new JButton();
		JScrollPane scrollPane1 = new JScrollPane();
		uiPortList = new JList<>();
		uiRefreshPort = new JButton();
		JLabel label4 = new JLabel();
		uiRemotePort = new JLabel();
		JLabel label6 = new JLabel();
		uiLocalPort = new JTextField();
		uiDelPort = new JButton();
		JLabel label7 = new JLabel();
		JScrollPane scrollPane2 = new JScrollPane();
		uiCustomMotd = new JTextPane();
		uiConnect = new JDialog();
		uiServer = new JTextField();
		uiLogin = new JButton();
		uiRoom = new JTextField();
		JLabel label1 = new JLabel();
		JLabel label2 = new JLabel();
		JLabel label3 = new JLabel();
		uiUser = new JTextField();
		uiCreateRoom = new JCheckBox();
		uiDirectServer = new JCheckBox();
		uiKeyDigest = new JLabel();
		uiKeyGen = new JButton();
		uiKeySL = new JButton();

		//======== this ========
		setTitle("AE-FakeP2P");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- uiLogout ----
		uiLogout.setText("\u65ad\u5f00\u8fde\u63a5");
		uiLogout.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiLogout);
		uiLogout.setBounds(new Rectangle(new Point(246, 0), uiLogout.getPreferredSize()));

		//---- uiWeb ----
		uiWeb.setText("\u7f51\u9875\u7aef");
		contentPane.add(uiWeb);
		uiWeb.setBounds(new Rectangle(new Point(195, 125), uiWeb.getPreferredSize()));

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiPortList);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(5, 25, 160, 150);

		//---- uiRefreshPort ----
		uiRefreshPort.setText("\u91cd\u542f\u670d\u52a1");
		uiRefreshPort.setMargin(new Insets(1, 2, 1, 2));
		contentPane.add(uiRefreshPort);
		uiRefreshPort.setBounds(new Rectangle(new Point(109, 2), uiRefreshPort.getPreferredSize()));

		//---- label4 ----
		label4.setText("\u7aef\u53e3\u6620\u5c04");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(5, 5), label4.getPreferredSize()));

		//---- uiRemotePort ----
		uiRemotePort.setText("\u8fdc\u7a0b\u7aef\u53e3: \u672a\u9009\u4e2d");
		contentPane.add(uiRemotePort);
		uiRemotePort.setBounds(new Rectangle(new Point(170, 30), uiRemotePort.getPreferredSize()));

		//---- label6 ----
		label6.setText("\u672c\u5730\u7aef\u53e3:");
		contentPane.add(label6);
		label6.setBounds(new Rectangle(new Point(170, 55), label6.getPreferredSize()));

		//---- uiLocalPort ----
		uiLocalPort.setEnabled(false);
		contentPane.add(uiLocalPort);
		uiLocalPort.setBounds(228, 52, 40, uiLocalPort.getPreferredSize().height);

		//---- uiDelPort ----
		uiDelPort.setText("\u5220\u9664");
		uiDelPort.setEnabled(false);
		uiDelPort.setVisible(false);
		contentPane.add(uiDelPort);
		uiDelPort.setBounds(new Rectangle(new Point(240, 25), uiDelPort.getPreferredSize()));

		//---- label7 ----
		label7.setText("motd");
		contentPane.add(label7);
		label7.setBounds(new Rectangle(new Point(275, 165), label7.getPreferredSize()));

		//======== scrollPane2 ========
		{

			//---- uiCustomMotd ----
			uiCustomMotd.setEditable(false);
			scrollPane2.setViewportView(uiCustomMotd);
		}
		contentPane.add(scrollPane2);
		scrollPane2.setBounds(5, 180, 295, 135);

		contentPane.setPreferredSize(new Dimension(305, 320));
		pack();
		setLocationRelativeTo(getOwner());

		//======== uiConnect ========
		{
			uiConnect.setTitle("\u4eca\u665a\u516b\u70b9\uff0c\u6211\u5728\u6c99\u57ce\u7b49\u4f60");
			uiConnect.setResizable(false);
			Container uiConnectContentPane = uiConnect.getContentPane();
			uiConnectContentPane.setLayout(null);

			//---- uiServer ----
			uiServer.setText("127.0.0.1:12003");
			uiConnectContentPane.add(uiServer);
			uiServer.setBounds(45, 5, 145, uiServer.getPreferredSize().height);

			//---- uiLogin ----
			uiLogin.setMargin(new Insets(2, 2, 2, 2));
			uiLogin.setEnabled(false);
			uiConnectContentPane.add(uiLogin);
			uiLogin.setBounds(10, 100, 175, 23);
			uiConnectContentPane.add(uiRoom);
			uiRoom.setBounds(45, 30, 145, uiRoom.getPreferredSize().height);

			//---- label1 ----
			label1.setText("\u670d\u52a1\u5668");
			label1.setLabelFor(uiServer);
			uiConnectContentPane.add(label1);
			label1.setBounds(new Rectangle(new Point(5, 8), label1.getPreferredSize()));

			//---- label2 ----
			label2.setText("\u623f  \u95f4");
			label2.setLabelFor(uiRoom);
			uiConnectContentPane.add(label2);
			label2.setBounds(new Rectangle(new Point(5, 34), label2.getPreferredSize()));

			//---- label3 ----
			label3.setText("\u6635  \u79f0");
			label3.setLabelFor(uiUser);
			uiConnectContentPane.add(label3);
			label3.setBounds(new Rectangle(new Point(5, 58), label3.getPreferredSize()));
			uiConnectContentPane.add(uiUser);
			uiUser.setBounds(45, 55, 145, uiUser.getPreferredSize().height);

			//---- uiCreateRoom ----
			uiCreateRoom.setText("\u6211\u662f\u623f\u4e3b");
			uiConnectContentPane.add(uiCreateRoom);
			uiCreateRoom.setBounds(new Rectangle(new Point(2, 78), uiCreateRoom.getPreferredSize()));

			//---- uiDirectServer ----
			uiDirectServer.setText("\u6211\u8fd8\u662f\u670d\u52a1\u5668");
			uiDirectServer.setVisible(false);
			uiConnectContentPane.add(uiDirectServer);
			uiDirectServer.setBounds(new Rectangle(new Point(72, 78), uiDirectServer.getPreferredSize()));

			//---- uiKeyDigest ----
			uiKeyDigest.setText("\u7528\u6237ID");
			uiConnectContentPane.add(uiKeyDigest);
			uiKeyDigest.setBounds(5, 130, 190, uiKeyDigest.getPreferredSize().height);

			//---- uiKeyGen ----
			uiKeyGen.setText("\u751f\u6210");
			uiKeyGen.setMargin(new Insets(1, 0, 1, 0));
			uiConnectContentPane.add(uiKeyGen);
			uiKeyGen.setBounds(2, 145, 60, uiKeyGen.getPreferredSize().height);

			//---- uiKeySL ----
			uiKeySL.setText("\u8bfb\u53d6");
			uiKeySL.setMargin(new Insets(1, 0, 1, 0));
			uiConnectContentPane.add(uiKeySL);
			uiKeySL.setBounds(135, 145, 60, uiKeySL.getPreferredSize().height);

			uiConnectContentPane.setPreferredSize(new Dimension(200, 170));
			uiConnect.pack();
			uiConnect.setLocationRelativeTo(uiConnect.getOwner());
		}
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JButton uiLogout;
	private JButton uiWeb;
	private JList<Object> uiPortList;
	private JButton uiRefreshPort;
	private JLabel uiRemotePort;
	private JTextField uiLocalPort;
	private JButton uiDelPort;
	private JTextPane uiCustomMotd;
	private JDialog uiConnect;
	private JTextField uiServer;
	private JButton uiLogin;
	private JTextField uiRoom;
	private JTextField uiUser;
	private JCheckBox uiCreateRoom;
	private JCheckBox uiDirectServer;
	private JLabel uiKeyDigest;
	private JButton uiKeyGen;
	private JButton uiKeySL;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on

	static final class PortGroupC {
		String name;
		int from, to;

		public PortGroupC(char port, String desc) {
			name = desc;
			from = to = port;
		}

		@Override
		public String toString() { return from+" => "+to+(name==null?"":"("+name+")"); }
	}
}
