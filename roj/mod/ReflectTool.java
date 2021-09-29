/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.mod;

import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.util.Desc;
import roj.asm.type.ParamHelper;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.collect.TrieTreeSet;
import roj.ui.UIUtil;
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
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class ReflectTool extends JFrame {
    private static final int MAX_DISPLAY = 200;

    static boolean simpleMode = true;

    JTextField classInp;
    JPanel result;
    JScrollPane scroll;

    Set<ClassWindow> opened = new MyHashSet<>();

    public ReflectTool(boolean exit, String flag) {
        super("反射工具");
        UIUtil.setLogo(this, "FMD_logo.png");

        setDefaultCloseOperation(exit ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent windowEvent) {
                for(JFrame frame1 : opened) {
                    frame1.dispose();
                }
            }
        });

        JPanel panel = new JPanel();

        JLabel classNameLabel = new JLabel(flag == null ? "类名:" : flag);
        classNameLabel.setBounds(10,20,50,25);
        panel.add(classNameLabel);

        classInp = new JTextField(20);
        classInp.setBounds(100,20,165,25);
        panel.add(classInp);

        JButton search = new JButton("搜索");
        search.setBounds(280, 20, 80, 25);
        search.addActionListener(this::search);
        panel.add(search);

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
        setBounds(520, 260, 510, 500);
        validate();

        loadData(tip);
    }

    static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    static final TrieTree<String>             simple2full = new TrieTree<>();
    static final TrieTreeSet                  fullClass   = new TrieTreeSet();
    static final Map<String, ArrayList<Desc>> methodIndex = new MyHashMap<>(),
            fieldIndex                                    = new MyHashMap<>();
    static Map<Desc, String> methodMap, fieldMap;

    public static void start(boolean exit) {
        new ReflectTool(exit, null);
    }

    protected void loadData(JLabel label) {
        if(fullClass.isEmpty()) {
            Shared.initForwardMapper();
            ConstMapper remapper = Shared.mapperFwd;
            methodMap = remapper.getMethodMap();
            fieldMap = remapper.getFieldMap();
            for(String s : remapper.getClassMap().keySet()) {
                fullClass.add(s);
                final String key = s.substring(s.lastIndexOf('/') + 1).toLowerCase();
                String s1 = simple2full.put(key, s);
                if(s1 != null) {
                    simple2full.put(key + '_' + System.currentTimeMillis(), s1);
                }
            }

            for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
                methodIndex.computeIfAbsent(entry.getKey().owner, Helpers.cast(Helpers.fnArrayList())).add(entry.getKey());
            }
            final Comparator<Desc> comparator = (o1, o2) -> o1.name.compareToIgnoreCase(o2.name);
            for(ArrayList<Desc> list : methodIndex.values()) {
                list.sort(comparator);
                list.trimToSize();
            }

            for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
                fieldIndex.computeIfAbsent(entry.getKey().owner, Helpers.cast(Helpers.fnArrayList())).add(entry.getKey());
            }
            for(ArrayList<Desc> list : fieldIndex.values()) {
                list.sort(comparator);
                list.trimToSize();
            }
        }

        done();

        label.setText("加载完毕，在上方输入类名然后点击搜索吧!");
        label.setForeground(Color.GREEN);

        result.repaint();
    }

    protected void done() {
        classInp.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
                if (keyEvent.getKeyChar() == '\n') {
                    search(null);
                }
            }
        });
    }

    protected void search(ActionEvent event) {
        String text = classInp.getText().replace('.', '/');
        Collection<String> entries;
        if(simpleMode) {
            entries = simple2full.valueMatches(text.toLowerCase(), MAX_DISPLAY);
        } else {
            if (!text.startsWith("net/"))
                text = "net/minecraft/" + text;

            entries = fullClass.keyMatches(text, MAX_DISPLAY);
        }

        result.removeAll();
        int y = 2;

        if(entries.isEmpty()) {
            JLabel labelNotify = new JLabel("没有结果!");
            labelNotify.setForeground(Color.RED);
            labelNotify.setBounds(220, y, 80, 15);
            y += 22;
            result.add(labelNotify);
        } else if(entries.size() >= MAX_DISPLAY) {
            JLabel labelNotify = new JLabel("结果超过" + MAX_DISPLAY +  "个!");
            labelNotify.setForeground(Color.RED);
            labelNotify.setBounds(200, y, 100, 15);
            y += 22;
            result.add(labelNotify);
        }
        for(String entry : entries) {
            JButton button = new JButton(entry);
            button.setBounds(5, y, 480, 20);
            y += 22;
            button.addActionListener(this::openClass);
            result.add(button);
        }

        result.setPreferredSize(new Dimension(500, y));
        scroll.validate();
        result.repaint();
    }

    private void openClass(ActionEvent event) {
        String className = ((JButton)event.getSource()).getText();
        for (ClassWindow window : opened) {
            if(window.className.equals(className)) {
                window.requestFocus();
                return;
            }
        }

        opened.add(new ClassWindow(this, className));
    }

    private static final class ClassWindow extends JFrame {
        final String className;
        public ClassWindow(ReflectTool parent, String className) {
            this.className = className;

            setTitle(className.substring(className.lastIndexOf('/') + 1) + " 的方法和字段");
            setLayout(null);

            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    parent.opened.remove(ClassWindow.this);
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
            ActionListener l = e -> {
                initData(className, panelMethod, panelField, searchField.getText().toLowerCase());
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

            initData(className, panelMethod, panelField, null);

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
                for(Desc descriptor : methodIndex.get(className)) {
                    try {
                        List<roj.asm.type.Type> params = ParamHelper.parseMethod(descriptor.param);
                        if(search == null || descriptor.name.toLowerCase().contains(search)) {
                            JLabel label1 = new JLabel(ParamHelper.humanize(params, descriptor.name));
                            label1.addMouseListener(new LabelClick(true, label1, descriptor));
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
                for (Desc descriptor : fieldIndex.get(className)) {
                    if(search == null || descriptor.name.toLowerCase().contains(search)) {
                        JLabel label1 = new JLabel(descriptor.name);
                        label1.addMouseListener(new LabelClick(false, label1, descriptor));
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

        private static class LabelClick implements MouseListener {
            final boolean md;
            final JLabel label;
            final Desc   descriptor;
            String originalLabel;

            public LabelClick(boolean md, JLabel label1, Desc descriptor) {
                this.md = md;
                this.label = label1;
                this.descriptor = descriptor;
            }

            public void mouseClicked(MouseEvent var1) {
                clipboard.setContents(new StringSelection(getName()), null);
                label.setText("已复制");
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
                return md ? methodMap.get(descriptor) : fieldMap.get(descriptor);
            }

            public void mouseExited(MouseEvent var1) {
                label.setText(originalLabel);
                label.setForeground(Color.BLACK);
                label.setBackground(null);
            }
        }
    }
}
