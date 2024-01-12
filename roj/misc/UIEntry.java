/*
 * Created by JFormDesigner on Fri Jan 19 03:57:59 CST 2024
 */

package roj.misc;

import roj.asmx.launcher.EntryPoint;
import roj.ui.GuiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Roj234
 */
public class UIEntry extends JFrame {
	public static void main(String[] args) throws Exception {
		if (System.console() != null && (args.length == 0 || !args[0].equals("gui"))) EntryPoint.main(args); // launch DefaultPluginSystem
		else launchUI();
	}

	private static void launchUI() {
		GuiUtil.systemLook();
		UIEntry f = new UIEntry();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
	}

	private static void bind(JButton button, String klass) {
		button.addActionListener(e -> {
			button.setEnabled(false);
			try {
				JFrame frame = (JFrame) Class.forName(klass).newInstance();
				frame.pack();
				frame.setResizable(false);
				frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
				frame.setVisible(true);
				frame.addWindowListener(new WindowAdapter() {
					public void windowClosing(WindowEvent e) { button.setEnabled(true); }
				});
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
	}

	public UIEntry() {
		initComponents();
		bind(uiArchiver, "roj.archive.ui.QZArchiverUI");
		bind(uiUnarchiver, "roj.archive.ui.UnarchiverUI");
		bind(uiFindClass, "roj.asmx.misc.FindClass");
		bind(uiNovel, "roj.text.novel.NovelFrame");
		bind(uiNat, "roj.plugins.cross.AEGui");
		bind(uiMapper, "roj.asmx.mapper.MapperUI");
		bind(uiObfuscator, "roj.asmx.mapper.ObfuscatorUI");
		bind(uiLavacTest, "roj.compiler.LavacUI");
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		scrollPane1 = new JScrollPane();
		textArea1 = new JTextArea();
		uiArchiver = new JButton();
		uiUnarchiver = new JButton();
		uiFindClass = new JButton();
		uiMapper = new JButton();
		uiObfuscator = new JButton();
		uiLavacTest = new JButton();
		uiNat = new JButton();
		uiNovel = new JButton();

		//======== this ========
		setTitle("\u9009\u62e9\u9700\u8981\u8fdb\u5165\u7684GUI\u529f\u80fd");
		var contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(textArea1);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(20, 130, 355, 160);

		//---- uiArchiver ----
		uiArchiver.setText("7z\u5e76\u884c\u538b\u7f29");
		uiArchiver.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiArchiver);
		uiArchiver.setBounds(new Rectangle(new Point(45, 15), uiArchiver.getPreferredSize()));

		//---- uiUnarchiver ----
		uiUnarchiver.setText("7z\u5e76\u884c\u89e3\u538b");
		uiUnarchiver.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiUnarchiver);
		uiUnarchiver.setBounds(45, 40, 69, 21);

		//---- uiFindClass ----
		uiFindClass.setText("\u5e38\u91cf\u67e5\u8be2");
		uiFindClass.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiFindClass);
		uiFindClass.setBounds(195, 15, 69, 21);

		//---- uiMapper ----
		uiMapper.setText("\u6620\u5c04\u5668");
		uiMapper.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiMapper);
		uiMapper.setBounds(120, 15, 69, 21);

		//---- uiObfuscator ----
		uiObfuscator.setText("\u6df7\u6dc6\u5668");
		uiObfuscator.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiObfuscator);
		uiObfuscator.setBounds(120, 40, 69, 21);

		//---- uiLavacTest ----
		uiLavacTest.setText("LavacTest");
		uiLavacTest.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiLavacTest);
		uiLavacTest.setBounds(195, 40, 69, 21);

		//---- uiNat ----
		uiNat.setText("NAT\u7a7f\u900f\u5de5\u5177");
		uiNat.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiNat);
		uiNat.setBounds(270, 15, 80, 21);

		//---- uiNovel ----
		uiNovel.setText("\u5c0f\u8bf4\u6574\u7406\u5de5\u5177");
		uiNovel.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiNovel);
		uiNovel.setBounds(270, 40, 80, 21);

		contentPane.setPreferredSize(new Dimension(400, 325));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JScrollPane scrollPane1;
	private JTextArea textArea1;
	private JButton uiArchiver;
	private JButton uiUnarchiver;
	private JButton uiFindClass;
	private JButton uiMapper;
	private JButton uiObfuscator;
	private JButton uiLavacTest;
	private JButton uiNat;
	private JButton uiNovel;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}