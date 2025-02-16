/*
 * Created by JFormDesigner on Fri Jan 19 03:57:59 CST 2024
 */

package roj.gui.impl;

import roj.asm.ClassNode;
import roj.asm.util.Context;
import roj.asmx.launcher.EntryPoint;
import roj.concurrent.TaskPool;
import roj.gui.GuiUtil;
import roj.io.IOUtil;
import roj.text.CharList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;

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
		try {
			f.uiInfo.setText(IOUtil.getTextResource("change.log"));
		} catch (Exception e) {
			f.uiInfo.setText("changelog已丢失");
		}

		f.uiCLITool.setText("正在读取……");
		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);

		TaskPool.Common().submit(() -> {
			CharList sb = new CharList();
			try {
				for (Context ctx : Context.fromZip(IOUtil.getJar(UIEntry.class), null)) {
					ClassNode data = ctx.getData();
					int id = data.getMethod("main", "([Ljava/lang/String;)V");
					String p = data.parent();
					if (id >= 0 && !p.startsWith("javax/swing/")/* && !p.equals("roj/plugin/Plugin")*/) {
						sb.append(data.name().replace('/', '.')).append('\n');
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			f.uiCLITool.setText(sb.toString());
		});
	}

	private static void bind(JButton button, String klass) {
		button.addActionListener(e -> {
			button.setEnabled(false);
			try {
				String copy = klass;
				boolean useMainClass = copy.startsWith("^");
				if (useMainClass) copy = copy.substring(1);
				Class<?> aClass = Class.forName(copy);
				if (useMainClass) {
					Method main = aClass.getDeclaredMethod("main", String[].class);
					main.setAccessible(true);
					main.invoke(null, (Object) new String[0]);
				} else {
					JFrame frame = (JFrame) aClass.newInstance();
					frame.pack();
					frame.setResizable(false);
					frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
					frame.setVisible(true);
					frame.addWindowListener(new WindowAdapter() {
						public void windowClosing(WindowEvent e) { button.setEnabled(true); }
					});
				}
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
	}

	public UIEntry() {
		initComponents();
		bind(uiArchiver, "roj.gui.impl.QZArchiverUI");
		bind(uiUnarchiver, "roj.gui.impl.UnarchiverUI");
		bind(uiFindClass, "roj.gui.impl.FindClass");
		bind(uiNovel, "roj.plugins.novel.NovelFrame");
		bind(uiNat, "^roj.plugins.frp.AEGui");
		bind(uiMapper, "roj.gui.impl.MapperUI");
		bind(uiObfuscator, "roj.plugins.obfuscator.ObfuscatorUI");
		bind(uiSM, "roj.novel.SimpleMergeUI");
		bind(uiDIffFinder, "roj.plugins.diff.DiffFinder");
		bind(uiCardSleep, "^roj.plugins.CardSleep");

		uiTest.setEnabled(false);
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        var label1 = new JLabel();
        var scrollPane1 = new JScrollPane();
        uiInfo = new JTextArea();
        uiArchiver = new JButton();
        uiUnarchiver = new JButton();
        uiFindClass = new JButton();
        uiMapper = new JButton();
        uiObfuscator = new JButton();
        uiNat = new JButton();
        uiNovel = new JButton();
        uiDIffFinder = new JButton();
        uiCardSleep = new JButton();
        uiSM = new JButton();
        uiTest = new JButton();
        var label2 = new JLabel();
        var scrollPane2 = new JScrollPane();
        uiCLITool = new JTextArea();

        //======== this ========
        setTitle("RojLib \u7528\u6237\u754c\u9762\u4e3b\u8981\u5165\u53e3");
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //---- label1 ----
        label1.setText("\u66f4\u65b0\u65e5\u5fd7");
        contentPane.add(label1);
        label1.setBounds(new Rectangle(new Point(20, 95), label1.getPreferredSize()));

        //======== scrollPane1 ========
        {
            scrollPane1.setViewportView(uiInfo);
        }
        contentPane.add(scrollPane1);
        scrollPane1.setBounds(20, 115, 355, 160);

        //---- uiArchiver ----
        uiArchiver.setText("7z\u5e76\u884c\u538b\u7f29");
        uiArchiver.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiArchiver);
        uiArchiver.setBounds(new Rectangle(new Point(20, 15), uiArchiver.getPreferredSize()));

        //---- uiUnarchiver ----
        uiUnarchiver.setText("7z\u5e76\u884c\u89e3\u538b");
        uiUnarchiver.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiUnarchiver);
        uiUnarchiver.setBounds(20, 40, 69, 21);

        //---- uiFindClass ----
        uiFindClass.setText("\u5e38\u91cf\u67e5\u8be2");
        uiFindClass.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiFindClass);
        uiFindClass.setBounds(170, 15, uiFindClass.getPreferredSize().width, 21);

        //---- uiMapper ----
        uiMapper.setText("\u6620\u5c04\u5668");
        uiMapper.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiMapper);
        uiMapper.setBounds(95, 15, 69, 21);

        //---- uiObfuscator ----
        uiObfuscator.setText("\u6df7\u6dc6\u5668");
        uiObfuscator.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiObfuscator);
        uiObfuscator.setBounds(95, 40, 69, 21);

        //---- uiNat ----
        uiNat.setText("\u5b89\u5168\u96a7\u9053");
        uiNat.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiNat);
        uiNat.setBounds(315, 40, 65, 21);

        //---- uiNovel ----
        uiNovel.setText("\u5c0f\u8bf4\u6574\u7406\u5de5\u5177");
        uiNovel.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiNovel);
        uiNovel.setBounds(230, 40, uiNovel.getPreferredSize().width, 21);

        //---- uiDIffFinder ----
        uiDIffFinder.setText("\u6587\u4ef6\u6bd4\u8f83");
        uiDIffFinder.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiDIffFinder);
        uiDIffFinder.setBounds(170, 40, uiDIffFinder.getPreferredSize().width, 21);

        //---- uiCardSleep ----
        uiCardSleep.setText("N\u5361\u7701\u7535\u5de5\u5177");
        uiCardSleep.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiCardSleep);
        uiCardSleep.setBounds(230, 15, 80, uiCardSleep.getPreferredSize().height);

        //---- uiSM ----
        uiSM.setText("SMUI");
        contentPane.add(uiSM);
        uiSM.setBounds(315, 15, 65, uiSM.getPreferredSize().height);

        //---- uiTest ----
        uiTest.setText("\u6d4b\u8bd5\u529f\u80fd");
        contentPane.add(uiTest);
        uiTest.setBounds(new Rectangle(new Point(160, 65), uiTest.getPreferredSize()));

        //---- label2 ----
        label2.setText("\u547d\u4ee4\u884c\u5de5\u5177");
        contentPane.add(label2);
        label2.setBounds(new Rectangle(new Point(25, 280), label2.getPreferredSize()));

        //======== scrollPane2 ========
        {

            //---- uiCLITool ----
            uiCLITool.setEditable(false);
            scrollPane2.setViewportView(uiCLITool);
        }
        contentPane.add(scrollPane2);
        scrollPane2.setBounds(20, 300, 355, 245);

        contentPane.setPreferredSize(new Dimension(400, 565));
        pack();
        setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JTextArea uiInfo;
    private JButton uiArchiver;
    private JButton uiUnarchiver;
    private JButton uiFindClass;
    private JButton uiMapper;
    private JButton uiObfuscator;
    private JButton uiNat;
    private JButton uiNovel;
    private JButton uiDIffFinder;
    private JButton uiCardSleep;
    private JButton uiSM;
    private JButton uiTest;
    private JTextArea uiCLITool;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}