package roj.mod;

import roj.asm.remapper.IRemapper;
import roj.asm.remapper.util.FlDesc;
import roj.asm.remapper.util.MtDesc;
import roj.asm.util.type.ParamHelper;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.collect.TrieTreeSet;
import roj.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ReflectToolWindow.java
 */
public class ReflectToolWindow extends JFrame {
    private static final int LIMIT = 200;

    static boolean simpleMode = true;

    JTextField className;
    JPanel resultPane;
    JScrollPane scrollPane;

    Set<ClassWindow> subWindows = new MyHashSet<>();

    public ReflectToolWindow(boolean exit, String flag) {
        super("反射工具");
        UIUtil.setLogo(this, "FMD_logo.png");

        setDefaultCloseOperation(exit ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent windowEvent) {
                for(JFrame frame1 : subWindows) {
                    frame1.dispose();
                }
            }
        });

        JPanel panel = new JPanel();

        //add(panel);

        // 创建 JLabel
        JLabel classNameLabel = new JLabel(flag == null ? "类名:" : flag);
        /* 这个方法定义了组件的位置。
         * setBounds(x, y, width, height)
         * x 和 y 指定左上角的新位置，由 width 和 height 指定新的大小。
         */
        classNameLabel.setBounds(10,20,50,25);
        panel.add(classNameLabel);

        className = new JTextField(20);
        className.setBounds(100,20,165,25);
        panel.add(className);


        JButton search = new JButton("搜索");
        search.setBounds(280, 20, 80, 25);
        search.addActionListener(this::search);
        panel.add(search);

        /*JTextField cb = new JTextField(2);
        cb.setToolTipText("0: 正常, 1: AT, 2:Nixim");
        cb.setText(String.valueOf(copyMode));
        cb.setBounds(320, 20, 50, 25);
        cb.addActionListener((e) -> {
            if(TextUtil.isNumber(cb.getText()) == 0)
                copyMode = Integer.parseInt(cb.getText());
        });
        panel.add(cb);*/

        if(flag == null) {
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

        this.scrollPane = scrollPane;

        JLabel tip = new JLabel("加载中...");
        panel1.add(tip);
        tip.setBounds(120, 5, 500, 15);
        resultPane = panel1;

        getContentPane().add(panel, BorderLayout.NORTH);

        getContentPane().add(scrollPane);

        pack();
        setVisible(true);
        setResizable(false);
        setBounds(520, 260, 510, 500);
        validate();

        loadData(tip);
    }

    static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    static final TrieTree<String> simpleClass = new TrieTree<>();
    static final TrieTreeSet fullClass = new TrieTreeSet();
    static final Map<String, ArrayList<MtDesc>> methodIndex = new MyHashMap<>();
    static final Map<String, ArrayList<FlDesc>> fieldIndex = new MyHashMap<>();
    static Map<MtDesc, String> methodMap;
    static Map<FlDesc, String> fieldMap;

    public static void start(boolean exit) {
        new ReflectToolWindow(exit, null);
    }

    protected void loadData(JLabel label) {
        if(fullClass.isEmpty()) {
            Shared.initRemapper();
            IRemapper remapper = Shared.remapper;
            methodMap = remapper.getMethodMap();
            fieldMap = remapper.getFieldMap();
            for(String s : remapper.getClassMap().keySet()) {
                fullClass.add(s);
                final String key = s.substring(s.lastIndexOf('/') + 1).toLowerCase();
                String s1 = simpleClass.put(key, s);
                if(s1 != null) {
                    simpleClass.put(key + '_' + System.currentTimeMillis(), s1);
                }
            }

            for (Map.Entry<MtDesc, String> entry : methodMap.entrySet()) {
                methodIndex.computeIfAbsent(entry.getKey().owner, (s) -> new ArrayList<>()).add(entry.getKey());
            }
            final Comparator<MtDesc> comparator = (o1, o2) -> o1.name.compareToIgnoreCase(o2.name);
            for(ArrayList<MtDesc> list : methodIndex.values()) {
                list.sort(comparator);
                list.trimToSize();
            }

            for (Map.Entry<FlDesc, String> entry : fieldMap.entrySet()) {
                fieldIndex.computeIfAbsent(entry.getKey().owner, (s) -> new ArrayList<>()).add(entry.getKey());
            }
            final Comparator<FlDesc> comparator1 = (o1, o2) -> o1.name.compareToIgnoreCase(o2.name);
            for(ArrayList<FlDesc> list : fieldIndex.values()) {
                list.sort(comparator1);
                list.trimToSize();
            }
        }

        done();

        label.setText("加载完毕，在上方输入类名然后点击搜索吧!");
        label.setForeground(Color.GREEN);

        resultPane.repaint();
    }

    protected void done() {
        className.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
                if (keyEvent.getKeyChar() == '\n') {
                    search(null);
                }
            }
        });
    }

    protected void search(ActionEvent event) {
        String text = className.getText().replace('.', '/');
        Collection<String> entries;
        if(simpleMode) {
            entries = simpleClass.valueMatches(text.toLowerCase(), LIMIT);
        } else {
            if (!text.startsWith("net/"))
                text = "net/minecraft/" + text;

            entries = fullClass.keyMatches(text, LIMIT);
        }

        resultPane.removeAll();
        int y = 2;

        if(entries.isEmpty()) {
            JLabel labelNotify = new JLabel("没有结果!");
            labelNotify.setForeground(Color.RED);
            labelNotify.setBounds(220, y, 80, 15);
            y += 22;
            resultPane.add(labelNotify);
        } else if(entries.size() >= LIMIT) {
            JLabel labelNotify = new JLabel("结果超过" + LIMIT +  "个!");
            labelNotify.setForeground(Color.RED);
            labelNotify.setBounds(200, y, 100, 15);
            y += 22;
            resultPane.add(labelNotify);
        }
        for(String entry : entries) {
            JButton button = new JButton(entry);
            button.setBounds(5, y, 480, 20);
            y += 22;
            button.addActionListener(this::openClass);
            resultPane.add(button);
        }

        resultPane.setPreferredSize(new Dimension(500, y));
        scrollPane.validate();
        resultPane.repaint();
    }

    private void openClass(ActionEvent event) {
        String className = ((JButton)event.getSource()).getText();
        for (ClassWindow window : subWindows) {
            if(window.className.equals(className)) {
                window.requestFocus();
                return;
            }
        }

        subWindows.add(new ClassWindow(this, className));
    }

    private static final class ClassWindow extends JFrame {
        final String className;
        public ClassWindow(ReflectToolWindow parent, String className) {
            this.className = className;

            setTitle(className.substring(className.lastIndexOf('/') + 1) + " 的方法和字段");
            setLayout(null);

            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    parent.subWindows.remove(ClassWindow.this);
                }
            });

            JPanel panelMethod = new JPanel();
            panelMethod.setLayout(null);
            panelMethod.setBounds(0, 30, 600, 300);

            JPanel panelField = new JPanel();
            panelField.setLayout(null);
            panelField.setBounds(0, 332, 600, 300);

            JTextField searchField = new JTextField(20);
            searchField.setBounds(2,2,100,25);
            add(searchField);

            JButton search = new JButton("过滤");
            search.setBounds(124, 2, 80, 25);
            search.addActionListener(e -> {
                initData(className, panelMethod, panelField, searchField.getText());
            });
            add(search);

            initData(className, panelMethod, panelField, "");

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

            pack();
            setVisible(true);
            setBounds(300, 300, 604, 662);
            setResizable(false);
            validate();
        }

        private void initData(String className, JPanel panelMethod, JPanel panelField, String search) {
            panelMethod.removeAll();
            panelField.removeAll();

            JLabel label = new JLabel("字段");
            label.setBounds(280, 5, 40, 15);
            panelField.add(label);

            label = new JLabel("方法");
            label.setBounds(280, 5, 40, 15);
            panelMethod.add(label);

            if(methodIndex.get(className) != null) {
                int y = 25;
                for(MtDesc descriptor : methodIndex.get(className)) {
                    try {
                        List<roj.asm.util.type.Type> params = ParamHelper.parseMethod(descriptor.param);
                        if(descriptor.name.contains(search)) {
                            JLabel label1 = new JLabel(ParamHelper.humanize(params, descriptor.name));
                            label1.addMouseListener(new ClickListener(true, label1, descriptor));
                            label1.setBounds(2, y, 596, 15);
                            panelMethod.add(label1);
                            y += 22;
                        }
                    } catch (Exception e) {
                        System.out.println("Error parsing descriptor: " + descriptor);
                    }
                }

                panelMethod.setPreferredSize(new Dimension(600, y));
            }

            if(panelMethod.getComponentCount() == 1) {
                JLabel label1 = new JLabel("没有数据");
                label1.setForeground(Color.RED);
                label1.setBounds(270, 20, 80, 15);
                panelMethod.add(label1);
            }

            if(fieldIndex.get(className) != null) {
                int y = 25;
                for (FlDesc descriptor : fieldIndex.get(className)) {
                    if(descriptor.name.contains(search)) {
                        JLabel label1 = new JLabel(descriptor.name);
                        label1.addMouseListener(new ClickListener(false, label1, descriptor));
                        label1.setBounds(2, y, 596, 15);
                        panelField.add(label1);
                        y += 22;
                    }
                }

                panelField.setPreferredSize(new Dimension(600, y));
            }

            if(panelField.getComponentCount() == 1) {
                JLabel label1 = new JLabel("没有数据");
                label1.setForeground(Color.RED);
                label1.setBounds(270, 20, 80, 15);
                panelField.add(label1);
            }

            panelField.repaint();
            panelMethod.repaint();
        }

        private static class ClickListener implements MouseListener {
            final boolean isMethod;
            final JLabel label;
            final Object descriptor;
            String originalLabel;

            public ClickListener(boolean isMethod, JLabel label1, Object descriptor) {
                this.isMethod = isMethod;
                this.label = label1;
                this.descriptor = descriptor;
            }

            public void mouseClicked(MouseEvent var1) {
                clipboard.setContents(new StringSelection(getStringToCopy()), null);
                label.setText("已复制");
            }

            private String getStringToCopy() {/*
                switch (copyMode) {
                    case 2: {
                        return "@RemapTo(\"" + getName() + "\")";
                    }
                    case 1: {
                        StringBuilder sb = new StringBuilder();
                        if(isMethod) {
                            MtDesc descriptor = (MtDesc) this.descriptor;
                            sb.append(methodMap.get(descriptor)).append('|').append(descriptor.param).append(" # ").append(descriptor.name);
                        } else {
                            FlDesc descriptor = (FlDesc) this.descriptor;
                            sb.append(fieldMap.get(descriptor)).append(" # ").append(descriptor.name);
                        }
                        return sb.toString();
                    }
                    case 0:
                        return getName();
                }
                return "错误";*/
                return getName();
            }

            public void mousePressed(MouseEvent var1) {}

            public void mouseReleased(MouseEvent var1) {}

            public void mouseEntered(MouseEvent var1) {
                label.setText(getText());
            }

            private String getText() {
                originalLabel = label.getText();
                String name;

                label.setForeground(Color.RED);
                label.setBackground(Color.GRAY);

                return getName();
            }

            private String getName() {
                return isMethod ? methodMap.get(descriptor) : fieldMap.get(descriptor);
            }

            public void mouseExited(MouseEvent var1) {
                label.setText(originalLabel);
                label.setForeground(Color.BLACK);
                label.setBackground(null);
            }
        }
    }
}
