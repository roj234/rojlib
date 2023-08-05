/*
 * Created by JFormDesigner on Tue Nov 28 01:30:28 CST 2023
 */

package roj.archive.ui;

import roj.archive.qz.QZArchive;
import roj.archive.qz.QZEntry;
import roj.archive.qz.xz.LZMA2Options;
import roj.concurrent.TaskPool;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.ui.CMBoxValue;
import roj.ui.EasyProgressBar;
import roj.ui.GUIUtil;
import roj.ui.OnChangeHelper;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.VMUtil;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Roj234
 */
public class QZArchiverUI extends JFrame {
	private static final Logger LOGGER = Logger.getLogger();

	public static void main(String[] args) throws Exception {
		GUIUtil.systemLook();
		QZArchiverUI f = new QZArchiverUI();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
	}

	private final LZMA2Options options = new LZMA2Options();
	private final OnChangeHelper helper;

	private void createFileCoder() throws Exception {
		if (uiOutput.getText().isEmpty()) {
			File file = GUIUtil.fileSaveTo("保存到何方?", "新建 7z 压缩文件.7z", this);
			if (file == null) return;
			uiOutput.setText(file.getAbsolutePath());
		}

		QZArchiver arc = new QZArchiver();
		arc.input = new File(uiInput.getText());
		arc.storeFolder = uiStoreFolder.isSelected();
		arc.storeMT = uiStoreMT.isSelected();
		arc.storeCT = uiStoreCT.isSelected();
		arc.storeAT = uiStoreAT.isSelected();
		arc.storeAttr = uiStoreAttr.isSelected();
		arc.threads = ((Number) uiThreads.getValue()).intValue();
		arc.autoSolidSize = uiAutoSolidSize.isSelected();
		if (!arc.autoSolidSize)
			arc.solidSize = (long) TextUtil.unscaledNumber1024(uiSolidSize.getText());
		arc.splitSize = uiSplitSize.getText().isEmpty() ? 0 : (long) TextUtil.unscaledNumber1024(uiSplitSize.getText());
		arc.compressHeader = uiCompressHeader.isSelected();
		if (uiCrypt.isSelected()) {
			arc.password = uiPassword.getText();
			arc.cryptPower = ((Number) uiCyclePower.getValue()).intValue();
			arc.cryptSalt = ((Number) uiSaltLength.getValue()).intValue();
			arc.encryptFileName = uiCryptHeader.isSelected();
		}
		arc.useBCJ = uiBCJ.isSelected();
		arc.useBCJ2 = uiBCJ2.isSelected();
		arc.options = options;
		arc.appendOptions = uiAppendOptions.getSelectedIndex();
		arc.fastAppendCheck = uiFastCheck.isSelected();
		arc.cacheFolder = uiDiskCache.isSelected() ? new File("") : null;
		File out = new File(uiOutput.getText());
		arc.outputFolder = out.getParentFile();
		arc.outputName = out.getName();

		uiLog.setText("正在计数文件\n");

		long chunkSize = arc.prepare();
		if (chunkSize < 0) {
			uiLog.append("压缩文件无需更新\n");
			return;
		}

		int threads = createTaskPool();
		TaskPool pool2;
		if (uiSplitTask.isSelected()) {
			pool2 = TaskPool.MaxThread(threads, pool -> {
				TaskPool.ExecutorImpl thread = pool.new ExecutorImpl();
				thread.setName("split-worker-"+thread.getId());
				thread.setPriority(6); // above normal
				return thread;
			});
			options.setAsyncMode((int) MathUtils.clamp(chunkSize, LZMA2Options.ASYNC_BLOCK_SIZE_MIN, LZMA2Options.ASYNC_BLOCK_SIZE_MAX), pool2, threads, uiSplitTaskType.getSelectedIndex());
		} else {
			pool2 = null;
			options.setAsyncMode(0, null, 0, 0);
		}

		uiBegin.setEnabled(false);
		TaskPool.Common().pushTask(() -> {
			TaskPool pool = TaskPool.MaxThread(threads, "7z-worker-");
			EasyProgressBar bar = new EasyProgressBar("") {
				@Override
				public synchronized void updateForce(double percent) {
					uiProgress.setValue((int) (percent*1000));
					super.updateForce(percent);
				}

				@Override
				public void setName(String name) {
					uiLog.append(name+"\n");
					super.setName(name);
				}
			};
			try {
				arc.compress(pool, bar);
			} finally {
				uiProgress.setValue(1000);
				pool.shutdown();
				if (pool2 != null) pool2.shutdown();
				uiBegin.setEnabled(true);
			}
			try (QZArchive archive = new QZArchive(out, arc.password)) {
				for (QZEntry entry : archive.getEntriesByPresentOrder()) {
					IOUtil.read(archive.getInput(entry));
				}
			}
		});
	}

	public QZArchiverUI() {
		initComponents();
		helper = new OnChangeHelper(this);

		DefaultComboBoxModel<Object> md;

		// region LZMA2Options
		md = new DefaultComboBoxModel<>();
		md.addElement(new CMBoxValue("Hash4", LZMA2Options.MF_HC4));
		md.addElement(new CMBoxValue("Binary4", LZMA2Options.MF_BT4));
		uiMatchFinder.setModel(Helpers.cast(md));

		md = new DefaultComboBoxModel<>();
		md.addElement(new CMBoxValue("1-极速压缩", 1));
		md.addElement(new CMBoxValue("2", 2));
		md.addElement(new CMBoxValue("3-快速压缩", 3));
		md.addElement(new CMBoxValue("4", 4));
		md.addElement(new CMBoxValue("5-标准压缩", 5));
		md.addElement(new CMBoxValue("6", 6));
		md.addElement(new CMBoxValue("7-最大压缩", 7));
		md.addElement(new CMBoxValue("8", 8));
		md.addElement(new CMBoxValue("9-极限压缩", 9));
		uiPreset.setModel(Helpers.cast(md));
		uiPreset.addActionListener(e -> {
			options.setPreset(((CMBoxValue) uiPreset.getSelectedItem()).value);
			syncToUI(true);
		});
		uiPreset.setSelectedIndex(4);

		ChangeListener cl1 = e -> {
			try {
				options.setLcLp(((Number) uiLc.getValue()).intValue(), ((Number) uiLp.getValue()).intValue());
			} catch (Exception ex) {
				uiLc.setValue(options.getLc());
				uiLp.setValue(options.getLp());
			}
		};
		uiLc.addChangeListener(cl1);
		uiLp.addChangeListener(cl1);
		uiPb.addChangeListener(e -> options.setPb(((Number) uiPb.getValue()).intValue()));
		helper.addEventListener(uiDictSize, field -> {
			try {
				options.setDictSize(fromDigital(field.getText()));
				updateMemoryUsage();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "输入错误", JOptionPane.WARNING_MESSAGE);
			}
		});
		uiNiceLen.addChangeListener(e -> options.setNiceLen(((Number) uiNiceLen.getValue()).intValue()));
		uiDepthLimit.setModel(new SpinnerListModel() { // hack createEditor()
			private int val;
			@Override
			public Object getValue() { return val == 0 ? "自动" : val; }
			@Override
			public void setValue(Object value) {
				String s = value.toString();
				int v = s.equals("自动") ? 0 : Integer.parseInt(s);
				if (v < 0) v = 0;
				else if (v > 1000) v = 1000;

				if (val != v) {
					val = v;
					fireStateChanged();
				}
			}
			@Override
			public Object getNextValue() { return val < 1000 ? val+1 : 1000; }
			@Override
			public Object getPreviousValue() { return val > 0 ? val-1 : 0; }
		});
		uiDepthLimit.addChangeListener(e -> {
			Object value = uiDepthLimit.getValue();
			options.setDepthLimit(value instanceof Number ? ((Number) value).intValue() : 0);
		});
		uiMatchFinder.addActionListener(e -> {
			options.setMatchFinder(uiMatchFinder.getSelectedIndex());
			updateMemoryUsage();
		});
		uiFastLZMA.addActionListener(e -> {
			options.setMode(uiFastLZMA.isSelected() ? LZMA2Options.MODE_FAST : LZMA2Options.MODE_NORMAL);
			updateMemoryUsage();
		});
		uiFindBestProp.addActionListener(e -> {
			File[] files = GUIUtil.filesLoadFrom("选择一些文件来测试最好的LcLpPb及压缩率", this, JFileChooser.FILES_ONLY);
			if (files == null) return;

			uiFindBestProp.setEnabled(false);
			uiLog.setText("开始测试,时间依据您除了LcLpPb的LZMA设定而变化,请耐心等待\n" +
				"请不要在测试过程中修改LZMA设定,这会导致结果不准确");

			TaskPool.Common().pushTask(() -> {
				TaskPool pool = TaskPool.MaxThread(createTaskPool(), "LcLpTest-worker-");
				try {
					ByteList data = new ByteList();
					for (File file : files) {
						try (FileInputStream in = new FileInputStream(file)) {
							data.readStreamFully(in);
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}

					byte[] b = data.toByteArray();
					data.list = null;

					int minSize = options.findBestProps(b, pool);
					uiLc.setValue(options.getLc());
					uiLp.setValue(options.getLp());
					uiPb.setValue(options.getPb());
					uiLog.setText("测试结束,LcLpPb已经填入输入框\n" +
						"使用它们的压缩大小为"+toDigital(minSize)+" ("+TextUtil.toFixed((double)minSize/data.length() * 100, 2)+"%)");
				} finally {
					uiFindBestProp.setEnabled(true);
					pool.shutdown();
				}
			});
		});
		// endregion
		// region Crypt
		uiCrypt.addActionListener(e -> {
			boolean b = uiCrypt.isSelected();
			uiPassword.setEnabled(b);
			uiCyclePower.setEnabled(b);
			uiCryptHeader.setEnabled(b);
			uiSaltLength.setEnabled(b);
			if (!b) uiPassword.setText("");
		});
		// endregion
		// region
		md = new DefaultComboBoxModel<>();
		md.addElement("添加并替换文件");
		md.addElement("更新并添加文件");
		md.addElement("只更新已存在的文件");
		md.addElement("同步压缩包内容");
		uiAppendOptions.setModel(Helpers.cast(md));
		// endregion
		// region
		uiThreads.setModel(new SpinnerNumberModel(Runtime.getRuntime().availableProcessors(), 1,256,1));
		uiMemoryLimit.setText(toDigital(VMUtil.usableMemory()));

		uiAutoSolidSize.addActionListener(e -> uiSolidSize.setEnabled(!uiAutoSolidSize.isSelected()));
		uiBCJ.addActionListener(e -> uiBCJ2.setEnabled(uiBCJ.isSelected()));

		ActionListener[] tip = new ActionListener[1];
		tip[0] = e -> {
			JOptionPane.showMessageDialog(this,
				"1. 该选项会导致实际使用的内存超过【内存】输入框的限制\n" +
					"    最高可超出【并行】*【词典大小+固实大小】\n" +
					"2. 该选项会导致实际使用的线程超过【并行】输入框的限制\n" +
					"3. 比起按文件固实,单压缩流并行会损失更多压缩率", "温馨提示", JOptionPane.WARNING_MESSAGE);
			uiSplitTask.removeActionListener(tip[0]);
		};
		uiSplitTask.addActionListener(e -> uiSplitTaskType.setEnabled(uiSplitTask.isSelected()));
		uiSplitTask.addActionListener(tip[0]);
		md = new DefaultComboBoxModel<>();
		md.addElement("压缩率中低|压缩快|内存低");
		md.addElement("压缩率中高|压缩慢|内存中");
		md.addElement("压缩率中高|压缩中|内存高");
		uiSplitTaskType.setModel(Helpers.cast(md));
		// endregion
		GUIUtil.dropFilePath(uiInput, null, false);
		GUIUtil.dropFilePath(uiOutput, null, false);
		uiBegin.addActionListener(e -> {
			try {
				createFileCoder();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
		uiProgress.setModel(new DefaultBoundedRangeModel(0,0,0,1000));
	}

	private void syncToUI(boolean isPreset) {
		helper.setEnabled(false);
		try {
			uiLc.setValue(options.getLc());
			uiLp.setValue(options.getLp());
			uiPb.setValue(options.getPb());
			uiDictSize.setText(toDigital(options.getDictSize()));
			uiNiceLen.setValue(options.getNiceLen());
			uiDepthLimit.setValue(options.getDepthLimit());
			uiMatchFinder.setSelectedIndex(options.getMatchFinder());
			uiFastLZMA.setSelected(options.getMode() == LZMA2Options.MODE_FAST);
			updateMemoryUsage();
		} finally {
			helper.setEnabled(true);
		}
	}
	private void updateMemoryUsage() {
		uiMemoryUsage.setText("每线程内存消耗:"+toDigital(options.getEncoderMemoryUsage()*1024d));
	}
	private int createTaskPool() {
		int threads = ((Number) uiThreads.getValue()).intValue();

		String text = uiMemoryLimit.getText();
		double memoryLimit;
		if (text.isEmpty()) memoryLimit = (VMUtil.usableMemory() >>> 10);
		else memoryLimit = TextUtil.unscaledNumber1024(text) / 1024;

		// 应该再加上分块大小
		int perThreadUsage = options.getEncoderMemoryUsage() + 256;
		threads = (int) Math.min(threads, Math.floor(memoryLimit / perThreadUsage));

		uiLog.append("实际能创建的线程:"+threads+'\n');

		return threads;
	}

	private static String toDigital(double size) { return TextUtil.scaledNumber1024(size); }
	private int fromDigital(String text) {
		double v = text.isEmpty() ? 0 : TextUtil.unscaledNumber1024(text);
		if (v > Integer.MAX_VALUE) throw new FastFailException(text+"超出了整型范围！");
		return (int) v;
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		JLabel label4 = new JLabel();
		JLabel label15 = new JLabel();
		uiInput = new JTextField();
		uiOutput = new JTextField();
		uiBegin = new JButton();
		uiProgress = new JProgressBar();
		JSeparator separator1 = new JSeparator();
		JLabel label14 = new JLabel();
		uiSplitSize = new JTextField();
		JLabel label3 = new JLabel();
		uiSolidSize = new JTextField();
		uiAutoSolidSize = new JCheckBox();
		uiDiskCache = new JCheckBox();
		JSeparator separator5 = new JSeparator();
		uiThreads = new JSpinner();
		JLabel label2 = new JLabel();
		uiSplitTask = new JCheckBox();
		uiSplitTaskType = new JComboBox<>();
		uiMemoryLimit = new JTextField();
		JLabel label5 = new JLabel();
		JSeparator separator2 = new JSeparator();
		uiPreset = new JComboBox<>();
		uiFindBestProp = new JButton();
		JLabel label7 = new JLabel();
		JLabel label8 = new JLabel();
		JLabel label9 = new JLabel();
		JLabel label10 = new JLabel();
		JLabel label11 = new JLabel();
		uiLc = new JSpinner();
		uiLp = new JSpinner();
		uiPb = new JSpinner();
		uiDictSize = new JTextField();
		uiNiceLen = new JSpinner();
		uiDepthLimit = new JSpinner();
		uiMatchFinder = new JComboBox<>();
		uiMemoryUsage = new JLabel();
		uiFastLZMA = new JCheckBox();
		JSeparator separator3 = new JSeparator();
		uiCrypt = new JCheckBox();
		uiCryptHeader = new JCheckBox();
		uiPassword = new JTextField();
		JLabel label12 = new JLabel();
		JLabel label13 = new JLabel();
		uiCyclePower = new JSpinner();
		uiSaltLength = new JSpinner();
		JSeparator separator4 = new JSeparator();
		uiCompressHeader = new JCheckBox();
		uiFastCheck = new JCheckBox();
		uiAppendOptions = new JComboBox<>();
		JLabel label6 = new JLabel();
		uiStoreMT = new JCheckBox();
		uiStoreCT = new JCheckBox();
		uiStoreAT = new JCheckBox();
		uiStoreAttr = new JCheckBox();
		uiStoreFolder = new JCheckBox();
		JSeparator separator6 = new JSeparator();
		uiBCJ = new JCheckBox();
		uiBCJ2 = new JCheckBox();
		JScrollPane scrollPane1 = new JScrollPane();
		uiLog = new JTextArea();
		JLabel label1 = new JLabel();

		//======== this ========
		setTitle("Roj234 SevenZ Archiver 1.2");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- label4 ----
		label4.setText("\u538b\u7f29\u8fd9\u4e2a\u76ee\u5f55\u6216\u6587\u4ef6");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(10, 0), label4.getPreferredSize()));

		//---- label15 ----
		label15.setText("...\u5230\u8fd9\u4e2a\u6587\u4ef6\u4e2d");
		contentPane.add(label15);
		label15.setBounds(new Rectangle(new Point(10, 40), label15.getPreferredSize()));

		//---- uiInput ----
		uiInput.setFont(uiInput.getFont().deriveFont(uiInput.getFont().getStyle() | Font.BOLD, uiInput.getFont().getSize() + 4f));
		contentPane.add(uiInput);
		uiInput.setBounds(15, 15, 335, uiInput.getPreferredSize().height);

		//---- uiOutput ----
		uiOutput.setFont(uiOutput.getFont().deriveFont(uiOutput.getFont().getSize() + 4f));
		contentPane.add(uiOutput);
		uiOutput.setBounds(15, 55, 335, uiOutput.getPreferredSize().height);

		//---- uiBegin ----
		uiBegin.setText("\u538b\u7f29");
		uiBegin.setFont(uiBegin.getFont().deriveFont(uiBegin.getFont().getSize() + 10f));
		uiBegin.setMargin(new Insets(2, 2, 2, 2));
		contentPane.add(uiBegin);
		uiBegin.setBounds(355, 17, 60, 60);
		contentPane.add(uiProgress);
		uiProgress.setBounds(5, 445, 480, uiProgress.getPreferredSize().height);
		contentPane.add(separator1);
		separator1.setBounds(0, 90, 490, 2);

		//---- label14 ----
		label14.setText("\u5206\u5377\u5927\u5c0f");
		contentPane.add(label14);
		label14.setBounds(new Rectangle(new Point(5, 103), label14.getPreferredSize()));
		contentPane.add(uiSplitSize);
		uiSplitSize.setBounds(55, 100, 90, uiSplitSize.getPreferredSize().height);

		//---- label3 ----
		label3.setText("\u56fa\u5b9e\u5927\u5c0f");
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(5, 128), label3.getPreferredSize()));

		//---- uiSolidSize ----
		uiSolidSize.setEnabled(false);
		contentPane.add(uiSolidSize);
		uiSolidSize.setBounds(55, 125, 90, uiSolidSize.getPreferredSize().height);

		//---- uiAutoSolidSize ----
		uiAutoSolidSize.setText("\u81ea\u52a8");
		uiAutoSolidSize.setSelected(true);
		contentPane.add(uiAutoSolidSize);
		uiAutoSolidSize.setBounds(new Rectangle(new Point(145, 124), uiAutoSolidSize.getPreferredSize()));

		//---- uiDiskCache ----
		uiDiskCache.setText("\u78c1\u76d8\u7f13\u5b58");
		contentPane.add(uiDiskCache);
		uiDiskCache.setBounds(new Rectangle(new Point(300, 124), uiDiskCache.getPreferredSize()));

		//---- separator5 ----
		separator5.setOrientation(SwingConstants.VERTICAL);
		contentPane.add(separator5);
		separator5.setBounds(130, 175, separator5.getPreferredSize().width, 155);
		contentPane.add(uiThreads);
		uiThreads.setBounds(225, 100, 70, uiThreads.getPreferredSize().height);

		//---- label2 ----
		label2.setText("\u5e76\u884c");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(198, 103), label2.getPreferredSize()));

		//---- uiSplitTask ----
		uiSplitTask.setText("\u5355\u538b\u7f29\u6d41\u5e76\u884c");
		contentPane.add(uiSplitTask);
		uiSplitTask.setBounds(new Rectangle(new Point(220, 148), uiSplitTask.getPreferredSize()));

		//---- uiSplitTaskType ----
		uiSplitTaskType.setEnabled(false);
		contentPane.add(uiSplitTaskType);
		uiSplitTaskType.setBounds(320, 150, 170, uiSplitTaskType.getPreferredSize().height);

		//---- uiMemoryLimit ----
		uiMemoryLimit.setText("4GB");
		contentPane.add(uiMemoryLimit);
		uiMemoryLimit.setBounds(225, 125, 70, uiMemoryLimit.getPreferredSize().height);

		//---- label5 ----
		label5.setText("\u5185\u5b58");
		contentPane.add(label5);
		label5.setBounds(new Rectangle(new Point(198, 128), label5.getPreferredSize()));
		contentPane.add(separator2);
		separator2.setBounds(0, 175, 495, 2);
		contentPane.add(uiPreset);
		uiPreset.setBounds(140, 180, 110, uiPreset.getPreferredSize().height);

		//---- uiFindBestProp ----
		uiFindBestProp.setText("\u9884\u6d4b");
		uiFindBestProp.setMargin(new Insets(2, 4, 2, 4));
		contentPane.add(uiFindBestProp);
		uiFindBestProp.setBounds(new Rectangle(new Point(259, 180), uiFindBestProp.getPreferredSize()));

		//---- label7 ----
		label7.setText("Lc Lp Pb");
		contentPane.add(label7);
		label7.setBounds(new Rectangle(new Point(140, 207), label7.getPreferredSize()));

		//---- label8 ----
		label8.setText("\u5b57\u5178\u5927\u5c0f");
		contentPane.add(label8);
		label8.setBounds(new Rectangle(new Point(140, 233), label8.getPreferredSize()));

		//---- label9 ----
		label9.setText("\u5355\u8bcd\u5927\u5c0f");
		contentPane.add(label9);
		label9.setBounds(new Rectangle(new Point(140, 258), label9.getPreferredSize()));

		//---- label10 ----
		label10.setText("\u641c\u7d22\u6df1\u5ea6");
		contentPane.add(label10);
		label10.setBounds(new Rectangle(new Point(140, 283), label10.getPreferredSize()));

		//---- label11 ----
		label11.setText("\u5339\u914d\u7b97\u6cd5");
		contentPane.add(label11);
		label11.setBounds(new Rectangle(new Point(140, 308), label11.getPreferredSize()));

		//---- uiLc ----
		uiLc.setModel(new SpinnerNumberModel(0, 0, 4, 1));
		contentPane.add(uiLc);
		uiLc.setBounds(new Rectangle(new Point(200, 205), uiLc.getPreferredSize()));

		//---- uiLp ----
		uiLp.setModel(new SpinnerNumberModel(0, 0, 4, 1));
		contentPane.add(uiLp);
		uiLp.setBounds(new Rectangle(new Point(233, 205), uiLp.getPreferredSize()));

		//---- uiPb ----
		uiPb.setModel(new SpinnerNumberModel(0, 0, 4, 1));
		contentPane.add(uiPb);
		uiPb.setBounds(new Rectangle(new Point(266, 205), uiPb.getPreferredSize()));
		contentPane.add(uiDictSize);
		uiDictSize.setBounds(200, 230, 95, uiDictSize.getPreferredSize().height);

		//---- uiNiceLen ----
		uiNiceLen.setModel(new SpinnerNumberModel(8, 8, 273, 1));
		contentPane.add(uiNiceLen);
		uiNiceLen.setBounds(200, 255, 95, uiNiceLen.getPreferredSize().height);
		contentPane.add(uiDepthLimit);
		uiDepthLimit.setBounds(200, 280, 95, uiDepthLimit.getPreferredSize().height);
		contentPane.add(uiMatchFinder);
		uiMatchFinder.setBounds(200, 305, 95, uiMatchFinder.getPreferredSize().height);

		//---- uiMemoryUsage ----
		uiMemoryUsage.setText("0");
		contentPane.add(uiMemoryUsage);
		uiMemoryUsage.setBounds(320, 315, 155, uiMemoryUsage.getPreferredSize().height);

		//---- uiFastLZMA ----
		uiFastLZMA.setText("\u5feb\u901fLZMA");
		contentPane.add(uiFastLZMA);
		uiFastLZMA.setBounds(new Rectangle(new Point(315, 290), uiFastLZMA.getPreferredSize()));

		//---- separator3 ----
		separator3.setOrientation(SwingConstants.VERTICAL);
		contentPane.add(separator3);
		separator3.setBounds(310, 175, separator3.getPreferredSize().width, 155);

		//---- uiCrypt ----
		uiCrypt.setText("\u52a0\u5bc6");
		contentPane.add(uiCrypt);
		uiCrypt.setBounds(new Rectangle(new Point(316, 180), uiCrypt.getPreferredSize()));

		//---- uiCryptHeader ----
		uiCryptHeader.setText("\u52a0\u5bc6\u6587\u4ef6\u540d");
		uiCryptHeader.setEnabled(false);
		contentPane.add(uiCryptHeader);
		uiCryptHeader.setBounds(new Rectangle(new Point(365, 180), uiCryptHeader.getPreferredSize()));

		//---- uiPassword ----
		uiPassword.setEnabled(false);
		contentPane.add(uiPassword);
		uiPassword.setBounds(320, 205, 150, uiPassword.getPreferredSize().height);

		//---- label12 ----
		label12.setText("\u6d3e\u751f\u5faa\u73af\u6b21\u65b9");
		contentPane.add(label12);
		label12.setBounds(new Rectangle(new Point(320, 233), label12.getPreferredSize()));

		//---- label13 ----
		label13.setText("\u76d0\u957f\u5ea6");
		contentPane.add(label13);
		label13.setBounds(new Rectangle(new Point(320, 258), label13.getPreferredSize()));

		//---- uiCyclePower ----
		uiCyclePower.setModel(new SpinnerNumberModel(19, 10, 22, 1));
		uiCyclePower.setEnabled(false);
		contentPane.add(uiCyclePower);
		uiCyclePower.setBounds(395, 230, 75, uiCyclePower.getPreferredSize().height);

		//---- uiSaltLength ----
		uiSaltLength.setModel(new SpinnerNumberModel(16, 0, 16, 1));
		uiSaltLength.setEnabled(false);
		contentPane.add(uiSaltLength);
		uiSaltLength.setBounds(360, 255, 110, uiSaltLength.getPreferredSize().height);
		contentPane.add(separator4);
		separator4.setBounds(310, 285, 185, 2);

		//---- uiCompressHeader ----
		uiCompressHeader.setText("\u538b\u7f29\u5143\u6570\u636e");
		uiCompressHeader.setSelected(true);
		contentPane.add(uiCompressHeader);
		uiCompressHeader.setBounds(new Rectangle(new Point(5, 180), uiCompressHeader.getPreferredSize()));

		//---- uiFastCheck ----
		uiFastCheck.setText("\u5feb\u901f\u91cd\u590d\u68c0\u6d4b(CRC32+ModTime)");
		uiFastCheck.setSelected(true);
		contentPane.add(uiFastCheck);
		uiFastCheck.setBounds(new Rectangle(new Point(5, 148), uiFastCheck.getPreferredSize()));
		contentPane.add(uiAppendOptions);
		uiAppendOptions.setBounds(5, 205, 120, uiAppendOptions.getPreferredSize().height);

		//---- label6 ----
		label6.setText("\u5728\u5143\u6570\u636e\u4e2d\u4fdd\u5b58");
		contentPane.add(label6);
		label6.setBounds(new Rectangle(new Point(5, 230), label6.getPreferredSize()));

		//---- uiStoreMT ----
		uiStoreMT.setText("\u4fee\u6539\u65f6\u95f4");
		uiStoreMT.setSelected(true);
		contentPane.add(uiStoreMT);
		uiStoreMT.setBounds(new Rectangle(new Point(5, 245), uiStoreMT.getPreferredSize()));

		//---- uiStoreCT ----
		uiStoreCT.setText("\u521b\u5efa\u65f6\u95f4");
		contentPane.add(uiStoreCT);
		uiStoreCT.setBounds(new Rectangle(new Point(5, 265), uiStoreCT.getPreferredSize()));

		//---- uiStoreAT ----
		uiStoreAT.setText("\u8bbf\u95ee\u65f6\u95f4");
		contentPane.add(uiStoreAT);
		uiStoreAT.setBounds(new Rectangle(new Point(5, 285), uiStoreAT.getPreferredSize()));

		//---- uiStoreAttr ----
		uiStoreAttr.setText("\u6587\u4ef6\u6743\u9650 (DOS)");
		contentPane.add(uiStoreAttr);
		uiStoreAttr.setBounds(new Rectangle(new Point(5, 305), uiStoreAttr.getPreferredSize()));

		//---- uiStoreFolder ----
		uiStoreFolder.setText("\u6587\u4ef6\u5939");
		uiStoreFolder.setSelected(true);
		contentPane.add(uiStoreFolder);
		uiStoreFolder.setBounds(new Rectangle(new Point(5, 325), uiStoreFolder.getPreferredSize()));
		contentPane.add(separator6);
		separator6.setBounds(130, 330, 180, 2);

		//---- uiBCJ ----
		uiBCJ.setText("\u5bf9\u53ef\u6267\u884c\u6587\u4ef6\u4f7f\u7528BCJ");
		uiBCJ.setSelected(true);
		contentPane.add(uiBCJ);
		uiBCJ.setBounds(new Rectangle(new Point(300, 99), uiBCJ.getPreferredSize()));

		//---- uiBCJ2 ----
		uiBCJ2.setText("BCJ2");
		contentPane.add(uiBCJ2);
		uiBCJ2.setBounds(new Rectangle(new Point(440, 99), uiBCJ2.getPreferredSize()));

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiLog);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(80, 340, 405, 100);

		//---- label1 ----
		label1.setText("Log =>");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(30, 375), label1.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(495, 460));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextField uiInput;
	private JTextField uiOutput;
	private JButton uiBegin;
	private JProgressBar uiProgress;
	private JTextField uiSplitSize;
	private JTextField uiSolidSize;
	private JCheckBox uiAutoSolidSize;
	private JCheckBox uiDiskCache;
	private JSpinner uiThreads;
	private JCheckBox uiSplitTask;
	private JComboBox<String> uiSplitTaskType;
	private JTextField uiMemoryLimit;
	private JComboBox<CMBoxValue> uiPreset;
	private JButton uiFindBestProp;
	private JSpinner uiLc;
	private JSpinner uiLp;
	private JSpinner uiPb;
	private JTextField uiDictSize;
	private JSpinner uiNiceLen;
	private JSpinner uiDepthLimit;
	private JComboBox<CMBoxValue> uiMatchFinder;
	private JLabel uiMemoryUsage;
	private JCheckBox uiFastLZMA;
	private JCheckBox uiCrypt;
	private JCheckBox uiCryptHeader;
	private JTextField uiPassword;
	private JSpinner uiCyclePower;
	private JSpinner uiSaltLength;
	private JCheckBox uiCompressHeader;
	private JCheckBox uiFastCheck;
	private JComboBox<String> uiAppendOptions;
	private JCheckBox uiStoreMT;
	private JCheckBox uiStoreCT;
	private JCheckBox uiStoreAT;
	private JCheckBox uiStoreAttr;
	private JCheckBox uiStoreFolder;
	private JCheckBox uiBCJ;
	private JCheckBox uiBCJ2;
	private JTextArea uiLog;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
