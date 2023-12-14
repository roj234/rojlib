/*
 * Created by JFormDesigner on Wed Sep 06 02:36:12 CST 2023
 */

package roj.text.novel;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.concurrent.OperationDone;
import roj.concurrent.TaskPool;
import roj.concurrent.timing.ScheduledTask;
import roj.concurrent.timing.Scheduler;
import roj.io.IOUtil;
import roj.text.*;
import roj.ui.DragReorderHelper;
import roj.ui.GUIUtil;
import roj.ui.OnChangeHelper;
import roj.ui.TextAreaPrintStream;
import roj.util.BsDiff;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 */
public class NovelFrame extends JFrame {
	public static void main(String[] args) {
		GUIUtil.systemLook();
		NovelFrame f = new NovelFrame();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	public NovelFrame() {
		chapters = new SimpleList<>();

		initComponents();
		cPresetRegexp.setModel(presetRegexps);
		changeHelper = new OnChangeHelper(this);
		changeHelper.addRoot(advancedMenu);
		changeHelper.addRoot(chapterParamWin);
		chapterDragHelper = new DragReorderHelper(cascadeChapterUI, draggedItem);

		cascadeChapterUI.addMouseListener(new MouseAdapter() {
			long prevClick;
			Chapter prevId;

			@Override
			public void mousePressed(MouseEvent e) {
				TreePath location = cascadeChapterUI.getPathForLocation(e.getX(), e.getY());
				if (location != null) {
					Chapter c = (Chapter) location.getLastPathComponent();

					if (prevId == c && System.currentTimeMillis() - prevClick < 300) {
						btnRenameChapter(c);
						return;
					}

					prevId = c;
					prevClick = System.currentTimeMillis();
					errout.setText(c.data != null ? c.data.toString() : novel_in.toString(c.start, c.end));
				}
			}
		});
		changeHelper.addEventListener(presetRegexpInp, this::onPresetRegexpChange);
		changeHelper.addEventListener(alignRegexp, this::alignRegexpKeyTyped);

		GUIUtil.dropFilePath(novelPath, (e) -> { read_novel(null); }, false);

		PresetRegexp regexp = new PresetRegexp();
		regexp.name = "";
		regexp.chapterId = 1;
		regexp.chapterName = 2;
		regexp.from = "〔(.+?)〕(.+)$";
		regexp.to = "第$1章 $2";
		presetRegexps.addElement(regexp);

		onPresetRegexpChange(presetRegexpInp);
		alignRegexpKeyTyped(alignRegexp);
		btnFindChapter.setEnabled(false);

		chapterManager = (DefaultTreeModel) cascadeChapterUI.getModel();
	}

	private final OnChangeHelper changeHelper;
	private final DragReorderHelper chapterDragHelper;
	private final DefaultTreeModel chapterManager;

	private static class PresetRegexp {
		String name;
		int chapterId, chapterName;
		String from, to;

		public String toString() { return name; }
	}
	private DefaultComboBoxModel<PresetRegexp> presetRegexps = new DefaultComboBoxModel<>();
	private boolean isPresetRegexp;

	private Pattern novel_regexp;
	private int chapterId_group, chapterName_group;

	CharList novel_in = new CharList(), novel_out = new CharList();

	List<Chapter> chapters;

	static final MyBitSet myWhiteSpace = MyBitSet.from("\r\n\t 　 \uFEFF\u200B");

	private Chapter firstActiveChapter(boolean nonnullWhenMultiply) {
		TreePath[] paths = cascadeChapterUI.getSelectionPaths();
		return paths != null && paths.length == 1 ? ((Chapter) paths[0].getLastPathComponent()) : null;
	}

	private Chapter[] allActiveChapter() {
		TreePath[] paths = cascadeChapterUI.getSelectionPaths();
		if (paths != null) {
			Chapter[] out = new Chapter[paths.length];
			for (int i = 0; i < paths.length; i++) {
				out[i] = (Chapter) paths[i].getLastPathComponent();
			}
			return out;
		}
		return null;
	}

	private void on_preset_regexp_clicked(ActionEvent e) {
		PresetRegexp item = (PresetRegexp) cPresetRegexp.getSelectedItem();
		if (item == null) return;

		if (!isPresetRegexp && !item.name.isEmpty()) {
			PresetRegexp prev = presetRegexps.getElementAt(0);
			prev.from = novel_regexp.pattern();
			prev.to = alignReplaceTo.getText();
			prev.chapterId = chapterId_group;
			prev.chapterName = chapterName_group;
		}

		isPresetRegexp = !item.name.isEmpty();
		alignRegexp.setText(item.from);
		alignRegexpKeyTyped(alignRegexp);
		alignReplaceTo.setText(item.to);
		chapIdGroupInp.setValue(chapterId_group = item.chapterId);
		chapNameGroupInp.setValue(chapterName_group = item.chapterName);
	}

	private void read_novel(ActionEvent e) {
		try (TextReader sr = TextReader.auto(new File(novelPath.getText()))) {
			novel_in.clear();
			novel_in.readFully(sr).replace("\r\n", "\n").replace('\r', '\n');
			if (removeHalfEmpty.isSelected()) novel_in.replace("\n\n", "\n");

			errout.setText("charset:"+sr.charset()+"\n" +
				"chars:"+ novel_in.length()+"\n"+novel_in);
		} catch (IOException ex) {
			errout.setText("");
			ex.printStackTrace(new TextAreaPrintStream(errout,99999));
		}

		btnWrite.setEnabled(false);
		btnAlign.setEnabled(false);
		btnFindChapter.setEnabled(novel_regexp != null);
	}

	private void write_novel(ActionEvent e) {
		File file = new File(novelPath.getText());
		long time = file.lastModified();
		try (TextWriter out = TextWriter.to(file, Charset.forName("GB18030"))) {
			out.append(novel_out);
		} catch (IOException ex) {
			errout.setText("");
			ex.printStackTrace(new TextAreaPrintStream(errout,99999));
			return;
		}

		if (keepModTime.isSelected())
			file.setLastModified(time);
	}

	private void align_novel(ActionEvent e) {
		novel_out.clear();

		chapters.clear();
		((Chapter) chapterManager.getRoot()).flat(chapters);

		if (reorderChapter.isSelected()) {
			chapters.sort((o1, o2) -> Long.compare(o1.no, o2.no));
		}

		SimpleList<String> replacedName = null;
		ToIntMap<String> myChapterNo = new ToIntMap<>();
		if (renameChapter.isSelected()) {
			replacedName = new SimpleList<>();
			for (int i = 1; i < chapters.size(); i++) {
				Chapter c = chapters.get(i);
				if (c.flagOverride) {
					replacedName.add(c.displayName);
					continue;
				}

				Matcher m = novel_regexp.matcher(c.fullName);
				boolean ok = m.matches();
				assert ok;

				CharList tmp = new CharList(alignReplaceTo.getText());
				computeChapterName(m, myChapterNo, c, tmp);
				replacedName.add(tmp.toStringAndFree());
			}
		}

		for (int i = 0; i < chapters.size(); i++) {
			Chapter c = chapters.get(i);
			if (i > 0) novel_out.append("\n\n").append(replacedName != null ? replacedName.get(i-1) : c.fullName == null ? c.displayName : c.fullName).append('\n');

			int st, len;
			char[] val;
			if (c.data != null) {
				st = 0;
				len = c.data.length();
				val = c.data.list;
			} else {
				st = c.start;
				len = c.end;
				val = novel_in.list;
			}
			boolean haveTrimmedFirstLine = false;
			while ((st < len) && myWhiteSpace.contains(val[st])) {
				st++;
				haveTrimmedFirstLine = true;
			}
			while ((st < len) && myWhiteSpace.contains(val[len - 1])) {
				len--;
			}

			for (String line : new LineReader(new CharList.Slice(val, st, len), false)) {
				if (!haveTrimmedFirstLine && (line.isEmpty() || prefixSpaceOnly.isSelected() && !Character.isWhitespace(line.charAt(0)))) {
					novel_out.append(line).append('\n');
					continue;
				}
				haveTrimmedFirstLine = false;

				st = 0;
				len = line.length();
				while ((st < len) && myWhiteSpace.contains(line.charAt(st))) {
					st++;
				}
				while ((st < len) && myWhiteSpace.contains(line.charAt(len - 1))) {
					len--;
				}

				if (st == len) novel_out.append('\n');
				else novel_out.append("　　").append(line, st, len).append('\n');
			}
		}
		errout.setText("chars:"+ novel_out.length()+"\n"+novel_out);
		btnWrite.setEnabled(true);
		novel_out.replace("\n", "\r\n");
	}

	private void computeChapterName(Matcher m, ToIntMap<String> myChapterNo, Chapter c, CharList tmp) {
		for (int j = 0; j <= m.groupCount(); j++) {
			if (j == chapterId_group) {
			} else if (j == chapterName_group) {
			} else if (j != 0) {
				String str = m.group(j);
				c.type ^= str.hashCode();
			}
		}

		for (int j = 0; j <= m.groupCount(); j++) {
			String str = m.group(j);
			if (j == chapterId_group) {
				if (checkBox1.isSelected()) {
					str = Integer.toString(myChapterNo.increase(String.valueOf(c.type), 1));
					System.out.println("type="+c.type);
				}

				switch (chapterNameType.getSelectedIndex()) {
					case 0: break;
					case 1:
						char[] cb = str.toCharArray();
						str = Long.toString(Chapter.parseChapterNo(cb, 0, cb.length));
						break;
					case 2:
						cb = str.toCharArray();
						str = ChinaNumeric.toString(Chapter.parseChapterNo(cb, 0, cb.length));
						break;
				}
			} else if (j == chapterName_group) {
				str = mytrim(str);
			}
			tmp.replace("$"+j, str);
		}
	}

	private void test_chapter(ActionEvent e) {
		Chapter root = new Chapter();
		if (cascadeChapter.isSelected()) {
			List<List<Chapter>> chapters = null;
			try {
				chapters = Chapter.parse(new LineReader(novel_in, false));
			} catch (IOException ignored) {}

			root.children = Chapter.groupChapter(chapters, 0);
			root.name = "Auto " + ACalendar.toLocalTimeString(System.currentTimeMillis());

			// fix: missing chapters
			// fix: obfuscator bug
			// add: InsnList2
			// add: TLS1.3
			this.chapters.clear();
			root.flat(this.chapters);
			int end = this.chapters.get(this.chapters.size() - 1).end;
			if (end != novel_in.length()) {
				Chapter fin = new Chapter();
				fin.name = "Appendix";
				fin.start = end;
				fin.end = novel_in.length();
				chapterManager.insertNodeInto(fin, root, root.children.size());
			}
		} else {
			Matcher m = novel_regexp.matcher(novel_in);

			List<Chapter> chapters = root.children = new SimpleList<>();

			Chapter c = root;
			c.no = -1;
			c.name = "Regexp " + ACalendar.toLocalTimeString(System.currentTimeMillis());

			int i = 0;
			while (m.find(i)) {
				c.end = m.start();

				c = new Chapter();
				c.name = m.group(chapterName_group);

				c.name = mytrim(c.name);

				try {
					c.no = Chapter.parseChapterNo(novel_in.list, m.start(chapterId_group), m.end(chapterId_group));
				} catch (NumberFormatException e1) {
					c.no = -1;
				}
				c.start = m.end();

				i = m.end();

				CharList tmp = new CharList(alignReplaceTo.getText());
				for (int j = 0; j <= m.groupCount(); j++) {
					tmp.replace("$"+j, j == chapterName_group ? c.name : m.group(j));
				}
				c.fullName = m.group();
				c.displayName = tmp.toStringAndFree();

				chapters.add(c);
			}
			c.end = novel_in.length();
		}

		root.setParents();
		chapterManager.setRoot(root);
		errout.setText("mode: "+root.name+"\nchapter count: "+root.sumChildCount());

		cascadeChapterUI.setRootVisible(true);
		btnAlign.setEnabled(true);
		checkChapterDup.setEnabled(true);
		delChapterName.setEnabled(true);
		delChapterText.setEnabled(true);
		replaceChapter.setEnabled(true);
		nextDisorder.setEnabled(true);
	}

	private String mytrim(String c) {
		int st = 0;
		int len = c.length();
		while ((st < len) && myWhiteSpace.contains(c.charAt(st))) {
			st++;
		}
		while ((st < len) && myWhiteSpace.contains(c.charAt(len - 1))) {
			len--;
		}
		return c.substring(st,len);
	}

	private void select_novel(ActionEvent e) {
		JFileChooser fileChooser = new JFileChooser(novelPath.getText());
		fileChooser.setDialogTitle("选择");
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int status = fileChooser.showOpenDialog(this);
		//没有选打开按钮结果提示
		if (status == JFileChooser.APPROVE_OPTION) {
			novelPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
		}
	}

	private CharList data(Chapter c) {
		if (c.data == null) c.data = new CharList(novel_in.subSequence(c.start, c.end));
		return c.data;
	}

	private void delChapterName(ActionEvent e) {
		Chapter[] indices = allActiveChapter();
		if (indices == null) return;

		boolean confirm = false;
		Chapter prev = null;
		for (int i = indices.length-1; i >= 0; i--) {
			Chapter c = indices[i];
			if (c.getChildCount() > 0) {
				if (!confirm) {
					if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(this, "合并上级会一并合并下级的所有章节\n"+c, "警告", JOptionPane.YES_NO_OPTION)) {
						return;
					}
					confirm = true;
				}
			}

			prev = c.getParent();
			int off = prev.getIndex(c);
			if (off > 0) prev = prev.getChildAt(off-1);

			chapterManager.removeNodeFromParent(c);
			CharList str = data(prev);

			if (c.getChildCount() > 0) {
				List<Chapter> flat = new SimpleList<>();
				c.flat(flat);
				for (Chapter c1 : flat)
					str.append(c1.fullName).append(data(c1));
			} else {
				str.append(c.fullName).append(data(c));
			}
		}

		TreePath path = makePath(prev);
		cascadeChapterUI.setSelectionPath(path);
		cascadeChapterUI.scrollPathToVisible(path);
	}

	private void delChapterText(ActionEvent e) {
		Chapter[] indices = allActiveChapter();
		if (indices == null) return;

		boolean confirm = false;
		Chapter prev = null;
		for (Chapter c : indices) {
			if (c.getChildCount() > 0) {
				if (!confirm) {
					if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(this, "删除上级会一并删除下级的所有章节\n"+c, "警告", JOptionPane.YES_NO_OPTION)) {
						return;
					}
					confirm = true;
				}
			}

			prev = c.getParent();
			int off = prev.getIndex(c);
			if (off+1 < prev.getChildCount()) prev = prev.getChildAt(off+1);

			chapterManager.removeNodeFromParent(c);
		}

		TreePath path = makePath(prev);
		cascadeChapterUI.setSelectionPath(path);
		cascadeChapterUI.scrollPathToVisible(path);
	}

	private void replaceChapter(ActionEvent e) {
		Chapter c = firstActiveChapter(false);
		if (c == null) {
			JOptionPane.showMessageDialog(this, "选中的章节数量不为1");
			return;
		}

		data(c).clear();
		c.data.append(errout.getText());
	}

	private void nextDisorderChapter(ActionEvent e) {
		Chapter[] indices = allActiveChapter();

		chapters.clear();
		((Chapter) chapterManager.getRoot()).flat(chapters);

		int i;
		if (indices == null || indices.length != 1) {
			i = 0;
		} else {
			i = chapters.indexOf(indices[0]);
		}

		if (i <= 0) i = 1;
		for (; i < chapters.size(); i++) {
			Chapter prev = chapters.get(i-1);
			Chapter curr = chapters.get(i);

			int delta = (int) (curr.no - prev.no);
			if (delta != 1) {
				if (curr.getParent() == prev) continue;

				TreePath path = makePath(curr);
				cascadeChapterUI.setSelectionPath(path);
				cascadeChapterUI.scrollPathToVisible(path);
				return;
			}
		}

		JOptionPane.showMessageDialog(advancedMenu, "没有了", "提示", JOptionPane.INFORMATION_MESSAGE);
	}

	private TreePath makePath(Chapter c) {
		int len = 1;
		Chapter c1 = c;
		while (c1.getParent() != null) {
			c1 = c1.getParent();
			len++;
		}
		Object[] paths = new Object[len];
		while (len > 0) {
			paths[--len] = c;
			c = c.getParent();
		}
		return new TreePath(paths);
	}

	private void checkChapterDup(ActionEvent e) {
		new Thread(() -> {
			LongAdder finished = new LongAdder();

			chapters.clear();
			((Chapter) chapterManager.getRoot()).flat(chapters);
			long total = (chapters.size() - 1) * chapters.size() / 2;

			TaskPool pool = TaskPool.Common();
			SimpleList<IntMap.Entry<String>> list = new SimpleList<>();

			AtomicReference<ScheduledTask> task = new AtomicReference<>();
			task.set(Scheduler.getDefaultScheduler().loop(() -> {
				progress.setValue((int) ((double)finished.sum() / total * 10000));
				progressStr.setText(finished + "/" + total);
				if (finished.sum() == total) {
					task.get().cancel();
					list.sort((o1, o2) -> Integer.compare(o2.getIntKey(), o1.getIntKey()));
					errout.setText("相似度(仅记录超过50%) - 章节名称\n"+list.toString());
					checkChapterDup.setEnabled(true);
					progressStr.setText("finished");
				}
			}, 20));

			for (int i = 0; i < chapters.size(); i++) {
				Chapter ca = chapters.get(i);
				byte[] ba = IOUtil.SharedCoder.get().encode(ca.data != null ? ca.data : novel_in.subSequence(ca.start, ca.end));

				BsDiff diff = new BsDiff();
				diff.setLeft(ba);

				for (int j = i+1; j < chapters.size(); j++) {
					Chapter cb = chapters.get(j);

					pool.pushTask(() -> {
						byte[] bb = IOUtil.SharedCoder.get().encode(cb.data != null ? cb.data : novel_in.subSequence(cb.start, cb.end));
						int siz = Math.min(ba.length, bb.length);
						int dd = new BsDiff(diff).getDiffLength(bb, siz / 2);
						if (dd >= 0) {
							synchronized (list) {
								list.add(new IntMap.Entry<>((int) ((double)(siz-dd) / siz * 10000), ca.fullName + "|" + cb.fullName));
							}
						}
						finished.add(1);
					});
				}
			}
			System.out.println("thread exit");
		}).start();

		checkChapterDup.setEnabled(false);
	}

	private void alignRegexpKeyTyped(JTextComponent e) {
		btnAlign.setEnabled(false);
		novel_regexp = null;
		try {
			if (e.getText().isEmpty()) throw OperationDone.INSTANCE;

			novel_regexp = Pattern.compile(e.getText(), Pattern.MULTILINE);
		} catch (Exception e1) {
			alignRegexp.setForeground(new Color(0xB60000));
			btnFindChapter.setEnabled(false);
			return;
		}
		alignRegexp.setForeground(new Color(0x008100));
		btnFindChapter.setEnabled(true);
	}

	private void onPresetRegexpChange(JTextComponent c) {
		LineReader lr = new LineReader(c.getText());

		PresetRegexp none = presetRegexps.getElementAt(0);
		presetRegexps.removeAllElements();
		presetRegexps.addElement(none);

		int i = 1;
		try {
			while (lr.hasNext()) {
				List<String> id = TextUtil.split(lr.next(), '|');
				String from = lr.next();
				String to = lr.next();
				PresetRegexp regexp = new PresetRegexp();
				Pattern.compile(from);
				regexp.from = from;
				regexp.to = to;
				regexp.name = id.get(0);
				regexp.chapterId = Integer.parseInt(id.get(1));
				regexp.chapterName = Integer.parseInt(id.get(2));
				presetRegexps.addElement(regexp);
				i++;
			}
		} catch (Exception e) {
			errout.setText("");
			e.printStackTrace(new TextAreaPrintStream(errout,99999));

			JOptionPane.showMessageDialog(advancedMenu, "第"+i+"个正则表达式解析失败", "错误", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void renameChapterStateChanged(ChangeEvent e) {
		chapterNameType.setEnabled(renameChapter.isSelected());
	}

	private void btnRenameChapter(Chapter c) {
		assert firstActiveChapter(true) == c;

		setEnabled(false);
		cpwChapName.setText(c.name);
		cpwChapNo.setValue((int)c.no);
		cpwOrigName.setText(c.fullName);
		cpwOutName.setText(c.displayName);
		chapterParamWin.show();
	}

	private void open_advanced_menu(ActionEvent e) {
		advancedMenu.show();
		setEnabled(false);
	}

	private void advancedMenuWindowClosing(WindowEvent e) {
		setEnabled(true);
		requestFocus();

		if (e.getWindow() == chapterParamWin) {
			Chapter c = firstActiveChapter(true);
			c.name = cpwChapName.getText();
			c.no = (int)cpwChapNo.getValue();
			c.displayName = cpwOutName.getText();
			chapterManager.nodeChanged(c);
			c.flagOverride = true;
		}
	}

	private void chapIdGroupInpStateChanged(ChangeEvent e) {
		chapterId_group = (int) chapIdGroupInp.getValue();
	}

	private void chapNameGroupInpStateChanged(ChangeEvent e) {
		chapterName_group = (int) chapNameGroupInp.getValue();
	}

	private void btnInsertMode(ActionEvent e) {
		chapterDragHelper.insertMode = btnInsertMode.isSelected();
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		webStart = new JButton();
		webSource = new JTextField();
		JLabel label1 = new JLabel();
		JSeparator separator1 = new JSeparator();
		JLabel label2 = new JLabel();
		btnRead = new JButton();
		novelPath = new JTextField();
		btnSelectNovel = new JButton();
		JLabel label3 = new JLabel();
		JScrollPane scrollPane1 = new JScrollPane();
		errout = new JEditorPane();
		btnAlign = new JButton();
		btnFindChapter = new JButton();
		alignRegexp = new JTextField();
		progress = new JProgressBar();
		progressStr = new JLabel();
		cPresetRegexp = new JComboBox<>();
		JButton openAdvanceMenu = new JButton();
		JSeparator separator2 = new JSeparator();
		JLabel label7 = new JLabel();
		webMerge = new JButton();
		webCommit = new JButton();
		btnWrite = new JButton();
		draggedItem = new JLabel();
		alignReplaceTo = new JTextField();
		prefixSpaceOnly = new JCheckBox();
		removeHalfEmpty = new JCheckBox();
		reorderChapter = new JCheckBox();
		renameChapter = new JCheckBox();
		chapterNameType = new JComboBox<>();
		checkChapterDup = new JButton();
		delChapterText = new JButton();
		delChapterName = new JButton();
		keepModTime = new JCheckBox();
		replaceChapter = new JButton();
		nextDisorder = new JButton();
		cascadeChapter = new JCheckBox();
		btnTextReplaceRegex = new JButton();
		btnTextReplaceBatch = new JButton();
		separator3 = new JSeparator();
		scrollPane4 = new JScrollPane();
		cascadeChapterUI = new JTree();
		btnInsertMode = new JCheckBox();
		button5 = new JButton();
		checkBox1 = new JCheckBox();
		advancedMenu = new JDialog();
		JLabel label5 = new JLabel();
		JLabel label6 = new JLabel();
		chapIdGroupInp = new JSpinner();
		chapNameGroupInp = new JSpinner();
		scrollPane3 = new JScrollPane();
		presetRegexpInp = new JTextArea();
		JLabel label8 = new JLabel();
		chapterParamWin = new JDialog();
		cpwOutName = new JTextField();
		cpwChapName = new JTextField();
		cpwChapNo = new JSpinner();
		cpwOrigName = new JTextField();
		JLabel label4 = new JLabel();
		JLabel label9 = new JLabel();
		JLabel label10 = new JLabel();
		label11 = new JLabel();

		//======== this ========
		setTitle("\u5c0f\u8bf4\u7ba1\u7406\u7cfb\u7edf");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//---- webStart ----
		webStart.setText("\u542f\u52a8\u540e\u7aef");
		webStart.setMargin(new Insets(0, 2, 0, 2));
		contentPane.add(webStart);
		webStart.setBounds(new Rectangle(new Point(10, 28), webStart.getPreferredSize()));

		//---- webSource ----
		webSource.setText("HR");
		contentPane.add(webSource);
		webSource.setBounds(108, 28, 40, 18);

		//---- label1 ----
		label1.setText("\u6570\u636e\u6e90");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(70, 30), label1.getPreferredSize()));
		contentPane.add(separator1);
		separator1.setBounds(5, 50, 420, separator1.getPreferredSize().height);

		//---- label2 ----
		label2.setText("\u7f51\u9875\u670d\u52a1");
		label2.setFont(label2.getFont().deriveFont(label2.getFont().getSize() - 2f));
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(385, 21), label2.getPreferredSize()));

		//---- btnRead ----
		btnRead.setText("\u8bfb\u53d6");
		btnRead.setMargin(new Insets(2, 4, 2, 4));
		btnRead.addActionListener(e -> read_novel(e));
		contentPane.add(btnRead);
		btnRead.setBounds(15, 55, btnRead.getPreferredSize().width, 20);
		contentPane.add(novelPath);
		novelPath.setBounds(55, 55, 167, novelPath.getPreferredSize().height);

		//---- btnSelectNovel ----
		btnSelectNovel.setText("\u2026");
		btnSelectNovel.setMargin(new Insets(2, 2, 2, 2));
		btnSelectNovel.addActionListener(e -> select_novel(e));
		contentPane.add(btnSelectNovel);
		btnSelectNovel.setBounds(220, 54, btnSelectNovel.getPreferredSize().width, 23);

		//---- label3 ----
		label3.setText("\u6821\u5bf9\u6574\u7406");
		label3.setFont(label3.getFont().deriveFont(label3.getFont().getSize() - 2f));
		contentPane.add(label3);
		label3.setBounds(new Rectangle(new Point(385, 51), label3.getPreferredSize()));

		//======== scrollPane1 ========
		{
			scrollPane1.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scrollPane1.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

			//---- errout ----
			errout.setText("\u5c0f\u63d0\u793a\n\u5728\u7ae0\u8282\u754c\u9762\u8bef\u62d6\u52a8\u53ef\u4ee5\u6309\u53f3\u952e\u53d6\u6d88\n\u9884\u5b9a\u4e49\u6b63\u5219\u53ef\u4ee5\u5728\u9ad8\u7ea7\u83dc\u5355\u4e2d\u4fee\u6539\n\u53cc\u51fb\u7ae0\u8282\u7f16\u8f91\u540d\u79f0\u548c\u5e8f\u53f7");
			scrollPane1.setViewportView(errout);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(435, 5, 505, 630);

		//---- btnAlign ----
		btnAlign.setText("\u6392\u7248");
		btnAlign.setEnabled(false);
		btnAlign.setMargin(new Insets(2, 4, 2, 4));
		btnAlign.addActionListener(e -> align_novel(e));
		contentPane.add(btnAlign);
		btnAlign.setBounds(15, 95, btnAlign.getPreferredSize().width, 20);

		//---- btnFindChapter ----
		btnFindChapter.setText("\u5206\u7ae0");
		btnFindChapter.setEnabled(false);
		btnFindChapter.setMargin(new Insets(2, 4, 2, 4));
		btnFindChapter.addActionListener(e -> test_chapter(e));
		contentPane.add(btnFindChapter);
		btnFindChapter.setBounds(15, 75, btnFindChapter.getPreferredSize().width, 20);
		contentPane.add(alignRegexp);
		alignRegexp.setBounds(55, 85, 320, alignRegexp.getPreferredSize().height);

		//---- progress ----
		progress.setValue(2000);
		progress.setMaximum(10000);
		contentPane.add(progress);
		progress.setBounds(5, 4, 320, progress.getPreferredSize().height);

		//---- progressStr ----
		progressStr.setText("ready");
		progressStr.setHorizontalAlignment(SwingConstants.CENTER);
		contentPane.add(progressStr);
		progressStr.setBounds(325, 4, 105, progressStr.getPreferredSize().height);

		//---- cPresetRegexp ----
		cPresetRegexp.addActionListener(e -> on_preset_regexp_clicked(e));
		contentPane.add(cPresetRegexp);
		cPresetRegexp.setBounds(290, 60, 85, cPresetRegexp.getPreferredSize().height);

		//---- openAdvanceMenu ----
		openAdvanceMenu.setText("\u9ad8\u7ea7");
		openAdvanceMenu.setMargin(new Insets(1, 6, 1, 6));
		openAdvanceMenu.addActionListener(e -> open_advanced_menu(e));
		contentPane.add(openAdvanceMenu);
		openAdvanceMenu.setBounds(new Rectangle(new Point(245, 60), openAdvanceMenu.getPreferredSize()));
		contentPane.add(separator2);
		separator2.setBounds(5, 490, 420, separator2.getPreferredSize().height);

		//---- label7 ----
		label7.setText("\u7ae0\u8282\u7ba1\u7406");
		label7.setFont(label7.getFont().deriveFont(label7.getFont().getSize() - 2f));
		contentPane.add(label7);
		label7.setBounds(new Rectangle(new Point(385, 491), label7.getPreferredSize()));

		//---- webMerge ----
		webMerge.setText("merge");
		webMerge.setMargin(new Insets(0, 2, 0, 2));
		contentPane.add(webMerge);
		webMerge.setBounds(new Rectangle(new Point(340, 30), webMerge.getPreferredSize()));

		//---- webCommit ----
		webCommit.setText("commit");
		webCommit.setMargin(new Insets(0, 2, 0, 2));
		contentPane.add(webCommit);
		webCommit.setBounds(new Rectangle(new Point(380, 30), webCommit.getPreferredSize()));

		//---- btnWrite ----
		btnWrite.setText("\u5199\u5165");
		btnWrite.setEnabled(false);
		btnWrite.setMargin(new Insets(2, 4, 2, 4));
		btnWrite.addActionListener(e -> write_novel(e));
		contentPane.add(btnWrite);
		btnWrite.setBounds(15, 115, btnWrite.getPreferredSize().width, 20);
		contentPane.add(draggedItem);
		draggedItem.setBounds(new Rectangle(new Point(0, 0), draggedItem.getPreferredSize()));
		contentPane.add(alignReplaceTo);
		alignReplaceTo.setBounds(55, 110, 320, alignReplaceTo.getPreferredSize().height);

		//---- prefixSpaceOnly ----
		prefixSpaceOnly.setText("\u4ec5\u6574\u7406\u7a7a\u767d\u5f00\u59cb\u7684\u884c");
		prefixSpaceOnly.setSelected(true);
		contentPane.add(prefixSpaceOnly);
		prefixSpaceOnly.setBounds(new Rectangle(new Point(15, 435), prefixSpaceOnly.getPreferredSize()));

		//---- removeHalfEmpty ----
		removeHalfEmpty.setText("\u53bb\u966450%\u7684\u7a7a\u884c");
		contentPane.add(removeHalfEmpty);
		removeHalfEmpty.setBounds(new Rectangle(new Point(150, 435), removeHalfEmpty.getPreferredSize()));

		//---- reorderChapter ----
		reorderChapter.setText("\u91cd\u65b0\u6392\u5e8f\u7ae0\u8282");
		contentPane.add(reorderChapter);
		reorderChapter.setBounds(new Rectangle(new Point(255, 435), reorderChapter.getPreferredSize()));

		//---- renameChapter ----
		renameChapter.setText("\u91cd\u65b0\u751f\u6210\u7ae0\u8282\u540d\u79f0");
		renameChapter.addChangeListener(e -> renameChapterStateChanged(e));
		contentPane.add(renameChapter);
		renameChapter.setBounds(new Rectangle(new Point(15, 460), renameChapter.getPreferredSize()));

		//---- chapterNameType ----
		chapterNameType.setModel(new DefaultComboBoxModel<>(new String[] {
			"\u4e0d\u5904\u7406\u6570\u5b57",
			"\u963f\u62c9\u4f2f\u6570\u5b57",
			"\u4e2d\u56fd\u6570\u5b57"
		}));
		chapterNameType.setEnabled(false);
		contentPane.add(chapterNameType);
		chapterNameType.setBounds(new Rectangle(new Point(140, 462), chapterNameType.getPreferredSize()));

		//---- checkChapterDup ----
		checkChapterDup.setText("\u67e5\u91cd");
		checkChapterDup.setEnabled(false);
		checkChapterDup.setToolTipText("\u67e5\u627e\u7591\u4f3c\u91cd\u590d\u7684\u7ae0\u8282");
		checkChapterDup.setMargin(new Insets(2, 4, 2, 4));
		checkChapterDup.addActionListener(e -> checkChapterDup(e));
		contentPane.add(checkChapterDup);
		checkChapterDup.setBounds(new Rectangle(new Point(320, 500), checkChapterDup.getPreferredSize()));

		//---- delChapterText ----
		delChapterText.setText("\u5220\u9664");
		delChapterText.setEnabled(false);
		delChapterText.setToolTipText("\u5220\u9664\u8be5\u7ae0\u8282\u53ca\u5176\u5185\u5bb9");
		delChapterText.setMargin(new Insets(2, 4, 2, 4));
		delChapterText.addActionListener(e -> delChapterText(e));
		contentPane.add(delChapterText);
		delChapterText.setBounds(new Rectangle(new Point(55, 500), delChapterText.getPreferredSize()));

		//---- delChapterName ----
		delChapterName.setText("\u4e0e\u4e0a\u7ae0\u5408\u5e76");
		delChapterName.setEnabled(false);
		delChapterName.setMargin(new Insets(2, 4, 2, 4));
		delChapterName.addActionListener(e -> delChapterName(e));
		contentPane.add(delChapterName);
		delChapterName.setBounds(new Rectangle(new Point(245, 500), delChapterName.getPreferredSize()));

		//---- keepModTime ----
		keepModTime.setText("\u4fdd\u7559\u4fee\u6539\u65f6\u95f4");
		contentPane.add(keepModTime);
		keepModTime.setBounds(new Rectangle(new Point(295, 460), keepModTime.getPreferredSize()));

		//---- replaceChapter ----
		replaceChapter.setText("\u66ff\u6362\u5185\u5bb9");
		replaceChapter.setEnabled(false);
		replaceChapter.setToolTipText("\u7528\u53f3\u4fa7\u8f93\u5165\u6846\u7684\u5185\u5bb9\u66ff\u6362\u9009\u4e2d\u7ae0\u8282\u7684\u5185\u5bb9");
		replaceChapter.setMargin(new Insets(2, 4, 2, 4));
		replaceChapter.addActionListener(e -> replaceChapter(e));
		contentPane.add(replaceChapter);
		replaceChapter.setBounds(new Rectangle(new Point(95, 500), replaceChapter.getPreferredSize()));

		//---- nextDisorder ----
		nextDisorder.setText("\u67e5\u627e\u7591\u4f3c\u8bef\u5224");
		nextDisorder.setEnabled(false);
		nextDisorder.setMargin(new Insets(2, 4, 2, 4));
		nextDisorder.addActionListener(e -> nextDisorderChapter(e));
		contentPane.add(nextDisorder);
		nextDisorder.setBounds(new Rectangle(new Point(158, 500), nextDisorder.getPreferredSize()));

		//---- cascadeChapter ----
		cascadeChapter.setText("\u542f\u53d1\u5f0f\u65ad\u7ae0(WIP)");
		contentPane.add(cascadeChapter);
		cascadeChapter.setBounds(new Rectangle(new Point(185, 130), cascadeChapter.getPreferredSize()));

		//---- btnTextReplaceRegex ----
		btnTextReplaceRegex.setText("\u6b63\u5219\u66ff\u6362");
		btnTextReplaceRegex.setMargin(new Insets(0, 0, 0, 0));
		contentPane.add(btnTextReplaceRegex);
		btnTextReplaceRegex.setBounds(55, 135, 60, 20);

		//---- btnTextReplaceBatch ----
		btnTextReplaceBatch.setText("\u6279\u91cf\u66ff\u6362");
		btnTextReplaceBatch.setMargin(new Insets(0, 0, 0, 0));
		contentPane.add(btnTextReplaceBatch);
		btnTextReplaceBatch.setBounds(120, 135, 60, 20);
		contentPane.add(separator3);
		separator3.setBounds(5, 20, 420, separator3.getPreferredSize().height);

		//======== scrollPane4 ========
		{

			//---- cascadeChapterUI ----
			cascadeChapterUI.setModel(new DefaultTreeModel(
				new DefaultMutableTreeNode("\u672a\u52a0\u8f7d") {
					{
					}
				}));
			cascadeChapterUI.setRootVisible(false);
			scrollPane4.setViewportView(cascadeChapterUI);
		}
		contentPane.add(scrollPane4);
		scrollPane4.setBounds(10, 160, 410, 270);

		//---- btnInsertMode ----
		btnInsertMode.setText("\u5feb\u901f\u63d2\u5165\u6a21\u5f0f");
		btnInsertMode.setToolTipText("\u6309\u4f4f\u8282\u70b9(A)\u5e76\u62d6\u52a8\u5230\u8282\u70b9(B)\u4e0a\u65f6\n\u5c06A\u8bbe\u7f6e\u4e3aB\u7684\u5b69\u5b50");
		btnInsertMode.addActionListener(e -> btnInsertMode(e));
		contentPane.add(btnInsertMode);
		btnInsertMode.setBounds(new Rectangle(new Point(10, 525), btnInsertMode.getPreferredSize()));

		//---- button5 ----
		button5.setText("\u65b0\u589e\u7ae0\u8282");
		button5.setEnabled(false);
		contentPane.add(button5);
		button5.setBounds(new Rectangle(new Point(110, 525), button5.getPreferredSize()));

		//---- checkBox1 ----
		checkBox1.setText("\u91cd\u65b0\u547d\u540d");
		contentPane.add(checkBox1);
		checkBox1.setBounds(new Rectangle(new Point(215, 580), checkBox1.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(945, 670));
		pack();
		setLocationRelativeTo(getOwner());

		//======== advancedMenu ========
		{
			advancedMenu.setTitle("\u9ad8\u7ea7\u6b63\u5219\u53c2\u6570");
			advancedMenu.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					advancedMenuWindowClosing(e);
				}
			});
			Container advancedMenuContentPane = advancedMenu.getContentPane();
			advancedMenuContentPane.setLayout(null);

			//---- label5 ----
			label5.setText("\u7ae0\u8282\u5e8f\u53f7group");
			advancedMenuContentPane.add(label5);
			label5.setBounds(new Rectangle(new Point(10, 20), label5.getPreferredSize()));

			//---- label6 ----
			label6.setText("\u7ae0\u8282\u540d\u79f0group");
			advancedMenuContentPane.add(label6);
			label6.setBounds(new Rectangle(new Point(10, 45), label6.getPreferredSize()));

			//---- chapIdGroupInp ----
			chapIdGroupInp.setModel(new SpinnerNumberModel(1, 0, null, 1));
			chapIdGroupInp.addChangeListener(e -> chapIdGroupInpStateChanged(e));
			advancedMenuContentPane.add(chapIdGroupInp);
			chapIdGroupInp.setBounds(90, 15, 45, chapIdGroupInp.getPreferredSize().height);

			//---- chapNameGroupInp ----
			chapNameGroupInp.setModel(new SpinnerNumberModel(2, 0, null, 1));
			chapNameGroupInp.addChangeListener(e -> chapNameGroupInpStateChanged(e));
			advancedMenuContentPane.add(chapNameGroupInp);
			chapNameGroupInp.setBounds(90, 40, 45, chapNameGroupInp.getPreferredSize().height);

			//======== scrollPane3 ========
			{

				//---- presetRegexpInp ----
				presetRegexpInp.setText("\u5e38\u7528|1|3\n(?:\u6b63\u6587\\s*)?\u7b2c(?:\\s+)?([\u2015\uff0d\\-\u2500\u2014\u58f9\u8d30\u53c1\u8086\u4f0d\u9646\u67d2\u634c\u7396\u4e00\u4e8c\u4e24\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u25cb\u3007\u96f6\u767e\u5343O0-9\uff10-\uff19 ]{1,12})(?:\\s+)?([\u7ae0\u5377])[ \u3000\\t]*(.*)$\n\u7b2c$1$2 $3\n\u7eaf\u4e2d\u6587|1|1\n(?<=[ \u3000\\t\\n])([0-9 \\x4e00-\\x9fa5\uff08\uff09\\(\\)\\[\\]]{1,15})[ \u3000\\t]*$\n$1");
				scrollPane3.setViewportView(presetRegexpInp);
			}
			advancedMenuContentPane.add(scrollPane3);
			scrollPane3.setBounds(10, 90, 380, 185);

			//---- label8 ----
			label8.setText("\u9884\u5b9a\u4e49\u6b63\u5219");
			advancedMenuContentPane.add(label8);
			label8.setBounds(new Rectangle(new Point(10, 70), label8.getPreferredSize()));

			{
				// compute preferred size
				Dimension preferredSize = new Dimension();
				for(int i = 0; i < advancedMenuContentPane.getComponentCount(); i++) {
					Rectangle bounds = advancedMenuContentPane.getComponent(i).getBounds();
					preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
					preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
				}
				Insets insets = advancedMenuContentPane.getInsets();
				preferredSize.width += insets.right;
				preferredSize.height += insets.bottom;
				advancedMenuContentPane.setMinimumSize(preferredSize);
				advancedMenuContentPane.setPreferredSize(preferredSize);
			}
			advancedMenu.pack();
			advancedMenu.setLocationRelativeTo(advancedMenu.getOwner());
		}

		//======== chapterParamWin ========
		{
			chapterParamWin.setTitle("\u7ae0\u8282\u53c2\u6570(\u76f4\u63a5\u4fdd\u5b58)");
			chapterParamWin.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					advancedMenuWindowClosing(e);
				}
			});
			Container chapterParamWinContentPane = chapterParamWin.getContentPane();
			chapterParamWinContentPane.setLayout(null);
			chapterParamWinContentPane.add(cpwOutName);
			cpwOutName.setBounds(5, 15, 185, cpwOutName.getPreferredSize().height);
			chapterParamWinContentPane.add(cpwChapName);
			cpwChapName.setBounds(60, 70, 130, cpwChapName.getPreferredSize().height);
			chapterParamWinContentPane.add(cpwChapNo);
			cpwChapNo.setBounds(60, 45, 130, cpwChapNo.getPreferredSize().height);

			//---- cpwOrigName ----
			cpwOrigName.setEditable(false);
			chapterParamWinContentPane.add(cpwOrigName);
			cpwOrigName.setBounds(5, 100, 185, cpwOrigName.getPreferredSize().height);

			//---- label4 ----
			label4.setText("\u8f93\u51fa\u540d\u79f0");
			chapterParamWinContentPane.add(label4);
			label4.setBounds(new Rectangle(new Point(0, 0), label4.getPreferredSize()));

			//---- label9 ----
			label9.setText("\u7ae0\u8282\u5e8f\u53f7");
			chapterParamWinContentPane.add(label9);
			label9.setBounds(new Rectangle(new Point(10, 45), label9.getPreferredSize()));

			//---- label10 ----
			label10.setText("\u7ae0\u8282\u540d\u79f0");
			chapterParamWinContentPane.add(label10);
			label10.setBounds(new Rectangle(new Point(10, 70), label10.getPreferredSize()));

			//---- label11 ----
			label11.setText("\u8f93\u5165\u540d\u79f0");
			chapterParamWinContentPane.add(label11);
			label11.setBounds(new Rectangle(new Point(0, 85), label11.getPreferredSize()));

			{
				// compute preferred size
				Dimension preferredSize = new Dimension();
				for(int i = 0; i < chapterParamWinContentPane.getComponentCount(); i++) {
					Rectangle bounds = chapterParamWinContentPane.getComponent(i).getBounds();
					preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
					preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
				}
				Insets insets = chapterParamWinContentPane.getInsets();
				preferredSize.width += insets.right;
				preferredSize.height += insets.bottom;
				chapterParamWinContentPane.setMinimumSize(preferredSize);
				chapterParamWinContentPane.setPreferredSize(preferredSize);
			}
			chapterParamWin.pack();
			chapterParamWin.setLocationRelativeTo(chapterParamWin.getOwner());
		}
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JButton webStart;
	private JTextField webSource;
	private JButton btnRead;
	private JTextField novelPath;
	private JButton btnSelectNovel;
	private JEditorPane errout;
	private JButton btnAlign;
	private JButton btnFindChapter;
	private JTextField alignRegexp;
	private JProgressBar progress;
	private JLabel progressStr;
	private JComboBox<PresetRegexp> cPresetRegexp;
	private JButton webMerge;
	private JButton webCommit;
	private JButton btnWrite;
	private JLabel draggedItem;
	private JTextField alignReplaceTo;
	private JCheckBox prefixSpaceOnly;
	private JCheckBox removeHalfEmpty;
	private JCheckBox reorderChapter;
	private JCheckBox renameChapter;
	private JComboBox<String> chapterNameType;
	private JButton checkChapterDup;
	private JButton delChapterText;
	private JButton delChapterName;
	private JCheckBox keepModTime;
	private JButton replaceChapter;
	private JButton nextDisorder;
	private JCheckBox cascadeChapter;
	private JButton btnTextReplaceRegex;
	private JButton btnTextReplaceBatch;
	private JSeparator separator3;
	private JScrollPane scrollPane4;
	private JTree cascadeChapterUI;
	private JCheckBox btnInsertMode;
	private JButton button5;
	private JCheckBox checkBox1;
	private JDialog advancedMenu;
	private JSpinner chapIdGroupInp;
	private JSpinner chapNameGroupInp;
	private JScrollPane scrollPane3;
	private JTextArea presetRegexpInp;
	private JDialog chapterParamWin;
	private JTextField cpwOutName;
	private JTextField cpwChapName;
	private JSpinner cpwChapNo;
	private JTextField cpwOrigName;
	private JLabel label11;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
