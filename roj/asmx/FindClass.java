/*
 * Created by JFormDesigner on Fri Dec 22 21:56:16 CST 2023
 */

package roj.asmx;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstRef;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.mapper.util.Desc;
import roj.text.CharList;
import roj.ui.GUIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Roj234
 */
public class FindClass extends JFrame {
	public static void main(String[] args) {
		GUIUtil.systemLook();
		FindClass f = new FindClass();

		f.pack();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private void search() {
		model.clear();

		if (uiSearch.getText().isEmpty()) {
			model.addElement("请输入搜索词");
			return;
		}

		Pattern p = uiSearch.getText().startsWith("!") ? Pattern.compile(uiSearch.getText().substring(1)) : Pattern.compile(uiSearch.getText(), Pattern.LITERAL);
		Predicate<CharSequence> filter = (id) -> p.matcher(id).find();

		MyHashSet<Object> out = new MyHashSet<>();
		if (uiSerClass.isSelected()) {
			for (ConstantData data : ref) {
				if (filter.test(data.name)) out.add(data.name);

				if (!uiDeclOnly.isSelected()) {
					for (Constant c : data.cp.array()) {
						if (c.type() == Constant.CLASS) {
							if (filter.test(c.getEasyCompareValue())) {
								out.add("REF: "+data.name);
								break;
							}
						}
					}
				}
			}
		}
		CharList sb = IOUtil.getSharedCharBuf();
		if (uiSerNode.isSelected()) {
			for (ConstantData data : ref) {
				for (MethodNode n : data.methods) {
					sb.clear();
					sb.append(data.name).append('.').append(n.name()).append(n.rawDesc());
					if (filter.test(sb)) {
						out.add("M: "+new Desc(data.name, n.name(), n.rawDesc()));
						break;
					}
				}
				for (FieldNode n : data.fields) {
					sb.clear();
					sb.append(data.name).append('.').append(n.name()).append(' ').append(n.rawDesc());
					if (filter.test(sb)) {
						out.add("F: "+new Desc(data.name, n.name(), n.rawDesc()));
						break;
					}
				}
				if (uiDeclOnly.isSelected()) continue;

				for (Constant c : data.cp.array()) {
					if (c instanceof CstRef) {
						CstRef ref = (CstRef) c;
						String s = ref.descType();
						sb.clear();
						sb.append(ref.className()).append('.').append(ref.descName());
						if (!s.startsWith("(")) sb.append(' ');
						sb.append(s);
						if (filter.test(sb)) {
							out.add("REF: "+data.name);
							break;
						}
					}
				}
			}
		}
		if (uiSerString.isSelected()) {
			for (ConstantData data : ref) {
				for (Constant c : data.cp.array()) {
					if (c.type() == Constant.STRING) {
						if (filter.test(c.getEasyCompareValue())) {
							out.add(data.name);
							break;
						}
					}
				}
			}
		}
		if (uiSerConstant.isSelected()) {
			for (ConstantData data : ref) {
				for (Constant c : data.cp.array()) {
					if (c.type() >= 3 && c.type() <= 6) {
						if (filter.test(c.getEasyCompareValue())) {
							out.add(data.name);
							break;
						}
					}
				}
			}
		}
		if (uiserAnnotation.isSelected()) {
			for (ConstantData data : ref) {
				Annotations aa = data.parsedAttr(data.cp, Attribute.RtAnnotations);
				checkAnnotation(filter, aa, out, data, data);
				Annotations bb = data.parsedAttr(data.cp, Attribute.ClAnnotations);
				checkAnnotation(filter, bb, out, data, data);
				for (MethodNode method : data.methods) {
					aa = method.parsedAttr(data.cp, Attribute.RtAnnotations);
					checkAnnotation(filter, aa, out, method, data);
					bb = method.parsedAttr(data.cp, Attribute.ClAnnotations);
					checkAnnotation(filter, bb, out, method, data);
				}
				for (FieldNode method : data.fields) {
					aa = method.parsedAttr(data.cp, Attribute.RtAnnotations);
					checkAnnotation(filter, aa, out, method, data);
					bb = method.parsedAttr(data.cp, Attribute.ClAnnotations);
					checkAnnotation(filter, bb, out, method, data);
				}
			}
		}

		for (Object node : out) model.addElement(node);
	}

	private void checkAnnotation(Predicate<CharSequence> filter, Annotations a, MyHashSet<Object> out, Attributed node, ConstantData data) {
		if (a == null) return;
		for (Annotation annotation : a.annotations) {
			if (filter.test(annotation.type)) {
				if (node instanceof ConstantData) {
					ConstantData node1 = (ConstantData) node;
					out.add(node1.name);
				} else {
					RawNode n = (RawNode) node;
					out.add((n instanceof FieldNode ? "F: ":"M: ")+new Desc(data.name, n.name(), n.rawDesc()));
				}
			}
		}
	}

	private void open(File file) {
		ref.clear();
		try (ZipArchive za = new ZipArchive(file)) {
			for (ZEntry value : za.getEntries().values()) {
				if (value.getName().toLowerCase().endsWith(".class")) {
					ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(value)).toByteArray());
					ref.add(data);
				}
			}
			System.out.println("loaded "+ref.size()+" class");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private final SimpleList<ConstantData> ref = new SimpleList<>();
	private final DefaultListModel<Object> model = new DefaultListModel<>();
	public FindClass() {
		initComponents();
		GUIUtil.dropFilePath(this, this::open, false);
		uiOpen.addActionListener(e -> open(GUIUtil.fileLoadFrom("jar file", this)));
		ActionListener ser = e -> search();
		uiDeclOnly.addActionListener(ser);
		uiSerClass.addActionListener(ser);
		uiSerNode.addActionListener(ser);
		uiSerString.addActionListener(ser);
		uiSerConstant.addActionListener(ser);
		uiSearch.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) { search(); }
		});
		uiResult.setModel(model);
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		uiSearch = new JTextField();
		JScrollPane scrollPane1 = new JScrollPane();
		uiResult = new JList<>();
		JLabel label1 = new JLabel();
		uiDeclOnly = new JCheckBox();
		uiSerClass = new JCheckBox();
		uiSerNode = new JCheckBox();
		uiSerString = new JCheckBox();
		uiSerConstant = new JCheckBox();
		uiOpen = new JButton();
		JLabel label2 = new JLabel();
		uiserAnnotation = new JCheckBox();

		//======== this ========
		setTitle("FindClass");
		Container contentPane = getContentPane();
		contentPane.setLayout(null);
		contentPane.add(uiSearch);
		uiSearch.setBounds(5, 25, 280, uiSearch.getPreferredSize().height);

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiResult);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(10, 105, 375, 235);

		//---- label1 ----
		label1.setText("search (regexp IF START WITH !)");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(5, 5), label1.getPreferredSize()));

		//---- uiDeclOnly ----
		uiDeclOnly.setText("Declaration only");
		contentPane.add(uiDeclOnly);
		uiDeclOnly.setBounds(new Rectangle(new Point(270, 45), uiDeclOnly.getPreferredSize()));

		//---- uiSerClass ----
		uiSerClass.setText("class");
		contentPane.add(uiSerClass);
		uiSerClass.setBounds(new Rectangle(new Point(5, 45), uiSerClass.getPreferredSize()));

		//---- uiSerNode ----
		uiSerNode.setText("element (field or method)");
		contentPane.add(uiSerNode);
		uiSerNode.setBounds(new Rectangle(new Point(60, 45), uiSerNode.getPreferredSize()));

		//---- uiSerString ----
		uiSerString.setText("string");
		contentPane.add(uiSerString);
		uiSerString.setBounds(new Rectangle(new Point(5, 65), uiSerString.getPreferredSize()));

		//---- uiSerConstant ----
		uiSerConstant.setText("constant (number)");
		contentPane.add(uiSerConstant);
		uiSerConstant.setBounds(new Rectangle(new Point(65, 65), uiSerConstant.getPreferredSize()));

		//---- uiOpen ----
		uiOpen.setText("Open");
		contentPane.add(uiOpen);
		uiOpen.setBounds(new Rectangle(new Point(315, 10), uiOpen.getPreferredSize()));

		//---- label2 ----
		label2.setText("found");
		contentPane.add(label2);
		label2.setBounds(new Rectangle(new Point(5, 90), label2.getPreferredSize()));

		//---- uiserAnnotation ----
		uiserAnnotation.setText("annotation");
		contentPane.add(uiserAnnotation);
		uiserAnnotation.setBounds(new Rectangle(new Point(190, 65), uiserAnnotation.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(400, 440));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JTextField uiSearch;
	private JList<Object> uiResult;
	private JCheckBox uiDeclOnly;
	private JCheckBox uiSerClass;
	private JCheckBox uiSerNode;
	private JCheckBox uiSerString;
	private JCheckBox uiSerConstant;
	private JButton uiOpen;
	private JCheckBox uiserAnnotation;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
