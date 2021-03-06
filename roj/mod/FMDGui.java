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

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.TaskExecutor;
import roj.concurrent.task.ITask;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.mod.util.IntCallable;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.TextAreaPrintStream;
import roj.ui.UIUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static javax.accessibility.AccessibleContext.ACCESSIBLE_TEXT_PROPERTY;
import static javax.swing.JOptionPane.*;
import static roj.mod.Shared.*;

/**
 * FMD Gui frontend
 *
 * @author Roj234
 * @since 2021/5/29 22:49
 */
public class FMDGui extends JFrame {
    public static final int MIN_CONSOLE_OUTPUT_HEIGHT = 800;
    static final Matcher FILE_NAME;
    static {
        Pattern pattern;
        try {
            pattern = Pattern.compile("^(?!_)(?!.*?_$)[a-zA-Z0-9\\-_\\p{Script=Han}.]{1,20}$");
        } catch (PatternSyntaxException e1) {
            pattern = Pattern.compile("^(?!_)(?!.*?_$)[a-zA-Z0-9\\-_\\u4e00-\\u9fa5.]{1,20}$");
        }

        FILE_NAME = pattern.matcher("");
    }

    static FMDGui frame;

    private final JTextArea consoleOutput;
    private final JPanel panel1;

    public FMDGui() {
        super("FMD " + VERSION + " ?????? Roj234");

        try {
            File af = new File(TMP_DIR, "noFirstUse");
            if(!af.isFile() || !IOUtil.readUTF(new FileInputStream(af)).equals(VERSION)) {
                try(FileOutputStream fos = new FileOutputStream(af)) {
                    ByteList.encodeUTF(VERSION).writeToStream(fos);
                }
                about(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        JPanel panel = new JPanel();

        // ?????? JLabel
        JButton button = new JButton("??????");
        button.addActionListener(FMDGui::build);
        button.setToolTipText("??????????????????????????????????????????jar");
        panel.add(button);

        button = new JButton("??????");
        button.setToolTipText("??????,???????????????????????????/mods?????????,???????????????");
        button.addActionListener(FMDGui::runClient);
        panel.add(button);

        button = new JButton("??????");
        button.addActionListener(FMDGui::config);
        button.setToolTipText("??????????????????, ??????????????????, ????????????");
        panel.add(button);

        button = new JButton("????????????");
        button.addActionListener(FMDGui::reflectTool);
        button.setToolTipText("??????MCP??????SRG???????????????");
        panel.add(button);

        button = new JButton("?????????");
        button.addActionListener(FMDGui::remap);
        button.setToolTipText("???Obf(SRG)?????????jar??????Dev(MCP)?????????jar(???????????????))");
        panel.add(button);

        button = new JButton("GC");
        button.addActionListener(FMDGui::gc);
        button.setToolTipText("??????????????????????????????");
        panel.add(button);

        button = new JButton("??????");
        button.addActionListener(FMDGui::about);
        panel.add(button);

        JScrollPane scrollPane = new JScrollPane();//??????????????????
        scrollPane.setBounds(0, 0, 500, 350);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        panel1 = new JPanel();
        panel1.setLayout(null);

        scrollPane.setViewportView(panel1);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        JTextArea textArea = new JTextArea("????????????????????????...");
        panel1.add(textArea);
        System.setOut(new TextAreaPrintStream(textArea, 131072));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        consoleOutput = textArea;

        getContentPane().add(panel, BorderLayout.NORTH);

        getContentPane().add(scrollPane);

        setMinimumSize(new Dimension(534, 400));
        pack();
        setResizable(true);
        setBounds(0, 0, 540, 400);
        UIUtil.center(this);

        setHeight();

        validate();

        addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                frame = null;
            }
        });

        consoleOutput.getAccessibleContext().addPropertyChangeListener(evt -> {
            if(ACCESSIBLE_TEXT_PROPERTY.equals(evt.getPropertyName())) {
                SwingUtilities.invokeLater(FMDGui.this::setHeight);
            }
        });
    }

    private static void about(ActionEvent event) {
        ImageIcon icon = null;
        try {
            InputStream iin = FMDGui.class.getClassLoader().getResourceAsStream("qrcode.png");
            if(iin != null)
                icon = new ImageIcon(ImageIO.read(iin));
        } catch (IOException ignored) {}

        JOptionPane.showMessageDialog(frame,
                "FMD - ??????mod???????????? - ?????? Roj234\n" +
                        VERSION + "\n" +
                        "\n" +
                        "  ??????????????????,????????????\n" +
                        "  ????????????bug\n", "??????FMD", INFORMATION_MESSAGE, icon);
    }

    private static void gc(ActionEvent event) {
        long free = Runtime.getRuntime().freeMemory();
        try {
            ATHelper.gc();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.gc();
        free = Runtime.getRuntime().freeMemory() - free;
        frame.consoleOutput.setText("????????? " + (free / 1024 / 1024) + " MB?????????.");
    }

    private int getOffset() {
        return consoleOutput.getDocument().getEndPosition().getOffset();
    }

    private void setHeight() {
        consoleOutput.setBounds(0, 0, getWidth() - 32, Math.max(MIN_CONSOLE_OUTPUT_HEIGHT, getOffset()));
        panel1.setPreferredSize(consoleOutput.getSize());
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        frame = new FMDGui();

        UIUtil.setLogo(frame, "FMD_logo.png");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        try {
            if(!Shared.loadProject(false)) {
                JOptionPane.showMessageDialog(frame, "??????????????????????????????, ???????????????!", "??????", ERROR_MESSAGE);
                initDefaultConf();
            }
            frame.setVisible(true);
        } catch (RuntimeException e) {
            initDefaultConf();
        }
    }

    private static void initDefaultConf() {
        Shared.setProject("default");
        JFrame frame1 = new ConfigEdit(project, frame);
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(false);
    }

    private static void config(ActionEvent event) {
        String[] availableValue = new String [] {
                "??????",
                "??????",
                "??????"
        };
        Project p = project;
        switch (JOptionPane.showOptionDialog(frame, "????????? " + p.getFile().getAbsolutePath().replace(BASE.getAbsolutePath(), ""), "??????", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
            case YES_OPTION:
                new ConfigEdit(p);
                break;
            case NO_OPTION: {
                JFileChooser fileChooser = new JFileChooser(Shared.PROJECTS_DIR);
                fileChooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isFile() && f.getName().endsWith(".json") && !f.getName().equals("index.json");
                    }

                    @Override
                    public String getDescription() {
                        return "????????????";
                    }
                });

                if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File selected = fileChooser.getSelectedFile();
                    Shared.setProject(selected.getName().substring(0, selected.getName().lastIndexOf('.')));

                    frame.consoleOutput.setText("????????? " + selected.getAbsolutePath());
                }
            }
            break;
            case CANCEL_OPTION: {
                String name;
                do {
                    name = JOptionPane.showInputDialog(frame, "??????????????????,???????????????", "??????", QUESTION_MESSAGE);
                    if(name == null)
                        return;
                    if(FILE_NAME.reset(name).matches() && !name.equals("index")) {
                        break;
                    }
                    JOptionPane.showMessageDialog(frame, "??????????????????!", "??????", WARNING_MESSAGE);
                } while(true);
                new ConfigEdit(Project.load(name));
            }
            break;
        }
    }

    private static void remap(ActionEvent event) {
        frame.consoleOutput.setText("");

        JFileChooser fileChooser = new JFileChooser(BASE);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        ArrayList<String> files = new ArrayList<>();
        while (true) {
            int status = fileChooser.showOpenDialog(frame);
            //?????????????????????????????????
            if (status == JFileChooser.APPROVE_OPTION) {
                files.add(fileChooser.getSelectedFile().getAbsolutePath());
                if(JOptionPane.showConfirmDialog(frame, "????????????????", "??????", YES_NO_OPTION) == NO_OPTION) {
                    break;
                }
            } else {
                break;
            }
        }

        if(files.isEmpty())
            return;

        files.add("");

        final String[] args = files.toArray(new String[files.size()]);
        ArrayUtil.inverse(args);

        asyncRun(() -> FMDMain.deobf(args, false));
    }

    static final TaskExecutor asyncExecutor = new TaskExecutor();
    static ITask task;

    private static void asyncRun(IntCallable intSupplier) {
        if(!asyncExecutor.isAlive()) {
            asyncExecutor.start();
        }

        if(task != null && !task.isDone()) {
            if(JOptionPane.showConfirmDialog(frame, "??????????????????, ??????????", "??????", OK_CANCEL_OPTION) != OK_OPTION)
                return;
            task.cancel(true);
        }

        asyncExecutor.pushTask(task = new Task(intSupplier));
    }

    private static void reflectTool(ActionEvent event) {
        new ReflectTool(false, null);
    }

    private static void runClient(ActionEvent event) {
        Map<String, Object> map = getCompileSetting();
        if(map == null)
            return;
        asyncRun(() -> FMDMain.run(map));
    }

    private static void build(ActionEvent event) {
        Map<String, Object> map = getCompileSetting();
        if(map == null)
            return;

        asyncRun(() -> FMDMain.build(map));
    }

    private static Map<String, Object> getCompileSetting() {
        frame.consoleOutput.setText("");
        Map<String, Object> map = new MyHashMap<>(2, 10);

        String[] availableValue = new String [] {
                "????????????",
                "????????????"
        };
        switch (JOptionPane.showOptionDialog(frame, "????????? ", "??????", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
            case YES_OPTION:
                map.put("zl", null);
                break;
            case NO_OPTION:
                break;
            case CLOSED_OPTION:
                return null;
        }
        return map;
    }

    private static class ConfigEdit extends JFrame {
        private final Project p;
        private final JTextField verInp, atInput, charsetInp, dependInp;
        private final Set<String> error = new MyHashSet<>();

        private JFrame parent;

        public ConfigEdit(Project p) {
            super(p.getFile().getName() + " - ????????????");
            this.p = p;

            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel panel = new JPanel();

            JLabel verLab = new JLabel("??????:");
            verLab.setBounds(10,10,80,25);
            panel.add(verLab);

            verInp = new JTextField(p.version, 20);
            verInp.setBounds(100,10,165,25);
            panel.add(verInp);

            JLabel atLab = new JLabel("AT.cfg??????(?):");
            atLab.setBounds(10,40,80,25);
            atLab.setToolTipText("AccessTransformer\n???????????????");
            panel.add(atLab);

            atInput = new JTextField(p.atName, 20);
            atInput.setBounds(100,40,165,25);
            panel.add(atInput);

            JLabel charsetLab = new JLabel("??????:");
            charsetLab.setBounds(10,70,80,25);
            panel.add(charsetLab);

            charsetInp = new JTextField(p.charset.name(), 20);
            charsetInp.setBounds(100,70,165,25);
            panel.add(charsetInp);

            JLabel dependLab = new JLabel("??????:");
            dependLab.setBounds(10,100,80,25);
            dependLab.setToolTipText("???????????????(|)??????");
            panel.add(dependLab);

            dependInp = new JTextField(p.dependencyString(), 20);
            dependInp.setBounds(100,100,165,25);
            panel.add(dependInp);

            JButton save = new JButton("??????");
            save.setBounds(40, 130, 60, 25);
            save.addActionListener(this::save);
            panel.add(save);

            JButton close = new JButton("??????");
            close.setBounds(180, 130, 60, 25);
            close.addActionListener((e) -> {
                if(parent != null) {
                    System.exit(0);
                } else {
                    ConfigEdit.this.dispose();
                }
            });
            panel.add(close);

            panel.setLayout(null);
            panel.setBounds(400, 200, 300, 190);
            add(panel);

            pack();
            setVisible(true);
            setResizable(false);
            setBounds(400, 200, 300, 190);
            validate();
        }

        public ConfigEdit(Project p, JFrame frame) {
            this(p);
            this.parent = frame;
            JOptionPane.showMessageDialog(this, "??????????????????", "??????", WARNING_MESSAGE);
        }

        private void save(ActionEvent event) {
            error.clear();
            if(atInput.getText().length() > 0 && !FILE_NAME.reset(atInput.getText()).matches()) {
                error.add("AT??????????????????");
            } else {
                p.atName = atInput.getText();
            }
            try {
                p.charset = Charset.forName(charsetInp.getText());
            } catch (Throwable e1) {
                error.add("???????????????");
            }

            if(!FILE_NAME.reset(verInp.getText()).matches()) {
                error.add("???????????????");
            } else {
                p.version = verInp.getText();
            }

            p.setDependencyString(dependInp.getText());

            if(error.isEmpty()) {
                p.save();

                File projPath = new File(BASE.getAbsolutePath() + File.separatorChar + "ps" + File.separatorChar + p.name);

                if(!projPath.isDirectory() && projPath.mkdirs()) {
                    ZipUtil.unzip(BASE.getAbsolutePath() + "/util/default.zip", projPath.getAbsolutePath() + "/");
                }

                if(p.getFile().equals(project.getFile())) {
                    project.reload();
                }
                dispose();
                if(parent != null)
                    parent.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, TextUtil.prettyPrint(error), "??????", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static class Task implements ITask {
        IntCallable fn;

        public Task(IntCallable intSupplier) {
            fn = intSupplier;
        }

        @Override
        public boolean cancel(boolean force) {
            return fn.stop();
        }

        @Override
        public void calculate() {
            try {
                int code = fn.call();
                fn = null;
                frame.consoleOutput.append("???????????????, ?????????" + code);
            } catch (Throwable e) {
                CmdUtil.warning("?????????????????????", e);
            }
        }

        @Override
        public boolean isDone() {
            return fn == null;
        }
    }
}
