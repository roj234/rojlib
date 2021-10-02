package roj.mod;

import roj.asm.mapper.ConstMapper;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.task.AbstractExecutionTask;
import roj.config.JSONConfiguration;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.CString;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.mod.compiler.Compiler;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.mod.Shared.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/11 13:59
 */
public final class Project extends JSONConfiguration {
    static final MyHashMap<String, Project> projects = new MyHashMap<>();
    static final Matcher matcher = Pattern.compile("^[a-z_][a-z0-9_]*$").matcher("");

    public static Project load(String name) {
        if(!matcher.reset(name).matches())
            throw new IllegalArgumentException("名称必须为全小写,不能以数字开头,可以包含下划线 ^[a-z_][a-z0-9_]*$");
        Project project = projects.get(name);
        if(project == null)
            projects.put(name, project = new Project(name));
        else
            project.reload();
        return project;
    }

    final String name;
    String version, atName;
    Charset charset;
    List<Project> dependencies;
    final Compiler compiler;

    ConstMapper.State state;
    String atConfigPathStr;
    File source, resource, stamp;

    static FileFilter resourceFilter = new FileFilter();
    MyHashMap<String, byte[]> resourceCache = new MyHashMap<>(100);

    MutableZipFile dstZip, stampZip;

    private Project(String name) {
        super(new File(BASE, "config/" + name + ".json"), false);
        this.name = name;

        String abs = BASE.getAbsolutePath();
        resource = new File(abs + File.separatorChar + "projects" + File.separatorChar + name + File.separatorChar + "resources" + File.separatorChar);
        source = new File(abs + File.separatorChar + "projects" + File.separatorChar + name + File.separatorChar + "java" + File.separatorChar);
        stamp = new File(abs + File.separatorChar + "bin" + File.separatorChar + name + "-src.jar");

        Set<String> ignores = new MyHashSet<>();
        FMDMain.readTextList(ignores::add, "忽略的编译错误码");
        this.compiler = new Compiler(null, null, ignores, source.getAbsolutePath().replace(File.separatorChar, '/'));

        try {
            this.stampZip = new MutableZipFile(stamp);
            if(stamp.length() == 0)
                if(!stamp.setLastModified(0))
                    CmdUtil.warning("无法初始化stampFileTime");
            // 防止有别的进程修改，而本进程又可以修改
            FileLock lock = stampZip.getFile().getChannel().tryLock(0, 0, true);
            if(null == lock) {
                CmdUtil.warning("无法初始化stampFileLock");
            }
        } catch (IOException e) {
            CmdUtil.warning("无法初始化stampFileZip, 请尝试重新启动FMD, 若无效, 请删除 " + stamp.getAbsolutePath(), e);
            if(stampZip != null) {
                try {
                    stampZip.close();
                } catch (IOException ignored) {}
            }
            if(!stamp.delete())
                stamp.deleteOnExit();
            LockSupport.parkNanos(5_000_000_000L);
            System.exit(-2);
        }

        init();
    }

    public List<Project> getAllDependencies() {
        if(dependencies.isEmpty())
            return dependencies;

        LinkedMyHashMap<Project, Void> projects = new LinkedMyHashMap<>();

        List<Project> dest = new ArrayList<>(this.dependencies);
        List<Project> dest2 = new ArrayList<>();
        while (!dest.isEmpty()) {
            for (int i = 0; i < dest.size(); i++) {
                projects.put(dest.get(i), null);
                dest2.addAll(dest.get(i).dependencies);
            }
            List<Project> tmp = dest;
            dest = dest2;
            dest2 = tmp;
            dest2.clear();
        }

        for (Map.Entry<Project, Void> entry : projects.entrySet()) {
            dest2.add(entry.getKey());
        }

        return dest2;
    }

    public String dependencyString() {
        CharList cl = new CharList();
        if(!dependencies.isEmpty()) {
            for (int i = 0; i < dependencies.size(); i++) {
                cl.append(dependencies.get(i).name).append('|');
            }
            cl.setIndex(cl.length() - 1);
        }
        return cl.toString();
    }

    public void setDependencyString(String text) {
        List<String> depend = TextUtil.split(new ArrayList<>(), text, '|');
        for (int i = 0; i < depend.size(); i++) {
            dependencies.add(Project.load(depend.get(i)));
        }
    }

    protected void readConfig(CMapping map) {
        version = map.putIfAbsent("version", "1.0.0");

        String cs = map.putIfAbsent("charset", "UTF-8");
        charset = StandardCharsets.UTF_8;
        try {
            charset = Charset.forName(cs);
        } catch (UnsupportedCharsetException e) {
            CmdUtil.warning(name + " 的字符集不存在");
        }

        String atName = this.atName = map.putIfAbsent("atConfig", "");

        atConfigPathStr = atName.length() > 0 ? resource.getPath() + File.separatorChar + "META-INF" + File.separatorChar + atName + ".cfg" : null;

        List<String> required = map.getOrCreateList("dependency").asStringList();
        if(!required.isEmpty()) {
            for (int i = 0; i < required.size(); i++) {
                File config = new File(BASE, "/config/" + required.get(i) + ".json");
                if (!config.exists()) {
                    CmdUtil.warning(name + " 的前置" + required.get(i) + "未找到");
                } else {
                    required.set(i, Helpers.cast(load(required.get(i))));
                }
            }
            dependencies = Helpers.cast(required);
        } else {
            dependencies = Collections.emptyList();
        }
    }

    public AbstractExecutionTask getResourceTask() {
        return new AbstractExecutionTask() {
            @Override
            public void run() {
                initForwardMapper();

                MyHashSet<String> set = watcher.getModified(Project.this, ProjectWatcher.ID_RES);
                if(!resourceCache.isEmpty() && !set.contains(null)) {
                    int len = resource.getAbsolutePath().length();
                    for (String s : set) {
                        if(new File(s).isDirectory()) continue;
                        try {
                            resourceCache.put(s.substring(len).replace('\\', '/'), IOUtil.read(new FileInputStream(s)));
                        } catch (IOException e) {
                            Helpers.throwAny(e);
                        }
                    }
                } else {
                    resourceCache.clear();

                    FileUtil.findAndOpenStream(resource, Helpers.cast(resourceCache), resourceFilter);

                    Set<Map.Entry<String, Object>> entrySet = Helpers.cast(resourceCache.entrySet());

                    for (Map.Entry<String, Object> entry : entrySet) {
                        try {
                            entry.setValue(IOUtil.read((InputStream) entry.getValue()));
                        } catch (IOException e) {
                            Helpers.throwAny(e);
                        }
                    }
                }
            }
        };
    }

    public void registerWatcher() {
        try {
            Shared.watcher.register(this);
        } catch (IOException e) {
            CmdUtil.warning("无法启动文件监控", e);
        }
    }

    @Override
    protected void saveConfig(CMapping map) {
        map.put("charset", charset == null ? "UTF-8" : charset.name());
        map.put("version", version);
        map.put("atConfig", atName);

        CList list = new CList(dependencies.size());
        for (int i = 0; i < dependencies.size(); i++) {
            list.add(CString.valueOf(dependencies.get(i).name));
        }
        map.put("dependency", list);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return name.equals(project.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
