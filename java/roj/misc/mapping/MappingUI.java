/*
 * Created by JFormDesigner on Sat Sep 09 21:07:44 CST 2023
 */

package roj.misc.mapping;

import roj.asm.type.Desc;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.collect.IntSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.crypt.MT19937;
import roj.text.TextReader;
import roj.ui.DoubleClickHelper;
import roj.ui.GuiUtil;
import roj.util.Helpers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Roj234
 */
public class MappingUI extends JFrame {
	private static final DefaultListModel<String> previewList = new DefaultListModel<>();

	private static int alertShown;
	private static final DefaultListModel<NamedMapping> mappings = new DefaultListModel<>();
	private static final class NamedMapping {
		String name;
		Mapping mapping;

		@Override
		public String toString() {
			return (name == null ? "无名氏" : name)+" ("+mapping.getClass().getSimpleName() + "), "+mapping.getClassMap().size()+" entries";
		}
	}


	private void uiLoad(ActionEvent e) {
		File input = GuiUtil.fileLoadFrom("注：先选择类型再点击加载！" + uiMappingType.getSelectedItem().toString(), this);
		if (input == null) return;

		try {
			load(input);
		} catch (Exception e1) {
			Helpers.athrow(e1);
		}
	}
	private void load(File input) throws IOException {
		Mapping m = null;
		switch (uiMappingType.getSelectedItem().toString()) {
			case "srg/xsrg":
				m = new Mapping();
				m.loadMap(input, false);
				break;
			case "tab srg": {
				TSrgMapping m1 = new TSrgMapping();
				m1.readMcpConfig(input, null, new SimpleList<>());
				m = m1;
			}
			break;
			case "mapper cache": {
				Mapper m1 = new Mapper();
				m1.readCache(0L, input);
				m = m1;
			}
			break;
			case "intermediary": {
				YarnMapping m1 = new YarnMapping();
				m1.readIntermediaryMap(input.getName(), TextReader.auto(input), new SimpleList<>());
				m = m1;
			}
			break;
			case "yarn": {
				YarnMapping m1 = new YarnMapping();
				m1.readYarnMap(input, new SimpleList<>(), null, null);
				m = m1;
			}
			break;
			case "mojang": {
				OjngMapping m1 = new OjngMapping();
				m1.readMojangMap(input.getName(), TextReader.auto(input), new SimpleList<>());
				m = m1;
			}
			break;
			case "mcp": {
				List<NamedMapping> maps = uiMappingList.getSelectedValuesList();
				if (maps.size() != 1) {
					JOptionPane.showMessageDialog(this, "请选择一个映射表作为应用的来源\nMCP mapping不能单独使用", "提示", JOptionPane.WARNING_MESSAGE);
					return;
				}
				MCPMapping m1 = new MCPMapping(input, null);
				m1.apply(new SimpleList<>(), maps.get(0).mapping, m = new Mapping());
			}
			break;
		}

		NamedMapping name = getNamedMapping();
		name.mapping = m;
		mappings.addElement(name);
	}

	private NamedMapping getNamedMapping() {
		NamedMapping m = new NamedMapping();
		String name = uiName.getText();
		uiName.setText("");
		m.name = name.isEmpty() ? null : name.trim();
		return m;
	}

	private void uiDel(ActionEvent e) {
		for (NamedMapping m : uiMappingList.getSelectedValuesList()) {
			mappings.removeElement(m);
		}
		System.gc();
	}

	private void uiMerge(ActionEvent e) {
		if (alertShown != (alertShown |= 1))
			JOptionPane.showMessageDialog(this, "从上至下的显示顺序（而不是点击顺序）会决定合并顺序！", "警告", JOptionPane.WARNING_MESSAGE);

		NamedMapping out = getNamedMapping();
		out.mapping = new Mapping();

		List<NamedMapping> maps = uiMappingList.getSelectedValuesList();

		for (NamedMapping m : maps) {
			out.mapping.merge(m.mapping, true);
		}
		mappings.addElement(out);
	}

	private void uiFlip(ActionEvent e) {
		NamedMapping m = getNamedMapping();
		NamedMapping om = uiMappingList.getSelectedValue();
		if (m.name == null) m.name = om.name+"-flip";
		m.mapping = om.mapping.reverse();
		mappings.addElement(m);
	}

	private void uiCopy(ActionEvent e) {
		NamedMapping m = getNamedMapping();
		NamedMapping om = uiMappingList.getSelectedValue();
		if (m.name == null) m.name = om.name+"-copy";
		m.mapping = new Mapping();
		m.mapping.merge(om.mapping);
		mappings.addElement(m);
	}

	private void uiExtend(ActionEvent e) {
		if (alertShown != (alertShown |= 2))
			JOptionPane.showMessageDialog(this, "最后选择（点击）的对象将作为child", "警告", JOptionPane.WARNING_MESSAGE);

		NamedMapping child = mappings.get(uiMappingList.getSelectionModel().getLeadSelectionIndex());
		List<NamedMapping> l = uiMappingList.getSelectedValuesList(); l.remove(child);
		NamedMapping parent = l.get(0);

		NamedMapping m = getNamedMapping();
		m.mapping = new Mapping();
		m.mapping.merge(child.mapping);

		// Mapper{B->C} .extend ( Mapper{A->B} )   =>>  Mapper{A->C}
		m.mapping.extend(parent.mapping, true);

		mappings.addElement(m);
		uiMappingList.setSelectedValue(m, true);

		//uiDel2.setVisible(true);
		//preview(m);
	}

	private void uiSave(ActionEvent e) {
		File file = GuiUtil.fileSaveTo("保存映射表", "mapping.map", MappingUI.this);
		if (file == null) return;

		try {
			uiMappingList.getSelectedValue().mapping.saveMap(file);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public MappingUI() {
		initComponents();

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		model.addElement("srg/xsrg");
		model.addElement("tab srg");
		model.addElement("mapper cache");
		model.addElement("intermediary");
		model.addElement("yarn");
		model.addElement("mojang");
		model.addElement("mcp");
		uiMappingType.setModel(model);

		uiMappingList.setModel(mappings);
		uiMappingList.getSelectionModel().addListSelectionListener(evt -> {
			int len = uiMappingList.getSelectedIndices().length;
			boolean some = len > 0;
			boolean one = len == 1;
			boolean many = len > 1;

			uiDel.setEnabled(some);
			uiFlip.setEnabled(one);
			uiMerge.setEnabled(many);
			uiExtend.setEnabled(len == 2);
			uiCopy.setEnabled(one);
			uiSave.setEnabled(one);
		});

		uiMappingList.addMouseListener(new DoubleClickHelper(uiMappingList, 300, (e) -> {
			uiDel2.setVisible(false);
			preview(uiMappingList.getSelectedValue());
		}));

		dlgPreview.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				setEnabled(true);
			}
		});

		uiPreview.setModel(previewList);
	}

	private void preview(NamedMapping m) {
		int i = 1000;
		IntSet id = null;

		if (m.mapping.getClassMap().size() > i) {
			Random r = new MT19937();
			id = new IntSet(i);
			while (i-- > 0) while (!id.add(r.nextInt(m.mapping.getClassMap().size())));
		}

		Map<String, List<String>> tmp = new MyHashMap<>();
		i = 0;
		for (Map.Entry<String, String> entry : m.mapping.getClassMap().entrySet()) {
			if (id == null || id.remove(i++))
				tmp.computeIfAbsent(entry.getKey(), Helpers.fnArrayList()).add(entry.getKey() + " => " + entry.getValue());
		}
		for (Map.Entry<Desc, String> entry : m.mapping.getMethodMap().entrySet()) {
			Desc d = entry.getKey();
			List<String> list = tmp.get(d.owner);
			if (list != null) list.add("  "+d.name+d.param + " => " + entry.getValue());
		}
		for (Map.Entry<Desc, String> entry : m.mapping.getFieldMap().entrySet()) {
			List<String> list = tmp.get(entry.getKey().owner);
			if (list != null) list.add("  "+entry.getKey().name + " => " + entry.getValue());
		}

		previewList.removeAllElements();
		for (List<String> s : tmp.values()) {
			for (String ss : s) {
				previewList.addElement(ss);
			}
		}

		dlgPreview.show();
	}

	public static void main(String[] args) throws Exception {
		GuiUtil.systemLook();
		MappingUI f = new MappingUI();

		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.show();
	}

	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
		JScrollPane scrollPane1 = new JScrollPane();
		uiMappingList = new JList<>();
		uiName = new JTextField();
		uiMappingType = new JComboBox<>();
		JLabel label1 = new JLabel();
		JButton uiLoad = new JButton();
		uiDel = new JButton();
		uiFlip = new JButton();
		uiMerge = new JButton();
		uiExtend = new JButton();
		uiSave = new JButton();
		uiCopy = new JButton();
		dlgPreview = new JDialog();
		JScrollPane scrollPane2 = new JScrollPane();
		uiPreview = new JList<>();
		uiDel2 = new JButton();

		//======== this ========
		setTitle("\u6620\u5c04\u8868Playground");
		setResizable(false);
		Container contentPane = getContentPane();
		contentPane.setLayout(null);

		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(uiMappingList);
		}
		contentPane.add(scrollPane1);
		scrollPane1.setBounds(10, 35, 320, 275);
		contentPane.add(uiName);
		uiName.setBounds(35, 5, 100, uiName.getPreferredSize().height);
		contentPane.add(uiMappingType);
		uiMappingType.setBounds(134, 5, 90, uiMappingType.getPreferredSize().height);

		//---- label1 ----
		label1.setText("\u540d\u79f0");
		contentPane.add(label1);
		label1.setBounds(new Rectangle(new Point(6, 8), label1.getPreferredSize()));

		//---- uiLoad ----
		uiLoad.setText("\u52a0\u8f7d");
		uiLoad.setMargin(new Insets(2, 4, 2, 4));
		uiLoad.addActionListener(e -> uiLoad(e));
		contentPane.add(uiLoad);
		uiLoad.setBounds(new Rectangle(new Point(222, 4), uiLoad.getPreferredSize()));

		//---- uiDel ----
		uiDel.setText("del");
		uiDel.setMargin(new Insets(2, 4, 2, 4));
		uiDel.setEnabled(false);
		uiDel.addActionListener(e -> uiDel(e));
		contentPane.add(uiDel);
		uiDel.setBounds(new Rectangle(new Point(30, 318), uiDel.getPreferredSize()));

		//---- uiFlip ----
		uiFlip.setText("flip");
		uiFlip.setMargin(new Insets(2, 4, 2, 4));
		uiFlip.setEnabled(false);
		uiFlip.addActionListener(e -> uiFlip(e));
		contentPane.add(uiFlip);
		uiFlip.setBounds(new Rectangle(new Point(60, 318), uiFlip.getPreferredSize()));

		//---- uiMerge ----
		uiMerge.setText("merge");
		uiMerge.setMargin(new Insets(2, 4, 2, 4));
		uiMerge.setEnabled(false);
		uiMerge.addActionListener(e -> uiMerge(e));
		contentPane.add(uiMerge);
		uiMerge.setBounds(new Rectangle(new Point(96, 318), uiMerge.getPreferredSize()));

		//---- uiExtend ----
		uiExtend.setText("extend");
		uiExtend.setMargin(new Insets(2, 4, 2, 4));
		uiExtend.setEnabled(false);
		uiExtend.addActionListener(e -> uiExtend(e));
		contentPane.add(uiExtend);
		uiExtend.setBounds(new Rectangle(new Point(138, 318), uiExtend.getPreferredSize()));

		//---- uiSave ----
		uiSave.setText("save");
		uiSave.setMargin(new Insets(2, 4, 2, 4));
		uiSave.setEnabled(false);
		uiSave.addActionListener(e -> uiSave(e));
		contentPane.add(uiSave);
		uiSave.setBounds(new Rectangle(new Point(222, 318), uiSave.getPreferredSize()));

		//---- uiCopy ----
		uiCopy.setText("copy");
		uiCopy.setMargin(new Insets(2, 4, 2, 4));
		uiCopy.setEnabled(false);
		uiCopy.addActionListener(e -> uiCopy(e));
		contentPane.add(uiCopy);
		uiCopy.setBounds(new Rectangle(new Point(186, 318), uiCopy.getPreferredSize()));

		contentPane.setPreferredSize(new Dimension(345, 345));
		pack();
		setLocationRelativeTo(getOwner());

		//======== dlgPreview ========
		{
			dlgPreview.setTitle("\u9884\u89c8");
			Container dlgPreviewContentPane = dlgPreview.getContentPane();
			dlgPreviewContentPane.setLayout(null);

			//======== scrollPane2 ========
			{

				//---- uiPreview ----
				uiPreview.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				scrollPane2.setViewportView(uiPreview);
			}
			dlgPreviewContentPane.add(scrollPane2);
			scrollPane2.setBounds(5, 5, 390, 280);

			//---- uiDel2 ----
			uiDel2.setText("\u53d6\u6d88\u64cd\u4f5c");
			uiDel2.addActionListener(e -> uiDel(e));
			dlgPreviewContentPane.add(uiDel2);
			uiDel2.setBounds(new Rectangle(new Point(160, 290), uiDel2.getPreferredSize()));

			dlgPreviewContentPane.setPreferredSize(new Dimension(400, 325));
			dlgPreview.pack();
			dlgPreview.setLocationRelativeTo(dlgPreview.getOwner());
		}
		// JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
	private JList<NamedMapping> uiMappingList;
	private JTextField uiName;
	private JComboBox<String> uiMappingType;
	private JButton uiDel;
	private JButton uiFlip;
	private JButton uiMerge;
	private JButton uiExtend;
	private JButton uiSave;
	private JButton uiCopy;
	private JDialog dlgPreview;
	private JList<String> uiPreview;
	private JButton uiDel2;
	// JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}