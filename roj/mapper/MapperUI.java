/*
 * Created by JFormDesigner on Fri Sep 08 20:21:26 CST 2023
 */

package roj.mapper;

import roj.archive.zip.ZipFileWriter;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.task.AsyncTask;
import roj.mapper.util.ResWriter;
import roj.text.LineReader;
import roj.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 */
public class MapperUI extends JFrame {
	private static final Mapper mapper = new Mapper();
	private void init() {
		JFileChooser jfc = new JFileChooser(new File(""));
		jfc.setDialogTitle("选择映射表(TSrg / XSrg)");
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int status = jfc.showOpenDialog(this);
		if (status != JFileChooser.APPROVE_OPTION) return;

		mapper.clear();
		mapper.checkFieldType = uiCheckFieldType.isSelected();
		mapper.loadMap(jfc.getSelectedFile(), uiInvert.isSelected());

		int flag = 0;
		if (uiFlag1.isSelected()) flag |= Mapper.FLAG_FULL_CLASS_MAP;
		if (uiFlag2.isSelected()) flag |= Mapper.FLAG_FIX_SUBIMPL;
		if (uiFlag4.isSelected()) flag |= Mapper.FLAG_FIX_INHERIT;
		if (uiFlag8.isSelected()) flag |= Mapper.FLAG_ANNOTATION_INHERIT;
		mapper.flag = (byte) flag;

		List<File> lib = new SimpleList<>();
		for (String line : new LineReader(uiLibraries.getText())) {
			if (line.startsWith("#")) continue;
			File f = new File(line);
			if (!f.exists()) {
				JOptionPane.showMessageDialog(this, "lib "+f+" 不存在");
				return;
			} else {
				lib.add(f);
			}
		}

		mapper.loadLibraries(lib);
		mapper.classNameChanged();

		uiMap.setEnabled(true);
	}
	private void map() throws Exception {
		File input = new File(uiInputPath.getText());
		File output = new File(uiOutputPath.getText());
		Charset charset = Charset.forName(uiCharset.getText());

		if (uiInputPath.getText().isEmpty() || !input.exists()) {
			JOptionPane.showMessageDialog(this, "输入不存在", "IO错误", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (uiOutputPath.getText().isEmpty() || (input.isDirectory() && !output.isDirectory() && !output.mkdirs())) {
			JOptionPane.showMessageDialog(this, "输出不是文件夹且无法创建", "IO错误", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (output.isFile() && !uiOverwriteOut.isSelected()) {
			if (JOptionPane.NO_OPTION == JOptionPane.showConfirmDialog(this, "是否覆盖输出文件"+output.getName(), "文件覆盖确认", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)) {
				return;
			}
		}

		long begin = System.currentTimeMillis();


		List<X> files = new SimpleList<>();
		List<Context> ctxs;

		Map<String, byte[]> resource;
		try {
			if (input.isDirectory()) {
				ctxs = new SimpleList<>();
				for (File file : input.listFiles()) {
					resource = new MyHashMap<>();
					List<Context> arr = Context.fromZip(file, charset, resource);
					files.add(new X(new File(output, file.getName()), ctxs.size(), arr.size(), resource));

					if (uiMapUsers.isSelected()) mapper.loadLibraries(Collections.singletonList(file));
					ctxs.addAll(arr);
				}
			} else {
				resource = new MyHashMap<>();
				if (uiMapUsers.isSelected()) mapper.loadLibraries(Collections.singletonList(input));
				ctxs = Context.fromZip(input, charset, resource);
				files.add(new X(output, 0, ctxs.size(), resource));
			}

			boolean singleThread = !uiMultiThread.isSelected();
			mapper.map(singleThread, ctxs);

			for (int i = 0; i < files.size(); i++)
				files.get(i).finish(ctxs);
		} finally {
			for (X x : files) x.zfw.close();
		}

		System.out.println(System.currentTimeMillis()-begin);
	}

	static final class X {
		ZipFileWriter zfw;
		AsyncTask<Void> task;
		int off, len;

		X(File out, int off, int len, Map<String, byte[]> resource) throws IOException {
			zfw = new ZipFileWriter(out);
			task = new AsyncTask<>(new ResWriter(zfw, resource));
			this.off = off;
			this.len = len;
			TaskPool.CpuMassive().pushTask(task);
		}

		public void finish(List<Context> byName) throws Exception {
			task.get();
			for (int i = off; i < off+len; i++) {
				Context c = byName.get(i);
				zfw.writeNamed(c.getFileName(), c.getCompressedShared());
			}
			zfw.finish();
		}
	}

	private void uiMap(ActionEvent e) {
		uiMap.setEnabled(false);
		TaskPool.CpuMassive().pushTask(() -> {
			try {
				map();
			} finally {
				uiMap.setEnabled(true);
			}
		});
	}

	private void uiInit(ActionEvent e) {
		uiInit.setEnabled(false);
		TaskPool.CpuMassive().pushTask(() -> {
			try {
				init();
			} finally {
				uiInit.setEnabled(true);
			}
		});
	}

	private void uiCreateMap(ActionEvent e) {
		setEnabled(false);
		MappingUI ui = new MappingUI();
		ui.pack();
		ui.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		ui.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				MapperUI.this.setEnabled(true);
			}
		});
		ui.show();
	}

	public static void main(String[] args) throws Exception {
		UIUtil.systemLook();
		MapperUI f = new MapperUI();

		f.pack();
		f.setDefaultCloseOperation(EXIT_ON_CLOSE);
		f.show();
	}

	public MapperUI() {
		initComponents();
		UIUtil.dropFilePath(uiInputPath, (f) -> uiOutputPath.setText(new File(f.getName()).getAbsolutePath()), false);
		UIUtil.dropFilePath(uiOutputPath, null, false);
		UIUtil.dropFilePath(uiLibraries, null, true);
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		JScrollPane scrollPane1 = new JScrollPane();
		uiLibraries = new JTextArea();
		uiInputPath = new JTextField();
		uiOutputPath = new JTextField();
		uiInvert = new JCheckBox();
		uiMultiThread = new JCheckBox();
		JLabel label1 = new JLabel();
		JLabel label2 = new JLabel();
		uiCharset = new JTextField();
		uiFlag1 = new JCheckBox();
		uiFlag2 = new JCheckBox();
		uiFlag4 = new JCheckBox();
		uiFlag8 = new JCheckBox();
		uiInit = new JButton();
		uiMap = new JButton();
		JLabel label3 = new JLabel();
		uiMapUsers = new JCheckBox();
		uiCheckFieldType = new JCheckBox();
		uiOverwriteOut = new JCheckBox();
		uiCreateMap = new JButton();

		//======== this ========
		setTitle("Roj234 Jar Mapper V3");
		setResizable(false);
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== scrollPane1 ========
		{

			//---- uiLibraries ----
			uiLibraries.setText("# 1. \u5982\u679c\u62a5\u9519\u201c\u7f3a\u5c11\u5143\u7d20\u201d\uff0c\u52fe\u4e0a\u201c\u8f93\u5165\u7684\u7c7b\u5728\u6620\u5c04\u8868\u4e2d\u201d\u91cd\u8bd5\n#    \u8f93\u5165\u4e0d\u53d8\u65f6\uff0c\u53ea\u6709\u7b2c\u4e00\u6b21\u6620\u5c04\u9700\u8981\u52fe\uff08\u8fd9\u6837\u53ef\u52a0\u5feb\u901f\u5ea6\uff09\n# 2. \u5173\u5fc3\u5b57\u6bb5\u7c7b\u578b\u9700\u8981\u6620\u5c04\u8868\u5305\u542b\u5b57\u6bb5\u7c7b\u578b\n# 3. \u201c\u5173\u5fc3\u5b57\u6bb5\u7c7b\u578b\u201d\u548c\u201c\u53cd\u5411\u6620\u5c04\u201d\u9700\u8981\u91cd\u65b0\u52a0\u8f7d\u751f\u6548\n# 4. \u8f93\u5165\u548c\u8f93\u51fa\u53ef\u4ee5\u662f\u6587\u4ef6\u5939\u6765\u6279\u5904\u7406\u6240\u6709\u6587\u4ef6\n# 5. \u5982\u679c\u8f93\u51fa\u662f\u6587\u4ef6\u5939\uff0c\u65e0\u8bba\u5982\u4f55\u90fd\u4f1a\u8986\u76d6\u6587\u4ef6\n# 6. \u8f93\u5165\u3001\u8f93\u51fa\u548c\u5e93\u6587\u4ef6\u652f\u6301\u62d6\u52a8\n# 7. \u8f93\u5165\u5b57\u7b26\u96c6\u4e0d\u7528\u8fc7\u4e8e\u62c5\u5fc3\uff0c\u6211\u7684ZipArchive\u6bd4sun\u5199\u7684\u597d");
			scrollPane1.setViewportView(uiLibraries);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(4, 160, 380, 210);

		//---- uiInputPath ----
		uiInputPath.setToolTipText("\u4e5f\u53ef\u4ee5\u662f\u6587\u4ef6\u5939");
		contentPane.add(uiInputPath);
		uiInputPath.setBounds(35, 5, 210, uiInputPath.getPreferredSize().height);
		contentPane.add(uiOutputPath);
		uiOutputPath.setBounds(35, 30, 210, uiOutputPath.getPreferredSize().height);

		//---- uiInvert ----
		uiInvert.setText("\u53cd\u5411\u6620\u5c04");
		contentPane.add(uiInvert);
		uiInvert.setBounds(new Rectangle(new Point(290, 55), uiInvert.getPreferredSize()));

		//---- uiMultiThread ----
		uiMultiThread.setText("\u591a\u7ebf\u7a0b");
		uiMultiThread.setSelected(true);
		contentPane.add(uiMultiThread);
		uiMultiThread.setBounds(new Rectangle(new Point(135, 75), uiMultiThread.getPreferredSize()));

		//---- label1 ----
		label1.setText("\u8f93\u5165");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(5, 10), label1.getPreferredSize()));

		//---- label2 ----
		label2.setText("\u8f93\u51fa");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(5, 33), label2.getPreferredSize()));

		//---- uiCharset ----
		uiCharset.setText("GB18030");
		uiCharset.setToolTipText("\u8f93\u5165\u7684\u5b57\u7b26\u96c6");
		contentPane.add(uiCharset);
		uiCharset.setBounds(250, 5, 70, uiCharset.getPreferredSize().height);

		//---- uiFlag1 ----
		uiFlag1.setText("\u6620\u5c04\u8868\u662f\u6807\u51c6\u683c\u5f0f");
		uiFlag1.setToolTipText("\u6bcf\u4e2a\u6620\u5c04\u7684\u65b9\u6cd5\u6216\u5b57\u6bb5\u5747\u6709\u5bf9\u5e94\u7684\u7c7b\u7ea7\u6620\u5c04\u3002\n\u6b63\u5e38\uff08\u7a0b\u5e8f\u751f\u6210\uff09\u7684\u6620\u5c04\u8868\u90fd\u5e94\u8be5\u7b26\u5408\u6b64\u6761\u4ef6");
		uiFlag1.setSelected(true);
		contentPane.add(uiFlag1);
		uiFlag1.setBounds(new Rectangle(new Point(5, 55), uiFlag1.getPreferredSize()));

		//---- uiFlag2 ----
		uiFlag2.setText("\u4fee\u590d\u65b9\u6cd5\u5b9e\u73b0\u51b2\u7a81");
		uiFlag2.setToolTipText("\u7236\u7c7b\u7684\u65b9\u6cd5\u88ab\u5b50\u7c7b\u7684\u63a5\u53e3\u5b9e\u73b0\uff0c\u5220\u9664\u5b83\u4eec\uff08\u4e4b\u4e00\uff09\u51b2\u7a81\u7684\u6620\u5c04\u8bb0\u5f55");
		contentPane.add(uiFlag2);
		uiFlag2.setBounds(new Rectangle(new Point(5, 75), uiFlag2.getPreferredSize()));

		//---- uiFlag4 ----
		uiFlag4.setText("\u4fee\u590d\u7ee7\u627f\u51b2\u7a81");
		uiFlag4.setToolTipText("\u5220\u9664\u7ee7\u627f\u94fe\u4e0b\u7ea7\u7684\u65b9\u6cd5\u4e0e\u4e0a\u7ea7\u4e0d\u540c\u7684\u6620\u5c04\u8bb0\u5f55");
		contentPane.add(uiFlag4);
		uiFlag4.setBounds(new Rectangle(new Point(5, 95), uiFlag4.getPreferredSize()));

		//---- uiFlag8 ----
		uiFlag8.setText("\u6269\u5c55\uff1a\u6ce8\u89e3\u4f2a\u7ee7\u627f");
		uiFlag8.setToolTipText("\u4f7f\u7528Inherited\u6ce8\u89e3\u6a21\u62df\u7c7b\u7684\u7ee7\u627f");
		contentPane.add(uiFlag8);
		uiFlag8.setBounds(new Rectangle(new Point(5, 115), uiFlag8.getPreferredSize()));

		//---- uiInit ----
		uiInit.setText("\u52a0\u8f7d");
		uiInit.addActionListener(e -> uiInit(e));
		contentPane.add(uiInit);
		uiInit.setBounds(new Rectangle(new Point(325, 5), uiInit.getPreferredSize()));

		//---- uiMap ----
		uiMap.setText("\u6620\u5c04");
		uiMap.setEnabled(false);
		uiMap.addActionListener(e -> uiMap(e));
		contentPane.add(uiMap);
		uiMap.setBounds(new Rectangle(new Point(325, 30), uiMap.getPreferredSize()));

		//---- label3 ----
		label3.setText("\u6269\u5c55\u5e93\u76ee\u5f55: \u6bcf\u884c\u4e00\u4e2a\u6587\u4ef6 \u6216 \u4e00\u4e2a\u6587\u4ef6\u5939 -> \u8bfb\u53d6\u5b50\u76ee\u5f55  ");
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(5, 140), label3.getPreferredSize()));

		//---- uiMapUsers ----
		uiMapUsers.setText("\u8f93\u5165\u7684\u7c7b\u5728\u6620\u5c04\u8868\u4e2d");
		uiMapUsers.setToolTipText("\u4e0d\u5c1d\u8bd5\u4ece\u8f93\u5165\u4e2d\u8bfb\u53d6\u6620\u5c04\u8868\u7684\u6743\u9650\u4fe1\u606f");
		contentPane.add(uiMapUsers);
		uiMapUsers.setBounds(new Rectangle(new Point(135, 95), uiMapUsers.getPreferredSize()));

		//---- uiCheckFieldType ----
		uiCheckFieldType.setText("\u5173\u5fc3\u5b57\u6bb5\u7c7b\u578b");
		uiCheckFieldType.setToolTipText("\u8fd9\u4f1a\u964d\u4f4e\u901f\u5ea6\uff0c\u5728\u6620\u5c04\u8868\u5b58\u5728\u540c\u540d\u4e0d\u540c\u7c7b\u578b\u5b57\u6bb5\u65f6\u5f00\u542f");
		contentPane.add(uiCheckFieldType);
		uiCheckFieldType.setBounds(new Rectangle(new Point(290, 75), uiCheckFieldType.getPreferredSize()));

		//---- uiOverwriteOut ----
		uiOverwriteOut.setText("\u8986\u76d6\u8f93\u51fa\u6587\u4ef6");
		contentPane.add(uiOverwriteOut);
		uiOverwriteOut.setBounds(new Rectangle(new Point(135, 55), uiOverwriteOut.getPreferredSize()));

		//---- uiCreateMap ----
		uiCreateMap.setText("\u521b\u5efa\u6620\u5c04");
		uiCreateMap.setMargin(new Insets(2, 8, 2, 8));
		uiCreateMap.addActionListener(e -> uiCreateMap(e));
		contentPane.add(uiCreateMap);
		uiCreateMap.setBounds(new Rectangle(new Point(250, 30), uiCreateMap.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(390, 375));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextArea uiLibraries;
	private JTextField uiInputPath;
	private JTextField uiOutputPath;
	private JCheckBox uiInvert;
	private JCheckBox uiMultiThread;
	private JTextField uiCharset;
	private JCheckBox uiFlag1;
	private JCheckBox uiFlag2;
	private JCheckBox uiFlag4;
	private JCheckBox uiFlag8;
	private JButton uiInit;
	private JButton uiMap;
	private JCheckBox uiMapUsers;
	private JCheckBox uiCheckFieldType;
	private JCheckBox uiOverwriteOut;
	private JButton uiCreateMap;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
