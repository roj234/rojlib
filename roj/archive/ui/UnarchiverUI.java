/*
 * Created by JFormDesigner on Tue Jan 16 22:42:12 CST 2024
 */

package roj.archive.ui;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.qz.QZArchive;
import roj.archive.qz.QZEntry;
import roj.archive.qz.QZUtils;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.collect.TrieTreeSet;
import roj.concurrent.TaskPool;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.ui.GuiPathTreeBuilder;
import roj.ui.GuiProgressBar;
import roj.ui.GuiUtil;
import roj.util.Helpers;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 */
public class UnarchiverUI extends JFrame {
	public static void main(String[] args) throws Exception {
		GuiUtil.systemLook();
		UnarchiverUI f = new UnarchiverUI();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
	}

	private ArchiveFile archiveFile;
	private QZArchive qzArhive;
	private ZipArchive zipArchive;
	public UnarchiverUI() {
		initComponents();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					closeFile(null);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		GuiUtil.dropFilePath(uiArchivePath, null, false);
		GuiUtil.dropFilePath(uiOutputPath, null, false);

		DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
		m.addElement("7z");
		m.addElement("zip");
		uiArchiveType.setModel(m);
		uiArchiveType.addActionListener(e -> {
			String id = uiArchiveType.getSelectedItem().toString();
			boolean is7z = id.equals("7z");
			uiStoreAnti.setEnabled(is7z);
		});

		m = new DefaultComboBoxModel<>();
		m.addElement("跳过");
		m.addElement("替换");
		m.addElement("重命名");
		uiDuplicateType.setModel(m);

		DefaultTreeModel m1 = new DefaultTreeModel(null);
		uiPathTree.setModel(m1);

		uiRead.addActionListener(e -> {
			String pathText = uiArchivePath.getText();
			File archive;
			if (pathText.isEmpty() || !(archive = new File(pathText)).isFile()) {
				archive = GuiUtil.fileLoadFrom("压缩文件", this);
				if (archive == null) return;
			}
			try {
				closeFile(m1);

				GuiPathTreeBuilder<ArchiveEntry> pathTree = new GuiPathTreeBuilder<>();
				if (uiArchiveType.getSelectedItem().equals("zip")) {
					archiveFile = zipArchive = new ZipArchive(archive);
					for (ZEntry entry : zipArchive.getEntries().values()) {
						pathTree.add(entry.getName(), entry, entry.isDirectory());
					}
				} else {
					List<String> password = null;
					while (true) {
						try {
							String strPass = password == null ? null : password.remove(0);
							archiveFile = qzArhive = new QZArchive(archive, strPass);
							if (strPass != null) uiPasswordInfo.setText("密码是"+strPass);
							break;
						} catch (CorruptedInputException|IllegalArgumentException ex) {
							if (password == null) password = TextUtil.split(uiPasswords.getText(), '\n');
							if (password.isEmpty()) throw ex;
						}
					}
					for (QZEntry entry : qzArhive.getEntriesByPresentOrder()) {
						pathTree.add(entry.getName(), entry, entry.isDirectory());
					}
				}

				m1.setRoot(pathTree.build(archive.getName()));
				uiExtract.setEnabled(true);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "无法打开压缩文件: "+ex.getMessage(), "打开失败", JOptionPane.ERROR_MESSAGE);
				ex.printStackTrace();
			}
		});
		uiExtract.addActionListener(e -> {
			File basePath = uiOutputPath.getText().isEmpty() ? GuiUtil.fileSaveTo("解压位置", "", this, true) : new File(uiOutputPath.getText());
			if (basePath == null || !basePath.isDirectory() && !basePath.mkdirs()) {
				JOptionPane.showMessageDialog(this, "解压目录不存在且无法创建");
				return;
			}

			uiExtract.setEnabled(false);
			TaskPool.Common().pushTask(() -> {
				int threads = (int) uiThreads.getValue();
				if (threads == 0) threads = Runtime.getRuntime().availableProcessors();
				TaskPool pool = TaskPool.MaxThread(threads, "7z-worker-");

				EasyProgressBar bar = new GuiProgressBar(null, progressBar1);
				bar.setName("解压");
				bar.setUnit("B");

				try {
					doExtract(basePath, pool, bar);
				} finally {
					pool.awaitFinish();
					pool.shutdown();

					uiExtract.setEnabled(true);
					progressBar1.setValue(100);
					bar.end("完成");
				}
			});
		});
	}

	private void doExtract(File basePath, TaskPool pool, EasyProgressBar bar) {
		TreePath[] paths = uiPathTree.getSelectionPaths();
		TrieTreeSet set;
		if (paths == null) {
			set = null;
		} else {
			set = new TrieTreeSet();
			for (TreePath path : paths) {
				GuiPathTreeBuilder.Node<QZEntry> node = Helpers.cast(path.getLastPathComponent());
				if (node == uiPathTree.getModel().getRoot()) {
					set = null;
					break;
				}
				set.add(node.fullName);
			}
		}

		Logger logger = Logger.getLogger();
		int storeFlag = 0;
		if (uiStoreMTime.isSelected()) storeFlag |= 1;
		if (uiStoreATime.isSelected()) storeFlag |= 2;
		if (uiStoreCTime.isSelected()) storeFlag |= 4;
		if (uiStoreAnti.isSelected())  storeFlag |= 8;
		if (uiStoreAttr.isSelected())  storeFlag |= 16;

		int storeFlag1 = storeFlag;
		TrieTreeSet javacSb = set;
		BiConsumer<ArchiveEntry, InputStream> cb = (entry, in) -> {
			if (javacSb == null || javacSb.strStartsWithThis(entry.getName())) {
				String name = entry.getName();
				if (uiPathFilter.isSelected()) name = URIUtil.escapeFilePath(IOUtil.safePath(name));

				File file1 = new File(basePath, name);
				loop:
				while (file1.exists()) {
					switch (uiDuplicateType.getSelectedIndex()) {
						case 0: return;// skip
						case 1: break loop; // replace
						case 2: break; // rename
					}
					name = IOUtil.fileName(name)+"-."+IOUtil.extensionName(name);
					file1 = new File(basePath, name);
				}
				file1.getParentFile().mkdirs();
				try {
					IOUtil.allocSparseFile(file1, entry.getSize());
					assert in.available() == Math.min(entry.getSize(), Integer.MAX_VALUE);
					try (FileOutputStream out = new FileOutputStream(file1)) {
						QZUtils.copyStreamWithProgress(in, out, bar);
					}
					assert file1.length() == entry.getSize();

					if ((storeFlag1&7) != 0) {
						BasicFileAttributeView view = Files.getFileAttributeView(file1.toPath(), BasicFileAttributeView.class);
						view.setTimes(
							(storeFlag1&1) == 0 ? null : entry.getPrecisionModificationTime(),
							(storeFlag1&2) == 0 ? null : entry.getPrecisionAccessTime(),
							(storeFlag1&4) == 0 ? null : entry.getPrecisionCreationTime());
					}
				} catch (Exception ex) {
					logger.warn("文件{}解压错误", ex, name);
				}

				bar.addCurrent(entry.getSize());
			}
		};

		if (zipArchive != null) {
			byte[] password = null;
			for (ZEntry entry : zipArchive.getEntries().values()) {
				if (set == null || set.strStartsWithThis(entry.getName())) {
					block:
					if (entry.isEncrypted() && password == null) {
						List<String> passwords = TextUtil.split(uiPasswords.getText(), '\n');
						for (String pass : passwords) {
							for (String charset : new String[] {"UTF8", "UTF_16LE", "UTF_16BE", "GBK", "GB18030", "SHIFT_JIS"}) {
								password = checkPassword(entry, pass, charset);
								if (password != null) {
									uiPasswordInfo.setText("密码是"+charset+"@"+pass);
									break block;
								}
							}
						}
						throw new IllegalArgumentException("密码错误");
					}
					bar.addMax(entry.getSize());
				}
			}

			byte[] javacSbAgain = password;
			for (ZEntry entry : zipArchive.getEntries().values()) {
				if (set == null || set.strStartsWithThis(entry.getName())) {
					pool.pushTask(() -> {
						try (InputStream in = zipArchive.getInput(entry, javacSbAgain)) {
							cb.accept(entry, in);
						}
					});
				}
			}
			return;
		}

		byte[] password = null;
		for (QZEntry entry : qzArhive.getEntriesByPresentOrder()) {
			if (set == null || set.strStartsWithThis(entry.getName())) {
				block:
				if (entry.isEncrypted() && password == null) {
					List<String> passwords = TextUtil.split(uiPasswords.getText(), '\n');
					for (String pass : passwords) {
						password = checkPassword(entry, pass, "UTF_16LE");
						if (password != null) {
							uiPasswordInfo.setText("密码是"+pass);
							break block;
						}
					}
					throw new IllegalArgumentException("密码错误");
				}

				if ((storeFlag&8) != 0 && entry.isAntiItem()) {
					String name = entry.getName();
					if (uiPathFilter.isSelected()) name = URIUtil.escapeFilePath(IOUtil.safePath(name));

					File file1 = new File(basePath, name);
					try {
						Files.deleteIfExists(file1.toPath());
					} catch (IOException ex) {
						logger.warn("文件{}无法删除", ex, file1);
					}
				}
				bar.addMax(entry.getSize());
			}
		}

		qzArhive.parallelDecompress(pool, Helpers.cast(cb), password);
	}

	private void closeFile(DefaultTreeModel m1) throws IOException {
		if (m1 != null) m1.setRoot(null);
		if (archiveFile != null) archiveFile.close();
		zipArchive = null;
		qzArhive = null;
		archiveFile = null;
	}

	private byte[] checkPassword(ArchiveEntry entry, String pass, String charset) {
		byte[] password;
		try (InputStream in = archiveFile.getInput(entry, password = pass.getBytes(Charset.forName(charset)))) {
			in.skip(1048576);
			return password;
		} catch (Exception ex) {
			return null;
		}
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		var label1 = new JLabel();
		uiArchivePath = new JTextField();
		var label2 = new JLabel();
		uiOutputPath = new JTextField();
		uiArchiveType = new JComboBox<>();
		uiThreads = new JSpinner();
		uiRead = new JButton();
		uiExtract = new JButton();
		uiDuplicateType = new JComboBox<>();
		var label4 = new JLabel();
		var scrollPane1 = new JScrollPane();
		uiPathTree = new JTree();
		var label5 = new JLabel();
		uiStoreCTime = new JCheckBox();
		uiStoreMTime = new JCheckBox();
		uiStoreATime = new JCheckBox();
		uiStoreAttr = new JCheckBox();
		uiStoreAnti = new JCheckBox();
		progressBar1 = new JProgressBar();
		uiPathFilter = new JCheckBox();
		var scrollPane2 = new JScrollPane();
		uiPasswords = new JTextArea();
		uiPasswordInfo = new JLabel();

		//======== this ========
		setTitle("Roj234 Unarchiver 1.1");
		var contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- label1 ----
		label1.setText("\u6587\u4ef6");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(30, 10), label1.getPreferredSize()));

		//---- uiArchivePath ----
		uiArchivePath.setFont(uiArchivePath.getFont().deriveFont(uiArchivePath.getFont().getSize() + 2f));
		contentPane.add(uiArchivePath);
		uiArchivePath.setBounds(60, 5, 180, 25);

		//---- label2 ----
		label2.setText("\u89e3\u538b\u5230");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(20, 45), label2.getPreferredSize()));

		//---- uiOutputPath ----
		uiOutputPath.setFont(uiOutputPath.getFont().deriveFont(uiOutputPath.getFont().getSize() + 2f));
		contentPane.add(uiOutputPath);
		uiOutputPath.setBounds(60, 35, 180, 25);
		contentPane.add(uiArchiveType);
		uiArchiveType.setBounds(245, 10, 60, uiArchiveType.getPreferredSize().height);
		contentPane.add(uiThreads);
		uiThreads.setBounds(250, 40, 45, uiThreads.getPreferredSize().height);

		//---- uiRead ----
		uiRead.setText("\u8bfb\u53d6");
		uiRead.setFont(uiRead.getFont().deriveFont(uiRead.getFont().getSize() + 6f));
		uiRead.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiRead);
		uiRead.setBounds(60, 65, 80, 40);

		//---- uiExtract ----
		uiExtract.setText("\u89e3\u538b");
		uiExtract.setEnabled(false);
		uiExtract.setMargin(new Insets(2, 2, 2, 2));
		uiExtract.setFont(uiExtract.getFont().deriveFont(uiExtract.getFont().getSize() + 6f));
		contentPane.add(uiExtract);
		uiExtract.setBounds(160, 65, 80, 40);
		contentPane.add(uiDuplicateType);
		uiDuplicateType.setBounds(255, 80, 60, uiDuplicateType.getPreferredSize().height);

		//---- label4 ----
		label4.setText("\u76ee\u5f55\u6811 \u5982\u679c\u9009\u4e2d\u4e86\uff0c\u90a3\u4e48\u53ea\u4f1a\u89e3\u538b\u9009\u4e2d\u7684\u6587\u4ef6\u548c\u5b50\u6587\u4ef6");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(10, 110), label4.getPreferredSize()));

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiPathTree);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(5, 170, 375, 245);

		//---- label5 ----
		label5.setText("\u8986\u76d6\u7684\u5c5e\u6027");
		contentPane.add(label5);
		label5.setBounds(new Rectangle(new Point(320, 10), label5.getPreferredSize()));

		//---- uiStoreCTime ----
		uiStoreCTime.setText("\u4fee\u6539\u65f6\u95f4");
		uiStoreCTime.setSelected(true);
		contentPane.add(uiStoreCTime);
		uiStoreCTime.setBounds(new Rectangle(new Point(315, 30), uiStoreCTime.getPreferredSize()));

		//---- uiStoreMTime ----
		uiStoreMTime.setText("\u521b\u5efa\u65f6\u95f4");
		contentPane.add(uiStoreMTime);
		uiStoreMTime.setBounds(new Rectangle(new Point(315, 50), uiStoreMTime.getPreferredSize()));

		//---- uiStoreATime ----
		uiStoreATime.setText("\u8bbf\u95ee\u65f6\u95f4");
		contentPane.add(uiStoreATime);
		uiStoreATime.setBounds(new Rectangle(new Point(315, 70), uiStoreATime.getPreferredSize()));

		//---- uiStoreAttr ----
		uiStoreAttr.setText("\u6587\u4ef6\u5c5e\u6027");
		uiStoreAttr.setEnabled(false);
		contentPane.add(uiStoreAttr);
		uiStoreAttr.setBounds(new Rectangle(new Point(315, 90), uiStoreAttr.getPreferredSize()));

		//---- uiStoreAnti ----
		uiStoreAnti.setText("\u5220\u9664\u6587\u4ef6");
		contentPane.add(uiStoreAnti);
		uiStoreAnti.setBounds(new Rectangle(new Point(315, 110), uiStoreAnti.getPreferredSize()));

		//---- progressBar1 ----
		progressBar1.setMaximum(10000);
		contentPane.add(progressBar1);
		progressBar1.setBounds(80, 140, 300, progressBar1.getPreferredSize().height);

		//---- uiPathFilter ----
		uiPathFilter.setText("\u8def\u5f84\u5b89\u5168");
		contentPane.add(uiPathFilter);
		uiPathFilter.setBounds(new Rectangle(new Point(5, 130), uiPathFilter.getPreferredSize()));

		//======== scrollPane2 ========
		{
			scrollPane2.setViewportView(uiPasswords);
		}
		contentPane.add(scrollPane2);
		scrollPane2.setBounds(5, 440, 375, 200);

		//---- uiPasswordInfo ----
		uiPasswordInfo.setText("\u5728\u4e0b\u65b9\u8f93\u5165\u5bc6\u7801\u6765\u81ea\u52a8\u8f93\u5165 \u6bcf\u884c\u4e00\u4e2a");
		contentPane.add(uiPasswordInfo);
		uiPasswordInfo.setBounds(new Rectangle(new Point(10, 420), uiPasswordInfo.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(390, 675));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextField uiArchivePath;
	private JTextField uiOutputPath;
	private JComboBox<String> uiArchiveType;
	private JSpinner uiThreads;
	private JButton uiRead;
	private JButton uiExtract;
	private JComboBox<String> uiDuplicateType;
	private JTree uiPathTree;
	private JCheckBox uiStoreCTime;
	private JCheckBox uiStoreMTime;
	private JCheckBox uiStoreATime;
	private JCheckBox uiStoreAttr;
	private JCheckBox uiStoreAnti;
	private JProgressBar progressBar1;
	private JCheckBox uiPathFilter;
	private JTextArea uiPasswords;
	private JLabel uiPasswordInfo;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}