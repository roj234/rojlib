package roj.mod;

import roj.asm.annotation.AnnotationProcessor;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.pool.TaskExecutor;
import roj.concurrent.task.ITask;
import roj.concurrent.task.ITaskUncancelable;
import roj.config.data.CMapping;
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
import java.nio.charset.StandardCharsets;
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
 * This file is a part of more items mod (MI) <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * @author Asyncorized_MC
 * @since 2020/8/10
 */
public class FMDWindow extends JFrame {
    public static final int MIN_CONSOLE_OUTPUT_HEIGHT = 800;
    static final Matcher FILE_NAME;
    static {
        Pattern pattern;
        try {
            pattern = Pattern.compile("^(?!_)(?!.*?_$)[a-zA-Z0-9\\-_\\p{Script=Han}.]{2,20}$");
        } catch (PatternSyntaxException e1) {
            pattern = Pattern.compile("^(?!_)(?!.*?_$)[a-zA-Z0-9\\-_\\u4e00-\\u9fa5.]{2,20}$");
        }

        FILE_NAME = pattern.matcher("");
    }

    static FMDWindow frame;

    private final JTextArea consoleOutput;
    private final JPanel panel1;

    public FMDWindow() {
        super("FMD " + VERSION + " —— Roj234");

        try {
            File af = new File(TMP_DIR, "noFirstUse");
            if(!af.isFile() || !IOUtil.readAsUTF(new FileInputStream(af)).equals(VERSION)) {
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
        button.addActionListener(FMDWindow::build);
        button.setToolTipText("把当前项目的文件编译为发布版jar");
        panel.add(button);

        button = new JButton("调试");
        button.setToolTipText("构建,并移动结果到客户端/mods文件夹,启动客户端");
        button.addActionListener(FMDWindow::runClient);
        panel.add(button);

        button = new JButton("配置");
        button.addActionListener(FMDWindow::config);
        button.setToolTipText("配置当前项目, 选择其他项目, 新建项目");
        panel.add(button);

        button = new JButton("反射工具");
        button.addActionListener(FMDWindow::reflectTool);
        button.setToolTipText("查看MCP名到SRG名的映射表");
        panel.add(button);

        button = new JButton("重映射");
        button.addActionListener(FMDWindow::remap);
        button.setToolTipText("把Obf(SRG)版本的jar化为Dev(MCP)版本的jar(或者反过来))");
        panel.add(button);

        button = new JButton("GC");
        button.addActionListener(FMDWindow::gc);
        button.setToolTipText("清理内存并释放文件锁");
        panel.add(button);

        button = new JButton("关于");
        button.addActionListener(FMDWindow::about);
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

        pack();
        setResizable(true);
        setBounds(600, 300, 534, 400);

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
                SwingUtilities.invokeLater(FMDWindow.this::setHeight);
            }
        });
    }

    private static void about(ActionEvent event) {
        ImageIcon icon = null;
        try {
            InputStream iin = FMDWindow.class.getClassLoader().getResourceAsStream("qrcode.png");
            if(iin != null)
                icon = new ImageIcon(ImageIO.read(iin));
        } catch (IOException ignored) {}

        JOptionPane.showMessageDialog(frame,
                "Fast Mod Development 快速mod开发环境\n" +
                        "\n" +
                        "1.4.4\n" +
                        "此版本主要修复了辣鸡forge瞎换地址导致下载太慢的BUG\n" +
                        "以及一些class的映射问题\n" +
                        "\n" +
                        "自我开始写mod,我就在抱怨gradle是如此之差, 速度慢, 要联网(这个倒能解决), 不能增量等等...\n" +
                        "为此, 我在2018年制作了[预编译模块], 因为我当时还在用记事本写代码, 无法实时看到错误.\n" +
                        "而后, 随着编程水平的进步, 我又在2020年1月制作了重映射器, 它可以基于映射表(要从gra的数据中提取)将class文件的方法和字段名称进行转换.\n" +
                        "在它出现没多久, 我就意识到, 为啥不能自己做个工具呢, 我想着,这么多年都没人做, 也许很难吧.\n" +
                        "然而, 现实就是这么狗血, 外国并没人意识到网速的重要性.\n" +
                        "\n" +
                        "自制JSONParser, 自制ASM, 以及由它们衍生的MCLauncher模块, 虽然不支持登陆, 但有了一个盗版启动器的所有功能\n" +
                        "多线程下载用一个HTTP头完美解决, RandomAccessFile助力断点续传\n" +
                        "\n" +
                        "这还不是终点, RemapperV1已被删除, V2在1.4.4又重写了代码, 获得了 6 倍SpecialSource的速度\n" +
                        "千万不要和SpecialSource比速度, 否则md_5会哭的\n" +
                        "ASM在更新中支持了泛型解析, 方法体注解读取, 删除了一些用不到的字段\n" +
                        "Renamer 修改class文件中类的名字和参数名, 使得FMD支持多重映射来源\n" +
                        "Lexer将行号等的计算移动至异常抛出时, 而不是读取字符时\n" +
                        "\n" +
                        "如今, 她已经支持了多重映射表, 你还可以手动制作, 或者修改, 你觉得这个方法名字不好? 盘它!\n" +
                        "如今, 她已经支持了下载MC并补Lib, 还能安装Forge, 在一些不会下载挖矿与合成和安装锻造锤与国防部的玩家看来, 这无疑是个福音.\n" +
                        "如今, 她绝大部分cmd界面都有了对应的GUI, 某人终于不会吐槽我用的是叫[windows]的linux了. @_@\n" +
                        "如今, 她还可以有事没事启动个MC, 抢了启动器的一点点饭碗, 让你不调试也可以玩MC (我真的觉得没啥用 orz).\n" +
                        "\n" +
                        "我(嗯, 就我一个呢, 虽然觉得这里放'我们'更带感的说), 仍在前行\n" +
                        "\n" +
                        "2021/9 就高三了, 马上就没时间写代码了, 1.4.2版本修复了许多映射器的BUG(没想到啊, javac居然也能违反java规范)祝你们别碰到bug\n" +
                        "\n有空的话, 会做一下1.16版本补丁问题的修复, 不过优先级并不高...", "关于Fast Mod Development " + VERSION, INFORMATION_MESSAGE, icon);
    }

    private static void gc(ActionEvent event) {
        long free = Runtime.getRuntime().freeMemory();
        try {
            AnnotationProcessor.closeStreams();
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

        frame = new FMDWindow();

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
        Config defaultConfig = new Config(new File(BASE, "/config/default.json"));
        config = defaultConfig;
        JFrame frame1 = new ConfigEditOrCreate(defaultConfig, frame);
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(false);
    }

    private static void config(ActionEvent event) {
        String[] availableValue = new String [] {
                "编辑",
                "选择",
                "新建"
        };
        final Config config = Shared.config;
        switch (JOptionPane.showOptionDialog(frame, "已选择 " + config.getFile().getAbsolutePath().replace(BASE.getAbsolutePath(), ""), "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
            case YES_OPTION:
                new ConfigEditOrCreate(config);
                break;
            case NO_OPTION: {
                JFileChooser fileChooser = new JFileChooser(Shared.PROJ_CONF_DIR);
                fileChooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isFile() && f.getName().endsWith(".json") && !f.getName().equals("mc.json") && !f.getName().equals("index.json");
                    }

                    @Override
                    public String getDescription() {
                        return "项目配置";
                    }
                });

                if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File selected = fileChooser.getSelectedFile();

                    CMapping map = new CMapping();
                    map.put("config", selected.getAbsolutePath());
                    try (FileOutputStream fos = new FileOutputStream(CONF_INDEX)) {
                        fos.write(map.toJSON().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Shared.config = new Config(selected);

                    frame.consoleOutput.setText("已选择 " + selected.getAbsolutePath());
                }
            }
            break;
            case CANCEL_OPTION: {
                String fileName;
                do {
                    fileName = JOptionPane.showInputDialog(frame, "请输入文件名(不需要.json)", "输入", QUESTION_MESSAGE);
                    if(fileName == null)
                        return;
                    if(FILE_NAME.reset(fileName).matches() && !fileName.equals("index") && !fileName.equals("mc")) {
                        break;
                    }
                    JOptionPane.showMessageDialog(frame, "无效的文件名!", "警告", WARNING_MESSAGE);
                } while(true);
                new ConfigEditOrCreate(new Config(new File(BASE, "config/" + fileName + ".json")));
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

        asyncRun(() -> ModDevelopment.srg2mcp(args));
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
        new ReflectToolWindow(false, null);
    }

    private static void runClient(ActionEvent event) {
        Map<String, String> map = getCompileSetting();
        if(map == null)
            return;
        asyncRun(() -> ModDevelopment.runClient(map));
    }

    private static void build(ActionEvent event) {
        Map<String, String> map = getCompileSetting();
        if(map == null)
            return;

        asyncRun(() -> ModDevelopment.build(map));
    }

    private static Map<String, String> getCompileSetting() {
        frame.consoleOutput.setText("");
        Map<String, String> map = new MyHashMap<>(2, 10);

        String[] availableValue = new String [] {
                "增量编译",
                "全量编译",
                "全量且清空编译缓存"
        };
        switch (JOptionPane.showOptionDialog(frame, "请选择 ", "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, availableValue, availableValue[0])) {
            case YES_OPTION:
                map.put("zl", null);
                break;
            case NO_OPTION:
                break;
            case CANCEL_OPTION:
                map.put("clearBin", null);
                break;
            case CLOSED_OPTION:
                return null;
        }
        return map;
    }

    private static class ConfigEditOrCreate extends JFrame {
        private final Config config;
        private final JTextField projectInput, versionInput, atInput, charsetInput, requireInput;
        private final Set<String> error = new MyHashSet<>();

        private JFrame parent;

        public ConfigEditOrCreate(Config config) {
            super(config.getFile().getName() + " - 配置窗口");
            this.config = config;

            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel panel = new JPanel();

            JLabel projectLabel = new JLabel("项目名字:");
            projectLabel.setBounds(10,10,80,25);
            panel.add(projectLabel);

            projectInput = new JTextField(config.currentProject, 30);
            projectInput.setBounds(100,10,165,25);
            panel.add(projectInput);

            JLabel versionLabel = new JLabel("版本:");
            versionLabel.setBounds(10,40,80,25);
            panel.add(versionLabel);

            versionInput = new JTextField(config.currentVersion, 20);
            versionInput.setBounds(100,40,165,25);
            panel.add(versionInput);

            JLabel atLabel = new JLabel("AT名字(?):");
            atLabel.setBounds(10,70,80,25);
            atLabel.setToolTipText("关闭请留空!");
            panel.add(atLabel);

            atInput = new JTextField(config.at ? config.atName : "", 20);
            atInput.setBounds(100,70,165,25);
            panel.add(atInput);

            JLabel charsetLabel = new JLabel("字符集:");
            charsetLabel.setBounds(10,100,80,25);
            panel.add(charsetLabel);

            charsetInput = new JTextField(config.charset, 20);
            charsetInput.setBounds(100,100,165,25);
            panel.add(charsetInput);

            JLabel requireLabel = new JLabel("前置:");
            requireLabel.setBounds(10,130,80,25);
            panel.add(requireLabel);

            requireInput = new JTextField(config.required, 20);
            requireInput.setBounds(100,130,165,25);
            panel.add(requireInput);

            JButton save = new JButton("保存");
            save.setBounds(40, 160, 60, 25);
            save.addActionListener(this::save);
            panel.add(save);

            JButton cancel = new JButton("关闭");
            cancel.setBounds(180, 160, 60, 25);
            cancel.addActionListener((e) -> {
                if(parent != null) {
                    System.exit(-1);
                } else {
                    ConfigEditOrCreate.this.dispose();
                }
            });
            panel.add(cancel);

            panel.setLayout(null);
            panel.setBounds(400, 200, 300, 220);
            add(panel);

            pack();
            setVisible(true);
            setResizable(false);
            setBounds(400, 200, 300, 220);
            validate();
        }

        public ConfigEditOrCreate(Config config, JFrame frame) {
            this(config);
            this.parent = frame;
            JOptionPane.showMessageDialog(this, "没有配置文件! 请创建默认配置", "警告", WARNING_MESSAGE);
        }

        private void save(ActionEvent event) {
            error.clear();
            config.at = atInput.getText().length() != 0;
            if(config.at && !FILE_NAME.reset(atInput.getText()).matches()) {
                error.add("AT文件名不合法");
            } else {
                config.atName = atInput.getText();
            }
            try {
                Charset.forName(charsetInput.getText());
                config.charset = charsetInput.getText();
            } catch (Throwable e1) {
                error.add("字符集不存在");
            }

            if(!FILE_NAME.reset(versionInput.getText()).matches()) {
                error.add("版本不合法");
            } else {
                config.currentVersion = versionInput.getText();
            }

            if(!FILE_NAME.reset(projectInput.getText()).matches()) {
                error.add("项目名不合法");
            } else {
                config.currentProject = projectInput.getText();
            }

            if(requireInput.getText().length() != 0 && !FILE_NAME.reset(requireInput.getText()).matches()) {
                error.add("前置不合法");
            } else {
                config.required = requireInput.getText();
            }

            if(error.isEmpty()) {
                config.save();

                File projPath = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + config.currentProject);

                if(!projPath.isDirectory() && projPath.mkdirs()) {
                    ZipUtil.unzip(BASE.getAbsolutePath() + "/util/defaultProject.zip", projPath.getAbsolutePath() + '/');
                }

                if(config.getFile().equals(Shared.config.getFile())) {
                    Shared.config.reload();
                }
                this.dispose();
                if(parent != null)
                    parent.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, TextUtil.prettyPrint(error), "错误", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static class Task implements ITaskUncancelable {
        IntCallable intCallable;

        public Task(IntCallable intSupplier) {
            intCallable = intSupplier;
        }

        @Override
        public boolean cancel(boolean force) {
            return intCallable.stop();
        }

        @Override
        public void calculate(Thread thread) {
            try {
                int code = intCallable.call();
                intCallable = null;
                frame.consoleOutput.append("程序已结束, 退出码" + code);
            } catch (Throwable e) {
                CmdUtil.warning("程序未成功完成", e);
            }
        }

        @Override
        public boolean isDone() {
            return intCallable == null;
        }
    }
}
