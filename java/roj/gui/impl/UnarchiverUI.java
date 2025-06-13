/*
 * Created by JFormDesigner on Tue Jan 16 22:42:12 CST 2024
 */

package roj.gui.impl;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.qpak.QZArchiver;
import roj.archive.qz.QZArchive;
import roj.archive.qz.QZEntry;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.collect.TrieTreeSet;
import roj.concurrent.TaskPool;
import roj.gui.GuiProgressBar;
import roj.gui.GuiUtil;
import roj.gui.TreeBuilder;
import roj.io.CorruptedInputException;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.text.TextUtil;
import roj.text.URICoder;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.util.Helpers;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static roj.archive.qz.WinAttributes.FILE_ATTRIBUTE_NORMAL;
import static roj.archive.qz.WinAttributes.FILE_ATTRIBUTE_REPARSE_POINT;

/**
 * @author Roj234
 */
public class UnarchiverUI extends JFrame {
	public static void main(String[] args) throws Exception {
		GuiUtil.systemLaf();
		UnarchiverUI f = new UnarchiverUI();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
	}

	private final DefaultTreeModel fileTree = new DefaultTreeModel(null);
	private ArchiveFile archiveFile;
	private QZArchive qzArhive;
	private ZipFile zipFile;

	public UnarchiverUI() {
		initComponents();
		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) {closeFile();}
		});
		GuiUtil.dropFilePath(uiOutputPath, null, false);

		var m = new DefaultComboBoxModel<String>();
		m.addElement("跳过");
		m.addElement("替换");
		m.addElement("重命名");
		uiDuplicateType.setModel(m);

		uiPathTree.setModel(fileTree);

		new DropTarget(uiRead, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent dtde) {
				dtde.acceptDrop(3);
				Transferable t = dtde.getTransferable();
				for (DataFlavor flavor : t.getTransferDataFlavors()) {
					if (flavor.getMimeType().startsWith("application/x-java-file-list")) {
						try {
							List<File> data = Helpers.cast(t.getTransferData(flavor));
							if (data.size() != 1) {
								JOptionPane.showMessageDialog(UnarchiverUI.this, "仅能打开一个文件！");
								return;
							}

							doOpen(data.get(0));
						} catch (Exception ignored) {}
					}
				}
			}
		});
		uiRead.addActionListener(e -> doOpen(null));
		uiExtract.addActionListener(e -> {
			File basePath = uiOutputPath.getText().isEmpty() ? GuiUtil.fileSaveTo("解压位置", "", this, true) : new File(uiOutputPath.getText());
			if (basePath == null || !basePath.isDirectory() && !basePath.mkdirs()) {
				JOptionPane.showMessageDialog(this, "解压目录不存在且无法创建");
				return;
			}

			uiRead.setEnabled(false);
			uiExtract.setEnabled(false);
			TaskPool.common().submit(() -> {
				int threads = (int) uiThreads.getValue();
				if (threads == 0) threads = Runtime.getRuntime().availableProcessors();
				TaskPool pool = TaskPool.newFixed(threads, "7z-worker-");

				EasyProgressBar bar = new GuiProgressBar(null, progressBar1);
				bar.setName("解压");
				bar.setUnit("B");

				try {
					doExtract(basePath, pool, bar);
				} finally {
					pool.shutdown();
					pool.awaitTermination();

					uiRead.setEnabled(true);
					uiExtract.setEnabled(true);
					progressBar1.setValue(100);
					bar.end("完成");
				}
			});
		});
	}

	private void doOpen(File archive) {
		if (archive == null) {
			if (archiveFile != null) {
				closeFile();
				return;
			}

			archive = GuiUtil.fileLoadFrom("压缩文件", this);
			if (archive == null) return;
		} else if (!archive.isFile()) {
			return;
		}

		closeFile();

		try {
			byte[] h = new byte[32];
			try (var in = new FileInputStream(archive)) {
				int r = in.read(h);
				if (r != h.length) h = Arrays.copyOf(h, Math.max(r, 0));
			}

			if (h[0] == 'P' && h[1] == 'K') {
				archiveFile = zipFile = new ZipFile(archive, ZipFile.FLAG_BACKWARD_READ, uiCharset.getText().isEmpty() ? Charset.defaultCharset() : Charset.forName(uiCharset.getText()));
				uiStoreAnti.setEnabled(false);
			} else if (h[0] == '7' && h[1] == 'z') {
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
				uiStoreAnti.setEnabled(true);
			} else {
				throw new FastFailException("无法识别的文件头，目前只支持7z和zip");
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "无法打开压缩文件: "+ex.getMessage(), "打开失败", JOptionPane.ERROR_MESSAGE);
			ex.printStackTrace();
			return;
		}

		var pathTree = new TreeBuilder<>();
		for (var entry : archiveFile.entries()) {
			pathTree.add(entry.getName(), entry, entry.isDirectory());
		}
		fileTree.setRoot(pathTree.build(archive.getName()));

		uiExtract.setEnabled(true);
		uiRead.setText("关闭");
	}
	private void doExtract(File basePath, TaskPool pool, EasyProgressBar bar) {
		TreePath[] paths = uiPathTree.getSelectionPaths();
		TrieTreeSet set;
		if (paths == null) {
			set = null;
		} else {
			set = new TrieTreeSet();
			for (TreePath path : paths) {
				TreeBuilder.Node<QZEntry> node = Helpers.cast(path.getLastPathComponent());
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
		if (bFollowLink.isSelected())  storeFlag |= 32;

		int storeFlag1 = storeFlag;
		TrieTreeSet javacSb = set;
		BiConsumer<ArchiveEntry, InputStream> cb = (entry, in) -> {
			if (javacSb == null || javacSb.strStartsWithThis(entry.getName())) {
				String name = entry.getName();
				if (uiPathFilter.isSelected()) name = URICoder.escapeFilePath(IOUtil.safePath(name));

				File file1 = new File(basePath, name);

				if (entry.isDirectory()) {
					file1.mkdirs();
					return;
				}

				int ord = 0;
				loop:
				while (file1.exists()) {
					switch (uiDuplicateType.getSelectedIndex()) {
						case 0: return;// skip
						case 1: break loop; // replace
						case 2: break; // rename
					}
					name = IOUtil.fileName(name)+"("+ ++ord +")."+IOUtil.extensionName(name);
					file1 = new File(basePath, name);
				}

				file1.getParentFile().mkdirs();
				try {
					if ((storeFlag1&32) != 0 && (entry.getWinAttributes()&FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
						if ((entry.getWinAttributes()&FILE_ATTRIBUTE_NORMAL) != 0) {
							Files.createLink(file1.toPath(), Path.of(basePath.getAbsolutePath()+File.separatorChar+IOUtil.readUTF(in)));
						} else {
							Files.createSymbolicLink(file1.toPath(), Path.of(IOUtil.readUTF(in)));
						}
					} else {
						IOUtil.createSparseFile(file1, entry.getSize());
						assert in.available() == Math.min(entry.getSize(), Integer.MAX_VALUE);
						try (var out = new FileSource(file1)) {
							QZArchiver.copyStreamWithProgress(in, out, bar);
						}
						assert file1.length() == entry.getSize();
					}

					if ((storeFlag1&7) != 0) {
						var view = Files.getFileAttributeView(file1.toPath(), BasicFileAttributeView.class);
						view.setTimes(
							(storeFlag1&1) == 0 ? null : entry.getPrecisionModificationTime(),
							(storeFlag1&2) == 0 ? null : entry.getPrecisionAccessTime(),
							(storeFlag1&4) == 0 ? null : entry.getPrecisionCreationTime());
					}
				} catch (Exception ex) {
					logger.warn("文件{}解压错误", ex, name);
				}

				bar.increment(entry.getSize());
			}
		};

		if (zipFile != null) {
			byte[] password = null;
			for (ZEntry entry : zipFile.entries()) {
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
						JOptionPane.showMessageDialog(this, "密码错误, 解压失败");
						throw new IllegalArgumentException("密码错误");
					}
					bar.addTotal(entry.getSize());
				}
			}

			byte[] javacSbAgain = password;
			for (ZEntry entry : zipFile.entries()) {
				if (set == null || set.strStartsWithThis(entry.getName())) {
					pool.submit(() -> {
						try (InputStream in = zipFile.getStream(entry, javacSbAgain)) {
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
					if (uiPathFilter.isSelected()) name = URICoder.escapeFilePath(IOUtil.safePath(name));

					File file1 = new File(basePath, name);
					try {
						Files.deleteIfExists(file1.toPath());
					} catch (IOException ex) {
						logger.warn("文件{}无法删除", ex, file1);
					}
				}
				bar.addTotal(entry.getSize());
			}
		}

		qzArhive.parallelDecompress(pool, Helpers.cast(cb), password);
	}

	private void closeFile() {
		fileTree.setRoot(null);
		IOUtil.closeSilently(archiveFile);
		archiveFile = null;
		zipFile = null;
		qzArhive = null;
		uiRead.setText("打开");
		uiExtract.setEnabled(false);
	}

	private byte[] checkPassword(ArchiveEntry entry, String pass, String charset) {
		byte[] password;
		try (InputStream in = archiveFile.getStream(entry, password = pass.getBytes(Charset.forName(charset)))) {
			in.skip(1048576);
			return password;
		} catch (Exception ex) {
			return null;
		}
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        var label2 = new JLabel();
        uiOutputPath = new JTextField();
        uiThreads = new JSpinner();
        uiRead = new JButton();
        uiExtract = new JButton();
        uiDuplicateType = new JComboBox<>();
        var label4 = new JLabel();
        var scrollPane1 = new JScrollPane();
        uiPathTree = new JTree();
        var label5 = new JLabel();
        uiStoreMTime = new JCheckBox();
        uiStoreCTime = new JCheckBox();
        uiStoreATime = new JCheckBox();
        uiStoreAttr = new JCheckBox();
        uiStoreAnti = new JCheckBox();
        bFollowLink = new JCheckBox();
        progressBar1 = new JProgressBar();
        uiPathFilter = new JCheckBox();
        var scrollPane2 = new JScrollPane();
        uiPasswords = new JTextArea();
        uiPasswordInfo = new JLabel();
        uiNoVerify = new JCheckBox();
        uiCharset = new JTextField();

        //======== this ========
        setTitle("Roj234 Unarchiver 1.3");
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //---- label2 ----
        label2.setText("\u89e3\u538b\u5230");
        contentPane.add(label2);
        label2.setBounds(new Rectangle(new Point(2, 62), label2.getPreferredSize()));

        //---- uiOutputPath ----
        uiOutputPath.setFont(uiOutputPath.getFont().deriveFont(uiOutputPath.getFont().getSize() + 2f));
        contentPane.add(uiOutputPath);
        uiOutputPath.setBounds(40, 55, 180, 25);
        contentPane.add(uiThreads);
        uiThreads.setBounds(240, 55, 60, uiThreads.getPreferredSize().height);

        //---- uiRead ----
        uiRead.setText("\u6253\u5f00");
        uiRead.setFont(uiRead.getFont().deriveFont(uiRead.getFont().getSize() + 6f));
        uiRead.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiRead);
        uiRead.setBounds(30, 5, 80, 40);

        //---- uiExtract ----
        uiExtract.setText("\u89e3\u538b");
        uiExtract.setEnabled(false);
        uiExtract.setMargin(new Insets(2, 2, 2, 2));
        uiExtract.setFont(uiExtract.getFont().deriveFont(uiExtract.getFont().getSize() + 6f));
        contentPane.add(uiExtract);
        uiExtract.setBounds(140, 5, 80, 40);
        contentPane.add(uiDuplicateType);
        uiDuplicateType.setBounds(240, 10, 60, uiDuplicateType.getPreferredSize().height);

        //---- label4 ----
        label4.setText("\u76ee\u5f55\u6811 \u82e5\u9009\u4e2d\uff0c\u4ec5\u9012\u5f52\u89e3\u538b\u9009\u4e2d\u7684");
        contentPane.add(label4);
        label4.setBounds(new Rectangle(new Point(10, 155), label4.getPreferredSize()));

        //======== scrollPane1 ========
        {
            scrollPane1.setViewportView(uiPathTree);
        }
        contentPane.add(scrollPane1);
        scrollPane1.setBounds(5, 170, 375, 245);

        //---- label5 ----
        label5.setText("\u6587\u4ef6\u5c5e\u6027");
        contentPane.add(label5);
        label5.setBounds(new Rectangle(new Point(320, 10), label5.getPreferredSize()));

        //---- uiStoreMTime ----
        uiStoreMTime.setText("\u4fee\u6539\u65f6\u95f4");
        uiStoreMTime.setSelected(true);
        contentPane.add(uiStoreMTime);
        uiStoreMTime.setBounds(new Rectangle(new Point(315, 30), uiStoreMTime.getPreferredSize()));

        //---- uiStoreCTime ----
        uiStoreCTime.setText("\u521b\u5efa\u65f6\u95f4");
        contentPane.add(uiStoreCTime);
        uiStoreCTime.setBounds(new Rectangle(new Point(315, 50), uiStoreCTime.getPreferredSize()));

        //---- uiStoreATime ----
        uiStoreATime.setText("\u8bbf\u95ee\u65f6\u95f4");
        contentPane.add(uiStoreATime);
        uiStoreATime.setBounds(new Rectangle(new Point(315, 70), uiStoreATime.getPreferredSize()));

        //---- uiStoreAttr ----
        uiStoreAttr.setText("DOS\u5c5e\u6027");
        uiStoreAttr.setEnabled(false);
        contentPane.add(uiStoreAttr);
        uiStoreAttr.setBounds(new Rectangle(new Point(210, 90), uiStoreAttr.getPreferredSize()));

        //---- uiStoreAnti ----
        uiStoreAnti.setText("\u5220\u9664\u6587\u4ef6");
        contentPane.add(uiStoreAnti);
        uiStoreAnti.setBounds(new Rectangle(new Point(315, 110), uiStoreAnti.getPreferredSize()));

        //---- bFollowLink ----
        bFollowLink.setText("\u8f6f\u786c\u94fe\u63a5");
        contentPane.add(bFollowLink);
        bFollowLink.setBounds(new Rectangle(new Point(315, 90), bFollowLink.getPreferredSize()));

        //---- progressBar1 ----
        progressBar1.setMaximum(10000);
        contentPane.add(progressBar1);
        progressBar1.setBounds(5, 140, 375, progressBar1.getPreferredSize().height);

        //---- uiPathFilter ----
        uiPathFilter.setText("\u8def\u5f84\u8fc7\u6ee4");
        contentPane.add(uiPathFilter);
        uiPathFilter.setBounds(new Rectangle(new Point(235, 32), uiPathFilter.getPreferredSize()));

        //======== scrollPane2 ========
        {
            scrollPane2.setViewportView(uiPasswords);
        }
        contentPane.add(scrollPane2);
        scrollPane2.setBounds(5, 440, 375, 200);

        //---- uiPasswordInfo ----
        uiPasswordInfo.setText("\u6bcf\u884c\u4e00\u4e2a\u5bc6\u7801\uff0c\u81ea\u52a8\u5c1d\u8bd5");
        contentPane.add(uiPasswordInfo);
        uiPasswordInfo.setBounds(new Rectangle(new Point(10, 420), uiPasswordInfo.getPreferredSize()));

        //---- uiNoVerify ----
        uiNoVerify.setText("\u4e0d\u9a8c\u8bc1");
        contentPane.add(uiNoVerify);
        uiNoVerify.setBounds(new Rectangle(new Point(145, 416), uiNoVerify.getPreferredSize()));

        //---- uiCharset ----
        uiCharset.setText("UTF-8");
        contentPane.add(uiCharset);
        uiCharset.setBounds(305, 417, 75, uiCharset.getPreferredSize().height);

        contentPane.setPreferredSize(new Dimension(390, 650));
        pack();
        setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JTextField uiOutputPath;
    private JSpinner uiThreads;
    private JButton uiRead;
    private JButton uiExtract;
    private JComboBox<String> uiDuplicateType;
    private JTree uiPathTree;
    private JCheckBox uiStoreMTime;
    private JCheckBox uiStoreCTime;
    private JCheckBox uiStoreATime;
    private JCheckBox uiStoreAttr;
    private JCheckBox uiStoreAnti;
    private JCheckBox bFollowLink;
    private JProgressBar progressBar1;
    private JCheckBox uiPathFilter;
    private JTextArea uiPasswords;
    private JLabel uiPasswordInfo;
    private JCheckBox uiNoVerify;
    private JTextField uiCharset;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}