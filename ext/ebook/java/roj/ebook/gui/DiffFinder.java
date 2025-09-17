/*
 * Created by JFormDesigner on Mon Mar 04 19:06:19 CST 2024
 */

package roj.ebook.gui;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.CharMap;
import roj.concurrent.Timer;
import roj.concurrent.*;
import roj.config.ConfigMaster;
import roj.config.ValueEmitter;
import roj.config.YamlSerializer;
import roj.config.mapper.ObjectMapper;
import roj.config.mapper.ObjectMapperFactory;
import roj.gui.GuiUtil;
import roj.gui.OnChangeHelper;
import roj.io.IOUtil;
import roj.reflect.Unsafe;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.text.TextWriter;
import roj.text.diff.BsDiff;
import roj.text.diff.DiffInfo;
import roj.text.logging.LogDestination;
import roj.text.logging.Logger;
import roj.util.ArrayCache;
import roj.util.FastFailException;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.reflect.Unsafe.U;
import static roj.text.diff.DiffInfo.bar;

/**
 * @author Roj234
 */
public class DiffFinder extends JFrame {
	public static void main(String[] args) {
		GuiUtil.systemLaf();
		DiffFinder f = new DiffFinder();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private static final class Group {
		String constraint;
		Pattern isRegexp;

		@Override
		public String toString() { return isRegexp == null ? "indexOf:"+constraint : "regex:"+isRegexp.pattern(); }
	}
	private static final class FileMeta {
		String path;
		int group, bucket;
		transient byte[] data;
	}

	private Pattern nameFilter;
	private final DefaultListModel<Group> model = new DefaultListModel<>();
	private ActionListener startListener, stopListener;

	public DiffFinder() {
		initComponents();
		uiGroups.setModel(model);

		GuiUtil.dropFilePath(uiInput, null, false);
		GuiUtil.dropFilePath(uiOutput, null, false);

		OnChangeHelper ch = new OnChangeHelper(this);

		Color fg = uiNameFilter.getForeground();
		nameFilter = Pattern.compile(uiNameFilter.getText());
		ch.addEventListener(uiNameFilter, tf -> {
			String text = tf.getText();
			try {
				nameFilter = Pattern.compile(text);
				tf.setForeground(fg);
				tf.setToolTipText(null);
			} catch (Exception e) {
				nameFilter = null;
				tf.setForeground(Color.RED);
				tf.setToolTipText("正则表达式错误: "+e.getMessage());
			}
		});

		ChangeListener constraint = e -> {
			if (((int) uiSlideWindow.getValue()) > ((int) uiPreWindow.getValue())) {
				String msg = "滑动窗口不能大于快速窗口";
				JOptionPane.showMessageDialog(this, msg);
				throw new FastFailException(msg);
			}
		};
		uiPreWindow.addChangeListener(constraint);
		uiSlideWindow.addChangeListener(constraint);

		uiGroupAdd.addActionListener(e -> {
			String text = uiGroupInput.getText();
			if (text.isEmpty()) {
				JOptionPane.showMessageDialog(this, "不能为空");
				return;
			}
			Group group = new Group();

			if (uiGroupIsRegex.isSelected()) {
				try {
					group.isRegexp = Pattern.compile(text);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "正则表达式错误: \n"+ex.getMessage());
					return;
				}
			} else {
				group.constraint = text;
			}

			model.addElement(group);
		});

		uiGroups.getSelectionModel().addListSelectionListener(e -> {
			uiGroupDel.setEnabled(uiGroups.getSelectedValue() != null);
		});
		uiGroupDel.addActionListener(e -> {
			int[] idx = uiGroups.getSelectedIndices();
			Arrays.sort(idx);
			for (int i = idx.length - 1; i >= 0; i--) {
				model.remove(i);
			}
		});

		startListener = e -> {
			File result = new File(uiOutput.getText());
			if (result.exists()) {
				if (!result.isFile()) {
					JOptionPane.showMessageDialog(this, "输出是文件夹！");
					return;
				}
			}

			File base = null;
			int finished = 0;
			FileMeta[] metas = null;
			int preWindow = 0;
			int slideWindow = 0;

			File progress = new File(result.getAbsolutePath() + ".ckpt");
			if (progress.isFile()) {
				int i = JOptionPane.showConfirmDialog(this, "找到之前的检查点，是否从检查点继续？\n继续将会覆盖你的分组设置！", "找到进度", JOptionPane.YES_NO_OPTION);
				if (i != JOptionPane.YES_OPTION) {
					if (!progress.delete()) {
						JOptionPane.showMessageDialog(this, "无法删除检查点！");
						return;
					}
				} else {
					try (DataInputStream in = new DataInputStream(new FileInputStream(progress))) {
						finished = in.readInt();
						preWindow = in.readInt();
						slideWindow = in.readInt();
						base = new File(in.readUTF());
						metas = ConfigMaster.NBT.readObject(FileMeta[].class, in);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(this, "检查点加载失败！\n" + ex.getMessage());
						return;
					}
				}
			}
			if (metas == null) {
				if (nameFilter == null) {
					JOptionPane.showMessageDialog(this, "正则过滤器有误！");
					return;
				}
				preWindow = (int) uiPreWindow.getValue();
				slideWindow = (int) uiSlideWindow.getValue();

				Matcher m = nameFilter.matcher("");
				Matcher[] matchers = new Matcher[model.size()];
				for (int i = 0; i < model.size(); i++) {
					Group g = model.get(i);
					matchers[i] = (g.isRegexp == null ? Pattern.compile(g.constraint, Pattern.LITERAL) : g.isRegexp).matcher("");
				}

				base = new File(uiInput.getText());
				int baseLen = base.getAbsolutePath().length() + 1;
				List<File> files = IOUtil.listFiles(base, file -> m.reset(file.getAbsolutePath().substring(baseLen)).find());

				long bucketSize = (long) TextUtil.unscaledNumber1024(uiBucketSize.getText());
				metas = new FileMeta[files.size()];

				for (int i = 0; i < files.size(); i++) {
					File f = files.get(i);
					FileMeta meta = new FileMeta();
					String path = f.getAbsolutePath().substring(baseLen);
					int j = 0;
					for (; j < matchers.length; j++) {
						if (matchers[j].reset(path).find()) break;
					}
					meta.path = path;
					meta.bucket = (int) (f.length() / bucketSize);
					meta.group = j == matchers.length ? -1 : j;

					metas[i] = meta;
				}

				try (DataOutputStream out = new DataOutputStream(new FileOutputStream(progress))) {
					out.writeInt(0);
					out.writeInt(preWindow);
					out.writeInt(slideWindow);
					out.writeUTF(base.getAbsolutePath());
					ConfigMaster.NBT.writeObject(metas, out);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "检查点保存失败！\n" + ex.getMessage());
					return;
				}
			}

			runDiffAsync(finished, base, metas, progress, result, preWindow, slideWindow);

			uiTogle.setText("停止");
			uiTogle.removeActionListener(startListener);
			uiTogle.addActionListener(stopListener);
		};
		stopListener = e -> {
			runner.stop();

			uiTogle.setText("开始");
			uiTogle.removeActionListener(stopListener);
			uiTogle.addActionListener(startListener);
		};
		uiTogle.addActionListener(startListener);
	}

	private static final CharMap<String> textRpl = new CharMap<>();
	static {
		textRpl.put('\r', "");
		textRpl.put('\n', "");
		textRpl.put('\t', "");
		textRpl.put(' ', "");
		textRpl.put('　', "");
		Logger.getRootContext().destination(LogDestination.stdout());
	}
	private static final Logger LOGGER = Logger.getLogger("Differ");
	private void runDiffAsync(int completed, File base, FileMeta[] metas, File progress, File result, int preWindow, int slideWindow) {
		List<List<FileMeta>> layered = new ArrayList<>();
		for (FileMeta meta : metas) {
			int bucket = meta.bucket;
			while (layered.size() <= bucket) layered.add(new ArrayList<>());
			layered.get(bucket).add(meta);
		}

		Comparator<FileMeta> groupCmp = (o1, o2) -> Integer.compare(o1.group, o2.group);
		for (int i = 0; i < layered.size(); i++) layered.get(i).sort(groupCmp);

		runner = new TaskRunner(Runtime.getRuntime().availableProcessors());
		runner.finish_callback = () -> {
			uiCompareState.setText("任务已结束");
			stopListener.actionPerformed(null);
			progress.delete();
		};

		bar.reset();
		bar.setName("初始化比较器");
		runner.initComparator(base, metas, preWindow, runner.comparator);

		bar.reset();
		bar.setName("比较文件");
		runner.start(progress, result, layered, completed, slideWindow);
	}
	static final double DIFF_MAX = 1/3d;
	private TaskRunner runner;
	class TaskRunner {
		Runnable finish_callback;
		TaskRunner(int core) {
			generator = new TaskThread();
			generator.setName("DIFFTaskGen");
			generator.start();

			cleanup = new TaskThread();
			cleanup.setName("DIFFCleanup");
			cleanup.start();

			comparator = TaskPool.newFixed(core, "DIFFCompare-");
		}
		private final TaskThread generator, cleanup;
		private final TaskPool comparator;

		private volatile boolean terminateFlag;

		private final ObjectMapper<DiffInfo> writer = ObjectMapperFactory.SAFE.serializer(DiffInfo.class);
		private ValueEmitter result;

		final void initComparator(File base, FileMeta[] metas, int preWindow, TaskPool POOL) {
			bar.addTotal(metas.length);
			AtomicLong memoryUsage = new AtomicLong();
			boolean notText = uiNotTextCompare.isSelected();
			for (FileMeta meta : metas) {
				if (notText) {
					POOL.executeUnsafe(() -> {
						byte[] out = ArrayCache.getByteArray(preWindow * 2, false);
						try (FileInputStream in = new FileInputStream(new File(base, meta.path))) {
							int i = 0;
							while (true) {
								int r = in.read(out, i, out.length - i);
								if (r < 0) break;
								i = r;
							}

							meta.data = Arrays.copyOf(out, i);
							ArrayCache.putArray(out);
							memoryUsage.addAndGet(i);

							bar.increment(1);
						} catch (Exception e) {
							LOGGER.error("读取文件{}发生了错误", e, meta.path);
						}
					});
				} else {
					POOL.executeUnsafe(() -> {
						byte[] out = ArrayCache.getByteArray(preWindow * 2, false);
						CharList sb = new CharList();
						try (TextReader in = TextReader.auto(new File(base, meta.path))) {
							int i = 0;
							while (in.readLine(sb)) {
								sb.replaceBatch(textRpl);

								int canCopy = Math.min(sb.length(), preWindow-i);
								U.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 2L, canCopy * 2L);
								if ((i += canCopy) == preWindow) break;

								sb.clear();
							}

							meta.data = Arrays.copyOf(out, i);
							ArrayCache.putArray(out);
							memoryUsage.addAndGet(i);

							bar.increment(1);
						} catch (Exception e) {
							LOGGER.error("读取文件{}发生了错误", e, meta.path);
						}

						sb._free();
					});
				}
			}
			mem = memoryUsage.get();
			updateMemoryUsage();
		}

		void start(File progress_, File output, List<List<FileMeta>> layered, int completed, int window) {
			progressOffset = completed;
			lastProgress = 0;
			generator.executeUnsafe(() -> {
				try {
					progressFile = new RandomAccessFile(progress_, "rwd");
					result = new YamlSerializer().to(TextWriter.append(output));
					result.emitList();
					taskGenerateThread(layered, window);
				} catch (Exception e) {
					LOGGER.error("发生了异常", e);
					stop();
				}
			});
		}

		private TimerTask updateProgressTask;
		private RandomAccessFile progressFile;
		private int progressOffset, progressMax;
		private BitSet progressState;
		private volatile int lastProgress;

		void updateProgress() {
			if (terminateFlag) {
				stop();
				return;
			}

			int next;
			synchronized (progressState) { next = progressState.nextFalse(lastProgress); }
			if (next != lastProgress) {
				DiffFinder.this.uiCompareState.setText("当前进度: "+next+"/"+progressMax);
				lastProgress = next;
				try {
					progressFile.seek(0);
					progressFile.writeInt(next);
				} catch (IOException e){
					LOGGER.error("无法保存进度", e);
				}
			}
		}

		private void taskGenerateThread(List<List<FileMeta>> layered, int slideWindow) {
			int skip = progressOffset;
			BitSet finishedBlock = new BitSet(layered.size());

			int taskCount = 0;

			int i = layered.size()-1;
			for (int j = i; j >= 0; j--) {
				List<FileMeta> prev = j==0?Collections.emptyList():layered.get(j-1);
				List<FileMeta> self = layered.get(j);

				int cmpCount = self.size() * prev.size() + (self.size() - 1) * self.size() / 2;
				bar.addTotal(cmpCount);

				taskCount += self.size();
				if (skip >= taskCount) {
					bar.increment(cmpCount);
					finishedBlock.add(j);
				}
			}

			freeMemory(layered, finishedBlock);

			int taskId = 0;
			BitSet finishedTask = progressState = new BitSet(progressMax = taskCount-skip);
			updateProgressTask = Timer.getDefault().loop(this::updateProgress, 10000);

			for (; i >= 0; i--) {
				List<FileMeta> prev = i==0?Collections.emptyList():layered.get(i-1);
				List<FileMeta> self = layered.get(i);

				List<Promise<?>> tasks = new ArrayList<>();

				for (int j = 0; j < self.size(); j++) {
					if (terminateFlag) return;

					int fTaskId = taskId++;
					int count = prev.size() + self.size() - j - 1;
					if (skip > 0) {
						bar.increment(count);
						skip --;
						finishedTask.add(fTaskId);
						continue;
					}

					FileMeta left = self.get(j);
					int finalJ = j;

					tasks.add(Promise.runAsync(comparator, () -> {
						BsDiff diff = new BsDiff(); diff.setLeft(left.data);
						int group = left.group;

						List<DiffInfo> founds = new ArrayList<>();

						for (int k = 0; k < prev.size(); k++) {
							FileMeta right = prev.get(k);
							if (terminateFlag) return;
							if (group >= 0 && right.group == group) continue;

							var dataB = right.data;
							int maxHeadDiff = (int) (dataB.length * DIFF_MAX);

							int byteDiff = diff.calculateDiffLength(dataB, slideWindow, dataB.length-slideWindow, maxHeadDiff);
							if (byteDiff >= 0) {
								DiffInfo diff1 = new DiffInfo();
								diff1.left = left.path;
								diff1.right = right.path;
								diff1.diff = byteDiff;
								founds.add(diff1);
							}
						}

						for (int k = finalJ+1; k < self.size(); k++) {
							FileMeta right = self.get(k);
							if (terminateFlag) return;
							if (group >= 0 && right.group == group) continue;

							var dataB = right.data;
							int maxHeadDiff = (int) (dataB.length * DIFF_MAX);

							int byteDiff = diff.calculateDiffLength(dataB, slideWindow, dataB.length-slideWindow, maxHeadDiff);
							if (byteDiff >= 0) {
								DiffInfo diff1 = new DiffInfo();
								diff1.left = left.path;
								diff1.right = right.path;
								diff1.diff = byteDiff;
								founds.add(diff1);
							}
						}

						if (founds.size() > 0) {
							int p = lastProgress;
							for (int k = 0; k < founds.size(); k++)
								founds.get(k).pos = p;

							synchronized (result) {
								for (int k = 0; k < founds.size(); k++)
									writer.write(result, founds.get(k));
							}
						}

						synchronized (finishedTask) { finishedTask.add(fTaskId); }

						bar.increment(count);
					}));
				}

				int finalI = i;
				Runnable cb = () -> {
					synchronized (finishedBlock) {
						finishedBlock.add(finalI);
						freeMemory(layered, finishedBlock);
					}
				};

				if (!tasks.isEmpty()) Promise.all(cleanup, tasks).thenRun(cb);
			}

			comparator.awaitTermination();
			finish_callback.run();
		}

		private long mem;
		private void updateMemoryUsage() {
			DiffFinder.this.uiMemoryState.setText("内存占用: "+TextUtil.scaledNumber(mem));
		}

		private void freeMemory(List<List<FileMeta>> layered, BitSet finishedBlock) {
			long mem = 0;
			for (int j = 0; j < layered.size(); j++) {
				if (finishedBlock.allTrue(j, j + 2)) {
					List<FileMeta> value = layered.get(j);
					if (value != null) {
						for (FileMeta file : value) {
							mem += file.data.length;
							file.data = null;
						}
						layered.set(j, Collections.emptyList());
					}
				}
			}
			this.mem -= mem;
			updateMemoryUsage();
		}

		void stop() {
			if (!terminateFlag) updateProgress();
			terminateFlag = true;
			IOUtil.closeSilently(progressFile);
			generator.shutdown();
			cleanup.shutdown();
			comparator.shutdownNow();
			IOUtil.closeSilently(result);
			if (updateProgressTask != null) updateProgressTask.cancel();
		}
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		var label1 = new JLabel();
		uiInput = new JTextField();
		var label3 = new JLabel();
		uiNameFilter = new JTextField();
		var label4 = new JLabel();
		uiOutput = new JTextField();
		var label5 = new JLabel();
		uiBucketSize = new JTextField();
		uiPreWindow = new JSpinner();
		uiSlideWindow = new JSpinner();
		uiTogle = new JButton();
		uiGroupIsRegex = new JCheckBox();
		uiGroupInput = new JTextField();
		var label2 = new JLabel();
		var scrollPane2 = new JScrollPane();
		uiGroups = new JList<>();
		uiGroupAdd = new JButton();
		uiGroupDel = new JButton();
		uiMemoryState = new JLabel();
		uiCompareState = new JLabel();
		uiNotTextCompare = new JCheckBox();

		//======== this ========
		setTitle("O(Log2(n) * n) \u6587\u4ef6\u6bd4\u8f83\u5de5\u5177");
		var contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- label1 ----
		label1.setText("\u8f93\u5165");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(5, 5), label1.getPreferredSize()));

		//---- uiInput ----
		uiInput.setFont(uiInput.getFont().deriveFont(uiInput.getFont().getSize() + 6f));
		contentPane.add(uiInput);
		uiInput.setBounds(15, 20, 280, 30);

		//---- label3 ----
		label3.setText("\u5168\u5c40\u6b63\u5219\u8fc7\u6ee4\u5668");
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(5, 55), label3.getPreferredSize()));

		//---- uiNameFilter ----
		uiNameFilter.setText("\\.[Tt][Xx][Tt]$");
		contentPane.add(uiNameFilter);
		uiNameFilter.setBounds(15, 75, 280, uiNameFilter.getPreferredSize().height);

		//---- label4 ----
		label4.setText("\u5dee\u5f02\u8f93\u51fa\uff08YML\uff09");
		contentPane.add(label4);
		label4.setBounds(new Rectangle(new Point(5, 100), label4.getPreferredSize()));
		contentPane.add(uiOutput);
		uiOutput.setBounds(15, 120, 280, uiOutput.getPreferredSize().height);

		//---- label5 ----
		label5.setText("\u6876\u5927\u5c0f   \u5feb\u901f\u7a97\u53e3   \u6ed1\u52a8\u7a97\u53e3");
		contentPane.add(label5);
		label5.setBounds(new Rectangle(new Point(5, 145), label5.getPreferredSize()));

		//---- uiBucketSize ----
		uiBucketSize.setText("512K");
		contentPane.add(uiBucketSize);
		uiBucketSize.setBounds(15, 165, 50, uiBucketSize.getPreferredSize().height);

		//---- uiPreWindow ----
		uiPreWindow.setModel(new SpinnerNumberModel(8192, 2048, 32768, 256));
		contentPane.add(uiPreWindow);
		uiPreWindow.setBounds(70, 165, 60, uiPreWindow.getPreferredSize().height);

		//---- uiSlideWindow ----
		uiSlideWindow.setModel(new SpinnerNumberModel(1536, 0, 8192, 128));
		contentPane.add(uiSlideWindow);
		uiSlideWindow.setBounds(135, 165, 60, uiSlideWindow.getPreferredSize().height);

		//---- uiTogle ----
		uiTogle.setText("\u5f00\u59cb");
		contentPane.add(uiTogle);
		uiTogle.setBounds(305, 20, uiTogle.getPreferredSize().width, 55);

		//---- uiGroupIsRegex ----
		uiGroupIsRegex.setText("\u8fd9\u662f\u6b63\u5219");
		contentPane.add(uiGroupIsRegex);
		uiGroupIsRegex.setBounds(new Rectangle(new Point(223, 188), uiGroupIsRegex.getPreferredSize()));
		contentPane.add(uiGroupInput);
		uiGroupInput.setBounds(15, 210, 280, uiGroupInput.getPreferredSize().height);

		//---- label2 ----
		label2.setText("\u5206\u7ec4\u6bd4\u8f83\uff08\u7ec4\u5185\u6210\u5458\u4e0d\u4f1a\u6bd4\u8f83\uff09");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(5, 190), label2.getPreferredSize()));

		//======== scrollPane2 ========
		{
			scrollPane2.setViewportView(uiGroups);
		}
		contentPane.add(scrollPane2);
		scrollPane2.setBounds(15, 235, 280, 290);

		//---- uiGroupAdd ----
		uiGroupAdd.setText("\u6dfb\u52a0");
		contentPane.add(uiGroupAdd);
		uiGroupAdd.setBounds(new Rectangle(new Point(300, 235), uiGroupAdd.getPreferredSize()));

		//---- uiGroupDel ----
		uiGroupDel.setText("\u79fb\u9664");
		uiGroupDel.setEnabled(false);
		contentPane.add(uiGroupDel);
		uiGroupDel.setBounds(new Rectangle(new Point(300, 260), uiGroupDel.getPreferredSize()));
		contentPane.add(uiMemoryState);
		uiMemoryState.setBounds(15, 530, 345, 15);
		contentPane.add(uiCompareState);
		uiCompareState.setBounds(15, 550, 345, 15);

		//---- uiNotTextCompare ----
		uiNotTextCompare.setText("\u6bd4\u8f83\u7684\u4e0d\u662f\u6587\u672c");
		contentPane.add(uiNotTextCompare);
		uiNotTextCompare.setBounds(new Rectangle(new Point(170, 53), uiNotTextCompare.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(375, 645));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextField uiInput;
	private JTextField uiNameFilter;
	private JTextField uiOutput;
	private JTextField uiBucketSize;
	private JSpinner uiPreWindow;
	private JSpinner uiSlideWindow;
	private JButton uiTogle;
	private JCheckBox uiGroupIsRegex;
	private JTextField uiGroupInput;
	private JList<Group> uiGroups;
	private JButton uiGroupAdd;
	private JButton uiGroupDel;
	private JLabel uiMemoryState;
	private JLabel uiCompareState;
	private JCheckBox uiNotTextCompare;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}