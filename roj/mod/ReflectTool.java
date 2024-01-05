package roj.mod;

import roj.asm.type.Desc;
import roj.asm.type.TypeHelper;
import roj.asmx.mapper.Mapper;
import roj.collect.*;
import roj.ui.GUIUtil;
import roj.util.Helpers;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

/**
 * Make reading srg mapping easier
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class ReflectTool extends JFrame implements KeyListener, WindowListener {
	private static final int MAX_DISPLAY = 200;

	static boolean simpleMode = true;
	static boolean opened1;

	JTextField searchText;
	JPanel result;
	JScrollPane scroll;

	Set<ClassWindow> opened = new MyHashSet<>();

	public ReflectTool(boolean exit, String flag) {
		super("反射工具");
		GUIUtil.setLogo(this, "FMD_logo.png");

		setDefaultCloseOperation(exit ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		addWindowListener(this);

		JPanel panel = new JPanel();

		JLabel classNameLabel = new JLabel(flag == null ? "类名:" : flag);
		classNameLabel.setBounds(10, 20, 50, 25);
		panel.add(classNameLabel);

		searchText = new JTextField(20);
		searchText.setBounds(100, 20, 165, 25);
		panel.add(searchText);

		JButton search = new JButton("搜索");
		search.setBounds(280, 20, 80, 25);
		search.addActionListener(this::search);
		panel.add(search);

		if (flag == null) {
			JCheckBox simple = new JCheckBox("简");
			simple.setToolTipText("使用简名搜索");
			simple.getModel().setSelected(simpleMode);
			simple.setBounds(320, 20, 50, 25);
			simple.addActionListener((e) -> simpleMode = simple.getModel().isSelected());
			panel.add(simple);
		}

		JScrollPane scrollPane = new JScrollPane();//创建滚动组件
		scrollPane.setBounds(0, 0, 500, 350);

		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);


		JPanel panel1 = new JPanel();
		panel1.setLayout(null);
		scrollPane.setViewportView(panel1);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		this.scroll = scrollPane;

		JLabel tip = new JLabel("加载中...");
		panel1.add(tip);
		tip.setBounds(120, 5, 500, 15);
		result = panel1;

		getContentPane().add(panel, BorderLayout.NORTH);

		getContentPane().add(scrollPane);

		pack();
		setVisible(true);
		setResizable(false);
		setSize(510, 500);
		validate();

		loadData(tip);
	}

	static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

	static final TrieTree<String> simple2full = new TrieTree<>();
	static final TrieTreeSet fullClass = new TrieTreeSet();
	static final Map<String, SimpleList<Desc>> methodIndex = new MyHashMap<>(), fieldIndex = new MyHashMap<>();

	public static void start(boolean exit) {
		if (!opened1) new ReflectTool(exit, null);
	}

	protected void loadData(JLabel label) {
		if (fullClass.isEmpty()) {
			Shared.loadMapper();
			Mapper mapper = Shared.mapperFwd;
			for (String s : mapper.getClassMap().keySet()) {
				fullClass.add(s);
				final String key = s.substring(s.lastIndexOf('/') + 1).toLowerCase();
				String s1 = simple2full.put(key, s);
				if (s1 != null) {
					simple2full.put(key + '_' + (System.nanoTime() % 9999), s1);
				}
			}

			for (Map.Entry<Desc, String> entry : mapper.getMethodMap().entrySet()) {
				Desc copy = entry.getKey().copy();
				methodIndex.computeIfAbsent(copy.owner, Helpers.cast(Helpers.fnArrayList())).add(copy);
				copy.owner = entry.getValue();
			}

			for (Map.Entry<Desc, String> entry : mapper.getFieldMap().entrySet()) {
				Desc copy = entry.getKey().copy();
				fieldIndex.computeIfAbsent(copy.owner, Helpers.cast(Helpers.fnArrayList())).add(copy);
				copy.owner = entry.getValue();
			}

			for (CharSequence c : fullClass) {
				String s = c.toString();
				if (!methodIndex.containsKey(s) && !fieldIndex.containsKey(s)) {
					fullClass.remove(s);
					final String key = s.substring(s.lastIndexOf('/') + 1).toLowerCase();
					simple2full.remove(key);
				}
			}

			final Comparator<Desc> cmp = (o1, o2) -> o1.name.compareToIgnoreCase(o2.name);
			for (SimpleList<Desc> list : methodIndex.values()) {
				list.sort(cmp);
				list.trimToSize();
			}

			for (SimpleList<Desc> list : fieldIndex.values()) {
				list.sort(cmp);
				list.trimToSize();
			}
		}

		done();

		label.setText("加载完毕");
		label.setForeground(Color.GREEN);

		result.repaint();
	}

	protected final void done() {
		searchText.addKeyListener(this);
	}

	protected void search(ActionEvent event) {
		String text = searchText.getText().trim().replace('.', '/');
		Collection<String> entries;
		if (text.startsWith("field_") || text.startsWith("func_")) {
			Mapper mapper = Shared.mapperFwd;
			entries = new ArrayList<>(2);
			for (Map.Entry<Desc, String> entry : (text.startsWith("field_") ? mapper.getFieldMap() : mapper.getMethodMap()).entrySet()) {
				if (entry.getValue().equals(text)) {
					entries.add(entry.getKey().owner);
					System.out.println("MCP名: " + entry.getKey().name);
					break;
				}
			}
		} else {
			if (simpleMode) {
				entries = simple2full.valueMatches(text.toLowerCase(), MAX_DISPLAY);
			} else {
				if (!text.startsWith("net/")) text = "net/minecraft/" + text;

				entries = fullClass.keyMatches(text, MAX_DISPLAY);
			}
		}

		result.removeAll();
		int y = 2;

		if (entries.isEmpty()) {
			JLabel labelNotify = new JLabel("没有结果!");
			labelNotify.setForeground(Color.RED);
			labelNotify.setBounds(220, y, 80, 15);
			y += 22;
			result.add(labelNotify);
		} else if (entries.size() >= MAX_DISPLAY) {
			JLabel labelNotify = new JLabel("结果超过" + MAX_DISPLAY + "个, 已省略超出的!");
			labelNotify.setForeground(Color.RED);
			labelNotify.setBounds(200, y, 180, 15);
			y += 22;
			result.add(labelNotify);
		}

		ActionListener openClass = this::openClass;
		for (String entry : entries) {
			JButton button = new JButton(entry);
			button.setBounds(5, y, 480, 20);
			y += 22;
			button.addActionListener(openClass);
			result.add(button);
		}

		result.setPreferredSize(new Dimension(500, y));
		scroll.validate();
		result.repaint();
	}

	private void openClass(ActionEvent event) {
		String clazz = ((JButton) event.getSource()).getText();
		for (ClassWindow window : opened) {
			if (window.clazz.equals(clazz)) {
				window.toFront();
				return;
			}
		}

		opened.add(new ClassWindow(this, clazz));
	}

	private static final class ClassWindow extends JFrame implements MouseListener {
		final String clazz;

		ClassWindow(ReflectTool parent, String clazz) {
			this.clazz = clazz;

			setTitle(clazz.substring(clazz.lastIndexOf('/') + 1) + " 的方法和字段");
			setLayout(null);
			setAlwaysOnTop(true);

			GUIUtil.setLogo(this, "FMD_logo.png");

			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			addWindowListener(parent);

			JPanel panelMethod = new JPanel();
			panelMethod.setLayout(null);
			panelMethod.setBounds(0, 30, 600, 300);

			JPanel panelField = new JPanel();
			panelField.setLayout(null);
			panelField.setBounds(0, 332, 600, 300);

			init(clazz, panelMethod, panelField, null);

			JScrollPane paneM = new JScrollPane();
			paneM.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			paneM.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			paneM.setBounds(panelMethod.getBounds());
			paneM.setViewportView(panelMethod);
			paneM.getVerticalScrollBar().setUnitIncrement(20);

			JScrollPane paneF = new JScrollPane();
			paneF.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			paneF.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			paneF.setBounds(panelField.getBounds());
			paneF.setViewportView(panelField);
			paneF.getVerticalScrollBar().setUnitIncrement(20);

			getContentPane().add(paneM, BorderLayout.PAGE_START);
			getContentPane().add(paneF, BorderLayout.PAGE_END);

			JTextField searchField = new JTextField(20);
			searchField.setBounds(2, 2, 100, 25);
			add(searchField);

			JButton search = new JButton("过滤");
			search.setBounds(124, 2, 80, 25);
			ActionListener l = e -> {
				init(clazz, panelMethod, panelField, searchField.getText().trim().toLowerCase());
				paneF.validate();
				paneM.validate();
			};
			search.addActionListener(l);
			add(search);

			searchField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyTyped(KeyEvent keyEvent) {
					if (keyEvent.getKeyChar() == '\n') {
						l.actionPerformed(null);
					}
				}
			});

			pack();
			setVisible(true);
			setBounds(300, 300, 608, 662);
			setResizable(false);
			validate();
		}

		private void init(String clazz, JPanel pMethod, JPanel pField, String search) {
			pMethod.removeAll();
			pField.removeAll();

			JLabel label = new JLabel("字段");
			label.setBounds(280, 5, 40, 15);
			pField.add(label);

			label = new JLabel("方法");
			label.setBounds(280, 5, 40, 15);
			pMethod.add(label);

			SimpleList<Desc> descs = methodIndex.get(clazz);
			if (descs != null) {
				methodNames = new String[descs.size()];
				int y = 25;
				for (Desc md : descs) {
					try {
						if (search == null || md.name.toLowerCase().contains(search)) {
							List<roj.asm.type.Type> params = TypeHelper.parseMethod(md.param);
							JLabel label1 = makeLabel(y, TypeHelper.humanize(params, md.name, true));
							methodNames[(y - 25) / 22] = md.owner;
							pMethod.add(label1);
							y += 22;
						}
					} catch (Exception e) {
						System.out.println("Error parsing method: " + md);
					}
				}

				pMethod.setPreferredSize(new Dimension(600, y));
			} else {
				label = new JLabel("没有数据");
				label.setForeground(Color.RED);
				label.setBounds(270, 20, 80, 15);
				pMethod.add(label);
			}

			descs = fieldIndex.get(clazz);
			if (descs != null) {
				fieldNames = new String[descs.size()];
				int y = 25;
				for (Desc fd : descs) {
					if (search == null || fd.name.toLowerCase().contains(search)) {
						pField.add(makeLabel(y, fd.name));
						fieldNames[(y - 25) / 22] = fd.owner;
						y += 22;
					}
				}

				pField.setPreferredSize(new Dimension(600, y));
			} else {
				label = new JLabel("没有数据");
				label.setForeground(Color.RED);
				label.setBounds(270, 20, 80, 15);
				pField.add(label);
			}

			pField.repaint();
			pMethod.repaint();
		}

		private JLabel makeLabel(int y, String name) {
			JLabel l = new JLabel(name);
			l.setForeground(Color.BLACK);
			l.addMouseListener(this);
			l.setBounds(2, y, 596, 15);
			return l;
		}

		String[] methodNames, fieldNames;
		String original, searge;

		@Override
		public void mouseClicked(MouseEvent e) {
			clipboard.setContents(new StringSelection(searge), null);
			JLabel label = (JLabel) e.getComponent();
			label.setText("已复制");
		}

		@Override
		public void mousePressed(MouseEvent e) {}

		@Override
		public void mouseReleased(MouseEvent e) {}

		@Override
		public void mouseEntered(MouseEvent e) {
			JLabel label = (JLabel) e.getComponent();
			label.setForeground(Color.RED);
			int i = (label.getY() - 25) / 22;
			original = label.getText();
			label.setText(searge = original.endsWith(")") ? methodNames[i] : fieldNames[i]);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			JLabel label = (JLabel) e.getComponent();
			label.setForeground(Color.BLACK);
			label.setText(original);
		}
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {}

	@Override
	public void windowClosed(WindowEvent e) {
		if (e.getWindow() instanceof ClassWindow) {opened.remove(e.getWindow());} else {
			for (Window window : opened) {
				window.dispose();
			}
			opened.clear();
			opened1 = false;
		}
	}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyChar() == '\n') {
			search(null);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}
}