/*
 * Created by JFormDesigner on Tue Nov 28 01:30:28 CST 2023
 */

package roj.archive.ui;

import roj.RojLib;
import roj.archive.qz.QZArchive;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMA2Parallel;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.io.FastFailException;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.ui.*;
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
import java.util.List;

/**
 * @author Roj234
 */
public class QZArchiverUI extends JFrame {
	public static void main(String[] args) throws Exception {
		GuiUtil.systemLook();
		QZArchiverUI f = new QZArchiverUI();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
	}

	private final LZMA2Options options = new LZMA2Options();
	private final OnChangeHelper helper;
	private final ActionListener beginListener;

	private void begin() throws Exception {
		if (uiOutput.getText().isEmpty()) {
			File file = GuiUtil.fileSaveTo("保存到何方?", "新建 7z 压缩文件.7z", this);
			if (file == null) return;
			uiOutput.setText(file.getAbsolutePath());
		}

		File out = new File(uiOutput.getText());
		boolean ok = true;
		try {
			if (!out.isFile() && (!out.createNewFile() || !out.delete())) ok = false;
		} catch (Exception e) {
			ok = false;
		}

		if (!ok) {
			JOptionPane.showMessageDialog(this, out.getAbsolutePath()+"\n请注意，输出路径包括文件名称和扩展名", "路径不合法", JOptionPane.ERROR_MESSAGE);
			return;
		}

		QZArchiver arc = new QZArchiver();
		List<File> input = new SimpleList<>();
		input.add(new File(uiInput.getText()));
		if (uiReadDirFromLog.isSelected()) {
			for (String line : LineReader.create(uiLog.getText())) {
				if (!line.isEmpty()) input.add(new File(line));
			}
		}
		arc.input = input;
		arc.storeFolder = uiStoreFolder.isSelected();
		arc.storeMT = uiStoreMT.isSelected();
		arc.storeCT = uiStoreCT.isSelected();
		arc.storeAT = uiStoreAT.isSelected();
		arc.storeAttr = uiStoreAttr.isSelected();
		if (!(arc.autoSolidSize = uiAutoSolidSize.isSelected())) {
			arc.solidSize = (long) TextUtil.unscaledNumber1024(uiSolidSize.getText());
		}
		if (uiSplitTask.isSelected() & !uiAutoSplitTask.isSelected() & !uiMixedMode.isSelected()) {
			arc.autoSolidSize = false;
			arc.solidSize = -1;
			arc.threads = 1;
		}
		else arc.threads = ((Number) uiThreads.getValue()).intValue();
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
		arc.cacheFolder = uiDiskCache.isSelected() ? new File(System.getProperty("roj.archiver.temp", ".")) : null;
		arc.outputFolder = out.getParentFile();
		arc.outputName = out.getName();
		arc.keepArchive = uiKeepArchive.isSelected();
		if (uiSortByFilename.isSelected()) {
			arc.sorter = (f1, f2) -> f1.getName().compareTo(f2.getName());
		}

		uiLog.setText("正在计数文件\n");

		long chunkSize = arc.prepare();
		if (chunkSize < 0) {
			uiLog.append("压缩文件无需更新\n");
			return;
		}

		options.setNativeAccelerate(uiNativeAccel.isSelected());

		int[] arr = createTaskPool();
		int threads = arr[0];
		TaskPool pool2;
		BufferPool buffer;

		options.clearAsyncMode();
		if (uiSplitTask.isSelected()) {
			LZMA2Options myOpt;
			if (!uiAutoSplitTask.isSelected()) {
				myOpt = options;
			} else {
				arc.autoSplitTaskSize = Integer.parseInt(JOptionPane.showInputDialog(this, "在并行小于多少时开始拆分任务？", Math.max(threads/2, 3)));
				myOpt = arc.autoSplitTaskOptions = arc.options.clone();
			}
			int blockSize = (int) MathUtils.clamp(chunkSize, LZMA2Options.ASYNC_BLOCK_SIZE_MIN, LZMA2Options.ASYNC_BLOCK_SIZE_MAX);

			LZMA2Parallel man = new LZMA2Parallel(myOpt, blockSize, uiSplitTaskType.getSelectedIndex(), threads);
			long mem = (man.getExtraMemoryUsageBytes(uiMixedMode.isSelected()) & ~7) + (16 * threads);// 8-byte alignment
			long needed = mem - ((long) arr[1]<<10);
			if (needed > 0) {
				JOptionPane.showMessageDialog(this, "压缩所需的内存超过了内存输入框的限制\n还需要"+TextUtil.scaledNumber1024(needed)+"的内存", "压缩流并行导致的内存不足", JOptionPane.ERROR_MESSAGE);
				return;
			}
			uiLog.append("压缩流并行需要的额外内存:"+TextUtil.scaledNumber1024(mem)+"\n");

			pool2 = TaskPool.MaxThread(threads, "split-worker-");
			buffer = new BufferPool(mem, 0, mem, 0, 0, 0, 0, 0, threads, 0, BufferPool.OOM_NULL);

			myOpt.setAsyncMode(pool2, buffer, man);
			if (arc.threads == 1) threads = 1;
		} else {
			pool2 = null;
			buffer = null;
		}

		TaskPool pool = TaskPool.MaxThread(threads, "7z-worker-");

		ActionListener stopListener = e -> {
			arc.interrupt();
			pool.shutdownAndCancel();
		};

		uiBegin.setText("停下");
		uiBegin.removeActionListener(beginListener);
		uiBegin.addActionListener(stopListener);

		TaskPool.Common().submit(() -> {
			pool.setRejectPolicy(TaskPool::waitPolicy);
			EasyProgressBar bar = new GuiProgressBar(uiLog, uiProgress);
			try {
				arc.compress(pool, bar);
			} finally {
				pool.shutdownAndCancel();
				if (pool2 != null) {
					pool2.shutdownAndCancel();

					try {
						buffer.release();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				uiProgress.setValue(10000);
				uiBegin.setText("压缩");
				uiBegin.removeActionListener(stopListener);
				uiBegin.addActionListener(beginListener);
			}
			new QZArchive(out, arc.password).close();
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
		md.addElement(new CMBoxValue("仅存储", 0));

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
			int level = ((CMBoxValue) uiPreset.getSelectedItem()).value;
			if (level == 0) {
				options.setMode(LZMA2Options.MODE_UNCOMPRESSED);
			} else {
				options.setPreset(level);
			}

			uiBCJ2.setSelected(level == 9);
			syncToUI(true);
		});
		uiPreset.setSelectedIndex(5);

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
			File[] files = GuiUtil.filesLoadFrom("选择一些文件来测试最好的LcLpPb及压缩率", this, JFileChooser.FILES_ONLY);
			if (files == null) return;

			uiFindBestProp.setEnabled(false);
			uiLog.setText("开始测试,时间依据您除了LcLpPb的LZMA设定而变化,请耐心等待\n" +
				"请不要在测试过程中修改LZMA设定,这会导致结果不准确");

			TaskPool.Common().submit(() -> {
				TaskPool pool = TaskPool.MaxThread(createTaskPool()[0], "LcLpTest-worker-");
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
					pool.shutdownAndCancel();
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
		md.addElement("忽略压缩包内容");
		md.addElement("计算差异的文件");
		uiAppendOptions.setModel(Helpers.cast(md));
		md = new DefaultComboBoxModel<>();
		md.addElement("相对路径"); // RELATIVE_PATH
		md.addElement("最小不重复路径"); // FULL_PATH
		md.addElement("绝对路径"); // ABSOLUTE_PATH
		uiPathType.setModel(Helpers.cast(md));
		// endregion
		// region
		uiThreads.setModel(new SpinnerNumberModel(Runtime.getRuntime().availableProcessors(), 1,256,1));
		uiMemoryLimit.setText(toDigital(VMUtil.usableMemory()));

		uiAutoSolidSize.addActionListener(e -> uiSolidSize.setEnabled(!uiAutoSolidSize.isSelected()));
		uiBCJ.addActionListener(e -> uiBCJ2.setEnabled(uiBCJ.isSelected()));

		uiLog.setText("""
				Roj234©可扩展并行7z压缩软件v2.0 帮助指南
				  · 从绝对的可能性来说，相同的文件压缩出来有可能校验值不同，在有大小核的处理器上可能性会很高
				  · CPU跑不满：开启【压缩流并行】或者减小【固实大小】
				  · 压缩任何大小（确切的说，是压缩后大小）超过2GB的文件时，务必打开磁盘缓存，否则会压缩失败
				  · 软件的弹窗都有意义，请仔细阅读""");

		ActionListener[] tip = new ActionListener[3];
		tip[0] = e -> {
			JOptionPane.showMessageDialog(this, """
				当【文件大小】的方差较大时，在压缩的最后阶段，CPU可能跑不满
				开启该选项来解决此问题，但是会导致：
				  · 进度条不动弹，在几分钟后瞬间结束
				  · 压缩率降低千分之0.3-1.5
				
				注意事项：
				  · 开启后，【固实大小】越小，压缩效果越差，CPU占用率也越低
				    （和不开启时相反）
				  · 若CPU跑不满
				    1. 取消勾选【固实大小】的【自动】，并填写【1TB】
				    2. 开启【混合模式】，CPU必定跑满（会使用更多内存）
				  · 开启【自动拆分任务】，可以在不降低很多速度的同时，提升压缩效果""", "关于压缩流并行……", JOptionPane.WARNING_MESSAGE);
			uiSplitTask.removeActionListener(tip[0]);
		};
		tip[1] = e -> {
			JOptionPane.showMessageDialog(this, """
				1. 在压缩流并行时（或许，看你的设定）提高压缩速度
				2. 会使用非常多的内存
				3. 建议同时开启磁盘缓存,不然内存可能不够""", "关于混合模式……", JOptionPane.WARNING_MESSAGE);
			uiMixedMode.removeActionListener(tip[1]);
		};
		tip[2] = e -> updateMemoryUsage();

		uiSplitTask.addActionListener(tip[0]);
		uiMixedMode.addActionListener(tip[1]);
		uiSplitTask.addActionListener(tip[2]);
		uiMixedMode.addActionListener(tip[2]);
		uiAutoSolidSize.addActionListener(tip[2]);
		uiAutoSplitTask.addActionListener(tip[2]);
		uiAutoSplitTask.addActionListener(e -> {
			boolean b = uiAutoSplitTask.isSelected();
			uiMixedMode.setEnabled(!b);
			if (b) uiMixedMode.setSelected(false);
		});
		helper.addEventListener(uiSolidSize, e -> updateMemoryUsage());
		uiSplitTaskType.addActionListener(tip[2]);
		uiSplitTask.addActionListener(e -> {
			boolean b = uiSplitTask.isSelected();
			uiSplitTaskType.setEnabled(b);
			uiAutoSplitTask.setEnabled(b);
			uiMixedMode.setEnabled(b);
		});
		md = new DefaultComboBoxModel<>();
		md.addElement("压缩率中低|压缩快|内存低");
		md.addElement("压缩率中高|压缩慢|内存中");
		md.addElement("压缩率中高|压缩中|内存高");
		uiSplitTaskType.setModel(Helpers.cast(md));
		// endregion
		GuiUtil.dropFilePath(uiInput, null, false);
		GuiUtil.dropFilePath(uiOutput, null, false);
		uiBegin.addActionListener(beginListener = e -> {
			try {
				begin();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		});
		uiHideComplicate.addActionListener(e -> {
			boolean v = uiHideComplicate.isSelected();
			Component[] c = {uiPathType,uiKeepArchive,uiStoreAT,uiStoreAttr,uiStoreCT,uiStoreFolder,uiStoreMT,uiCyclePower,uiSaltLength,uiFastLZMA,uiFastCheck,uiCompressHeader,uiBCJ,uiBCJ2,uiMatchFinder,uiLc,uiLp,uiPb,uiDepthLimit,uiReadDirFromLog};
			for (Component cc : c) {
				cc.setVisible(!v);
			}
		});

		if (!RojLib.hasNative(RojLib.FAST_LZMA))
			uiNativeAccel.setEnabled(false);
		else {
			uiNativeAccel.addActionListener(e -> {
				JOptionPane.showConfirmDialog(this, "作者C++技术力不行，压缩后可能没法解压（数据错误）\n请自行决定是否使用");
			});
		}
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
		long myUsage = options.getEncoderMemoryUsage() * 1024L;
		String text = uiSolidSize.getText();
		String msg = "";
		if (uiSplitTask.isSelected() && !uiAutoSplitTask.isSelected()) {
			if (text.isEmpty() || uiAutoSolidSize.isSelected()) {
				msg = "约";
				text = "64M";
			}

			int blockSize = MathUtils.clamp((int) TextUtil.unscaledNumber1024(text), LZMA2Options.ASYNC_BLOCK_SIZE_MIN, LZMA2Options.ASYNC_BLOCK_SIZE_MAX);
			LZMA2Parallel man = new LZMA2Parallel(options, blockSize, uiSplitTaskType.getSelectedIndex(), 1);
			myUsage += man.getExtraMemoryUsageBytes(uiMixedMode.isSelected());
		}
		uiMemoryUsage.setText("内存/线程:"+msg+toDigital(myUsage));
	}
	private int[] createTaskPool() {
		int threads = ((Number) uiThreads.getValue()).intValue();

		String text = uiMemoryLimit.getText();
		double memoryLimit;
		if (text.isEmpty()) memoryLimit = (VMUtil.usableMemory() >>> 10);
		else memoryLimit = TextUtil.unscaledNumber1024(text) / 1024;

		double perThreadUsageKb = options.getEncoderMemoryUsage() + 256;

		threads = (int) Math.min(threads, Math.floor(memoryLimit / perThreadUsageKb));

		uiLog.append("实际能创建的线程:"+threads+'\n');

		return new int[] {threads, (int) (memoryLimit - (perThreadUsageKb * threads))};
	}

	private static String toDigital(long size) { return TextUtil.scaledNumber1024(size); }
	private int fromDigital(String text) {
		double v = text.isEmpty() ? 0 : TextUtil.unscaledNumber1024(text);
		if (v > Integer.MAX_VALUE) throw new FastFailException(text+"超出了整型范围！");
		return (int) v;
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        var label4 = new JLabel();
        var label15 = new JLabel();
        uiInput = new JTextField();
        uiOutput = new JTextField();
        uiKeepArchive = new JCheckBox();
        uiPathType = new JComboBox<>();
        uiBegin = new JButton();
        uiHideComplicate = new JCheckBox();
        uiNativeAccel = new JCheckBox();
        uiProgress = new JProgressBar();
        var separator1 = new JSeparator();
        var label14 = new JLabel();
        uiSplitSize = new JTextField();
        var label3 = new JLabel();
        uiSolidSize = new JTextField();
        uiAutoSolidSize = new JCheckBox();
        uiDiskCache = new JCheckBox();
        var separator5 = new JSeparator();
        uiThreads = new JSpinner();
        var label2 = new JLabel();
        uiSplitTask = new JCheckBox();
        uiSplitTaskType = new JComboBox<>();
        uiMemoryLimit = new JTextField();
        var label5 = new JLabel();
        var separator2 = new JSeparator();
        uiPreset = new JComboBox<>();
        uiFindBestProp = new JButton();
        var label7 = new JLabel();
        var label8 = new JLabel();
        var label9 = new JLabel();
        var label10 = new JLabel();
        var label11 = new JLabel();
        uiLc = new JSpinner();
        uiLp = new JSpinner();
        uiPb = new JSpinner();
        uiDictSize = new JTextField();
        uiNiceLen = new JSpinner();
        uiDepthLimit = new JSpinner();
        uiMatchFinder = new JComboBox<>();
        uiMemoryUsage = new JLabel();
        uiFastLZMA = new JCheckBox();
        var separator3 = new JSeparator();
        uiCrypt = new JCheckBox();
        uiCryptHeader = new JCheckBox();
        uiPassword = new JTextField();
        var label12 = new JLabel();
        var label13 = new JLabel();
        uiCyclePower = new JSpinner();
        uiSaltLength = new JSpinner();
        var separator4 = new JSeparator();
        uiCompressHeader = new JCheckBox();
        uiFastCheck = new JCheckBox();
        uiAppendOptions = new JComboBox<>();
        var label6 = new JLabel();
        uiStoreMT = new JCheckBox();
        uiStoreCT = new JCheckBox();
        uiStoreAT = new JCheckBox();
        uiStoreAttr = new JCheckBox();
        uiStoreFolder = new JCheckBox();
        var separator6 = new JSeparator();
        uiBCJ = new JCheckBox();
        uiBCJ2 = new JCheckBox();
        var scrollPane1 = new JScrollPane();
        uiLog = new JTextArea();
        var label1 = new JLabel();
        uiAutoSplitTask = new JCheckBox();
        uiMixedMode = new JCheckBox();
        uiReadDirFromLog = new JCheckBox();
        uiSortByFilename = new JCheckBox();

        //======== this ========
        setTitle("Roj234 SevenZ Archiver 2.6");
        var contentPane = getContentPane();
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
        uiOutput.setBounds(15, 60, 335, uiOutput.getPreferredSize().height);

        //---- uiKeepArchive ----
        uiKeepArchive.setText("\u4fdd\u7559\u539f\u538b\u7f29\u5305");
        contentPane.add(uiKeepArchive);
        uiKeepArchive.setBounds(new Rectangle(new Point(135, 40), uiKeepArchive.getPreferredSize()));
        contentPane.add(uiPathType);
        uiPathType.setBounds(240, 40, 110, uiPathType.getPreferredSize().height);

        //---- uiBegin ----
        uiBegin.setText("\u538b\u7f29");
        uiBegin.setFont(uiBegin.getFont().deriveFont(uiBegin.getFont().getSize() + 10f));
        uiBegin.setMargin(new Insets(2, 2, 2, 2));
        contentPane.add(uiBegin);
        uiBegin.setBounds(355, 17, 60, 60);

        //---- uiHideComplicate ----
        uiHideComplicate.setText("\u6211\u662f\u65b0\u624b");
        contentPane.add(uiHideComplicate);
        uiHideComplicate.setBounds(new Rectangle(new Point(425, 20), uiHideComplicate.getPreferredSize()));

        //---- uiNativeAccel ----
        uiNativeAccel.setText("C\u8bed\u8a00\u52a0\u901f");
        contentPane.add(uiNativeAccel);
        uiNativeAccel.setBounds(new Rectangle(new Point(425, 50), uiNativeAccel.getPreferredSize()));

        //---- uiProgress ----
        uiProgress.setMaximum(10000);
        contentPane.add(uiProgress);
        uiProgress.setBounds(5, 480, 500, uiProgress.getPreferredSize().height);
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
        uiMemoryUsage.setBounds(320, 315, 170, uiMemoryUsage.getPreferredSize().height);

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
        scrollPane1.setBounds(5, 355, 495, 120);

        //---- label1 ----
        label1.setText("Log \ud83d\udc47");
        contentPane.add(label1);
        label1.setBounds(new Rectangle(new Point(80, 340), label1.getPreferredSize()));

        //---- uiAutoSplitTask ----
        uiAutoSplitTask.setText("\u81ea\u52a8\u62c6\u5206");
        uiAutoSplitTask.setEnabled(false);
        contentPane.add(uiAutoSplitTask);
        uiAutoSplitTask.setBounds(new Rectangle(new Point(370, 125), uiAutoSplitTask.getPreferredSize()));

        //---- uiMixedMode ----
        uiMixedMode.setText("\u6df7\u5408\u6a21\u5f0f");
        uiMixedMode.setEnabled(false);
        contentPane.add(uiMixedMode);
        uiMixedMode.setBounds(new Rectangle(new Point(440, 125), uiMixedMode.getPreferredSize()));

        //---- uiReadDirFromLog ----
        uiReadDirFromLog.setText("\u4eceLog\u8bfb\u53d6\u66f4\u591a\u6587\u4ef6(\u5939) (\u6bcf\u884c\u4e00\u4e2a)");
        contentPane.add(uiReadDirFromLog);
        uiReadDirFromLog.setBounds(new Rectangle(new Point(125, 333), uiReadDirFromLog.getPreferredSize()));

        //---- uiSortByFilename ----
        uiSortByFilename.setText("\u6309\u6587\u4ef6\u540d\u6392\u5e8f");
        contentPane.add(uiSortByFilename);
        uiSortByFilename.setBounds(new Rectangle(new Point(395, 290), uiSortByFilename.getPreferredSize()));

        contentPane.setPreferredSize(new Dimension(510, 495));
        pack();
        setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JTextField uiInput;
    private JTextField uiOutput;
    private JCheckBox uiKeepArchive;
    private JComboBox<String> uiPathType;
    private JButton uiBegin;
    private JCheckBox uiHideComplicate;
    private JCheckBox uiNativeAccel;
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
    private JCheckBox uiAutoSplitTask;
    private JCheckBox uiMixedMode;
    private JCheckBox uiReadDirFromLog;
    private JCheckBox uiSortByFilename;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}