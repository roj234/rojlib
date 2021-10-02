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
import roj.concurrent.task.ITaskNaCl;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.mod.util.IntCallable;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.TextAreaPrintStream;
import roj.ui.UIUtil;
import roj.util.ArrayUtil;
import roj.util.ByteWriter;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
 * @version 0.1
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
        super("FMD " + VERSION + " —— Roj234");

        try {
            File af = new File(TMP_DIR, "noFirstUse");
            if(!af.isFile() || !IOUtil.readUTF(new FileInputStream(af)).equals(VERSION)) {
                try(FileOutputStream fos = new FileOutputStream(af)) {
                    ByteWriter.encodeUTF(VERSION).writeToStream(fos);
                }
                about(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        JPanel panel = new JPanel();

        // 创建 JLabel
        JButton button = new JButton("构建");
        button.addActionListener(FMDGui::build);
        button.setToolTipText("把当前项目的文件编译为发布版jar");
        panel.add(button);

        button = new JButton("调试");
        button.setToolTipText("构建,并移动结果到客户端/mods文件夹,启动客户端");
        button.addActionListener(FMDGui::runClient);
        panel.add(button);

        button = new JButton("配置");
        button.addActionListener(FMDGui::config);
        button.setToolTipText("配置当前项目, 选择其他项目, 新建项目");
        panel.add(button);

        button = new JButton("反射工具");
        button.addActionListener(FMDGui::reflectTool);
        button.setToolTipText("查看MCP名到SRG名的映射表");
        panel.add(button);

        button = new JButton("重映射");
        button.addActionListener(FMDGui::remap);
        button.setToolTipText("把Obf(SRG)版本的jar化为Dev(MCP)版本的jar(或者反过来))");
        panel.add(button);

        button = new JButton("GC");
        button.addActionListener(FMDGui::gc);
        button.setToolTipText("清理内存并释放文件锁");
        panel.add(button);

        button = new JButton("关于");
        button.addActionListener(FMDGui::about);
        panel.add(button);

        JScrollPane scrollPane = new JScrollPane();//创建滚动组件
        scrollPane.setBounds(0, 0, 500, 350);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        panel1 = new JPanel();
        panel1.setLayout(null);

        scrollPane.setViewportView(panel1);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        JTextArea textArea = new JTextArea("这里是控制台输出...");
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
                "FMD - 快速mod开发环境 - 作者 Roj234\n" +
                        VERSION + "\n" +
                        "\n" +
                        "  修复不少bug\n" +
                        "  支持解决 “父类的方法被子类实现的接口使用”\n" +
                        "    这个问题FG一直没修\n" +
                        "    这会导致AbstractMethodError\n" +
                        "      当你有一个Tile实现了一个接口\n" +
                        "      里面包含诸如getWorld的抽象方法\n" +
                        "      然后这个接口的调用者就会在生产环境出错\n" +
                        "  支持自动混淆代码\n" +
                        "    请在配置文件中配置\n" +
                        "    可能还有BUG，见谅\n" +
                        "\n" +
                        "下一个版本更新: \n" +
                        "  可能会支持1.17的开发, 开发！\n", "关于FMD", INFORMATION_MESSAGE, icon);
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
        frame.consoleOutput.setText("释放了 " + (free / 1024 / 1024) + " MB的内存.");
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
            if(!Shared.loadConfig(false)) {
                JOptionPane.showMessageDialog(frame, "配置文件不存在或有误, 已恢复默认!", "错误", ERROR_MESSAGE);
                initDefaultConf();
            }
            frame.setVisible(true);
        } catch (RuntimeException e) {
            initDefaultConf();
        }
    }

    private static void initDefaultConf() {
        Shared.setConfig("default");
        JFrame frame1 = new ConfigEdit(currentProject, frame);
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(false);
    }

    private static void config(ActionEvent event) {
        String[] availableValue = new String [] {
                "编辑",
                "选择",
                "新建"
        };
        Project project = Shared.currentProject;
        switch (JOptionPane.showOptionDialog(frame, "已选择 " + project.getFile().getAbsolutePath().replace(BASE.getAbsolutePath(), ""), "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
            case YES_OPTION:
                new ConfigEdit(project);
                break;
            case NO_OPTION: {
                JFileChooser fileChooser = new JFileChooser(Shared.PROJ_CONF_DIR);
                fileChooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isFile() && f.getName().endsWith(".json") && !f.getName().equals("index.json");
                    }

                    @Override
                    public String getDescription() {
                        return "项目配置";
                    }
                });

                if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File selected = fileChooser.getSelectedFile();
                    Shared.setConfig(selected.getName().substring(0, selected.getName().lastIndexOf('.')));

                    frame.consoleOutput.setText("已选择 " + selected.getAbsolutePath());
                }
            }
            break;
            case CANCEL_OPTION: {
                String name;
                do {
                    name = JOptionPane.showInputDialog(frame, "请输入项目名,也是文件名", "输入", QUESTION_MESSAGE);
                    if(name == null)
                        return;
                    if(FILE_NAME.reset(name).matches() && !name.equals("index")) {
                        break;
                    }
                    JOptionPane.showMessageDialog(frame, "无效的文件名!", "警告", WARNING_MESSAGE);
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
            //没有选打开按钮结果提示
            if (status == JFileChooser.APPROVE_OPTION) {
                files.add(fileChooser.getSelectedFile().getAbsolutePath());
                if(JOptionPane.showConfirmDialog(frame, "还有前置吗?", "询问", YES_NO_OPTION) == NO_OPTION) {
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

        asyncRun(() -> FMDMain.deobf(args));
    }

    static final TaskExecutor asyncExecutor = new TaskExecutor();
    static ITask task;

    private static void asyncRun(IntCallable intSupplier) {
        if(!asyncExecutor.isAlive()) {
            asyncExecutor.start();
        }

        if(task != null && !task.isDone()) {
            if(JOptionPane.showConfirmDialog(frame, "有操作未完成, 继续吗?", "询问", OK_CANCEL_OPTION) != OK_OPTION)
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
                "增量编译",
                "全量编译"
        };
        switch (JOptionPane.showOptionDialog(frame, "请选择 ", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
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
        private final Project project;
        private final JTextField verInp, atInput, charsetInp, dependInp;
        private final Set<String> error = new MyHashSet<>();

        private JFrame parent;

        public ConfigEdit(Project project) {
            super(project.getFile().getName() + " - 配置编辑");
            this.project = project;

            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel panel = new JPanel();

            JLabel verLab = new JLabel("版本:");
            verLab.setBounds(10,10,80,25);
            panel.add(verLab);

            verInp = new JTextField(project.version, 20);
            verInp.setBounds(100,10,165,25);
            panel.add(verInp);

            JLabel atLab = new JLabel("AT.cfg名字(?):");
            atLab.setBounds(10,40,80,25);
            atLab.setToolTipText("AccessTransformer\n不使用留空");
            panel.add(atLab);

            atInput = new JTextField(project.atName, 20);
            atInput.setBounds(100,40,165,25);
            panel.add(atInput);

            JLabel charsetLab = new JLabel("编码:");
            charsetLab.setBounds(10,70,80,25);
            panel.add(charsetLab);

            charsetInp = new JTextField(project.charset.name(), 20);
            charsetInp.setBounds(100,70,165,25);
            panel.add(charsetInp);

            JLabel dependLab = new JLabel("前置:");
            dependLab.setBounds(10,100,80,25);
            dependLab.setToolTipText("多个用竖线(|)隔开");
            panel.add(dependLab);

            dependInp = new JTextField(project.dependencyString(), 20);
            dependInp.setBounds(100,100,165,25);
            panel.add(dependInp);

            JButton save = new JButton("保存");
            save.setBounds(40, 130, 60, 25);
            save.addActionListener(this::save);
            panel.add(save);

            JButton close = new JButton("关闭");
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

        public ConfigEdit(Project project, JFrame frame) {
            this(project);
            this.parent = frame;
            JOptionPane.showMessageDialog(this, "创建默认配置", "警告", WARNING_MESSAGE);
        }

        private void save(ActionEvent event) {
            error.clear();
            if(atInput.getText().length() > 0 && !FILE_NAME.reset(atInput.getText()).matches()) {
                error.add("AT文件名不合法");
            } else {
                project.atName = atInput.getText();
            }
            try {
                project.charset = Charset.forName(charsetInp.getText());
            } catch (Throwable e1) {
                error.add("编码不支持");
            }

            if(!FILE_NAME.reset(verInp.getText()).matches()) {
                error.add("版本不合法");
            } else {
                project.version = verInp.getText();
            }

            project.setDependencyString(dependInp.getText());

            if(error.isEmpty()) {
                project.save();

                File projPath = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + project.name);

                if(!projPath.isDirectory() && projPath.mkdirs()) {
                    ZipUtil.unzip(BASE.getAbsolutePath() + "/util/default.zip", projPath.getAbsolutePath());
                }

                if(project.getFile().equals(Shared.currentProject.getFile())) {
                    Shared.currentProject.reload();
                }
                dispose();
                if(parent != null)
                    parent.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, TextUtil.prettyPrint(error), "错误", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static class Task implements ITaskNaCl {
        IntCallable fn;

        public Task(IntCallable intSupplier) {
            fn = intSupplier;
        }

        @Override
        public boolean cancel(boolean force) {
            return fn.stop();
        }

        @Override
        public void calculate(Thread thread) {
            try {
                int code = fn.call();
                fn = null;
                frame.consoleOutput.append("程序已结束, 退出码" + code);
            } catch (Throwable e) {
                CmdUtil.warning("程序未成功完成", e);
            }
        }

        @Override
        public boolean isDone() {
            return fn == null;
        }
    }
}
