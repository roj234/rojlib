/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationProcessor.java
 */
package roj.asm.annotation;

import roj.asm.transform.AccessTransformer;
import roj.collect.MyHashMap;
import roj.io.IOUtil;

import javax.annotation.Nullable;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AnnotationProcessor extends AbstractProcessor implements Runnable {
    static final List<ZipFile> openStreams = new ArrayList<>();
    static final Map<String, Object> classMap = new MyHashMap<>();

    Messager errorReporter;
    Filer filer;
    final List<String> generatedFiles = new LinkedList<>();
    String binPath;
    final StringBuilder atOutput = new StringBuilder();
    File atPath;

    public File getAtPath() {
        return atPath;
    }

    public static void closeStreams() throws IOException {
        classMap.clear();
        for (ZipFile zf : openStreams) {
            zf.close();
        }
        openStreams.clear();
    }

    public void internalInit(String atPath, String cp, String binPath, boolean keepOriginal) {
        this.binPath = binPath;
        if (!binPath.endsWith("/"))
            this.binPath = binPath + '/';

        try {
            this.atPath = new File(atPath);
            File localFile = this.atPath.getParentFile();
            if (!localFile.isDirectory() && !localFile.mkdirs()) {
                throw new IOException("无法创建AT保存路径");
            }

            initZip(cp.replace("\r", "").replace("\n", "").split(";"));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (hook())
            Runtime.getRuntime().addShutdownHook(new Thread(this, "PostCleaner"));

        if (keepOriginal) {
            try {
                atOutput.append(IOUtil.readAsUTF(new FileInputStream(this.atPath)));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        errorReporter = env.getMessager();

        if(binPath != null) {
            super.init(env);
            return;
        }

        Map<String, String> options = env.getOptions();
        String classPath = options.get("cp");
        binPath = options.get("binPath");
        String atPath = options.get("tPath");
        boolean keepOriginal = options.getOrDefault("keepOrig", "false").equalsIgnoreCase("true");

        if (classPath == null || atPath == null || binPath == null)
            throw new IllegalArgumentException("MI_ASM @OpenAny(s)注解处理程序 v1.5\n" +
                    "请指定参数通过\n" +
                    "   -Acp=<classpath>\n" +
                    "   -AbinPath=<binary path>\n" +
                    "   -AkeepOriginal=<true/false>\n" +
                    "以及\n" +
                    "   -AtPath=<AT saving path>");

        if (!binPath.endsWith("/"))
            binPath = binPath + '/';

        try {
            this.atPath = new File(atPath);
            File localFile = this.atPath.getParentFile();
            if (!localFile.isDirectory() && !localFile.mkdirs()) {
                throw new IOException("无法创建AT保存路径");
            }

            initZip(classPath.replace("\r", "").replace("\n", "").split(";"));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        super.init(env);

        if (hook())
            Runtime.getRuntime().addShutdownHook(new Thread(this, "PostCleaner"));

        if (keepOriginal) {
            try {
                atOutput.append(IOUtil.readAsUTF(new FileInputStream(this.atPath)));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        List<OpenAny> list = new ArrayList<>();

        Set<? extends Element> annotated = roundEnvironment.getElementsAnnotatedWith(OpenAny.class);
        annotated.forEach((element) -> list.add(element.getAnnotation(OpenAny.class)));

        annotated = roundEnvironment.getElementsAnnotatedWith(OpenAnys.class);
        annotated.forEach((element) -> Collections.addAll(list, element.getAnnotation(OpenAnys.class).value()));

        Map<String, Set<String>> openAnyData = new HashMap<>();

        processAnnotationData(list, openAnyData);

        for (Map.Entry<String, Set<String>> entry : openAnyData.entrySet()) {
            String className = entry.getKey();
            Set<String> names = entry.getValue();

            if (!names.contains("$COMPILE_ONLY$")) {
                for (String name : names) {
                    atOutput.append("public-f ").append(className).append(' ').append(name).append('\n');
                }
            }
            generatedFiles.add(className + ".class");

            byte[] data = getBytesAndTransform(className, names);
            if (data != null) {
                try {
                    JavaFileObject classFile = filer.createClassFile(className.replace('/', '.'));
                    OutputStream writer = classFile.openOutputStream();
                    writer.write(data);
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                onError(null, "无法找到所需的类文件 " + className);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(atPath)) {
            fos.write(atOutput.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return true;
    }

    public void processAnnotationData(List<OpenAny> list, Map<String, Set<String>> openAnyDatas) {
        for (OpenAny annotation : list) {
            String classQualifiedName = annotation.value().replace('.', '/').replace(':', '/');
            Set<String> data = openAnyDatas.computeIfAbsent(classQualifiedName, (key) -> new HashSet<>());
            Collections.addAll(data, annotation.names());
            if (annotation.compileOnly()) {
                data.add("$COMPILE_ONLY$");
            }
        }
    }

    byte[] getBytesAndTransform(String fullClassName, Set<String> names) {
        Object is = classMap.get(fullClassName + ".class");
        try {
            if (is instanceof InputStream) {
                byte[] code = IOUtil.readFully((InputStream) is);
                classMap.put(fullClassName + ".class", code);
                return AccessTransformer.openSome(code, names);
            } else if (is instanceof byte[]) {
                return AccessTransformer.openSome((byte[]) is, names);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    void initZip(String[] list) throws IOException {
        if (classMap.isEmpty())
            for (String s : list) initZip(new File(s));
    }

    void initZip(File file) throws IOException {
        if (!file.exists() || file.isDirectory() || file.length() == 0) return;
        if (file.getName().startsWith("[noread]") || !(file.getName().endsWith(".jar") || file.getName().endsWith(".zip")))
            return;
        ZipFile zf;
        try {
            zf = new ZipFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        openStreams.add(zf);

        Enumeration<? extends ZipEntry> en = zf.entries();
        ZipEntry zn = null;
        while (en.hasMoreElements()) {
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                on(Diagnostic.Kind.WARNING, null, "非UTF-8编码的ZIP文件 " + file + " 中出现了非ASCII字符, 上一个节点: " + (zn == null ? "~NULL~" : zn.getName()));
                return;
            }
            if (zn.isDirectory()) continue;
            if (!zn.getName().endsWith(".class")) continue;
            if (classMap.put(zn.getName(), zf.getInputStream(zn)) != null) {
                on(Diagnostic.Kind.NOTE, null, "重复的类文件 " + zn.getName());
            }
        }
    }

    public void onError(@Nullable Element element, String text) {
        if (element == null) {
            errorReporter.printMessage(Diagnostic.Kind.ERROR, text);
        } else {
            errorReporter.printMessage(Diagnostic.Kind.ERROR, text, element);
        }
    }

    public void on(Diagnostic.Kind kind, @Nullable Element element, String text) {
        if (element == null) {
            errorReporter.printMessage(kind, text);
        } else {
            errorReporter.printMessage(kind, text, element);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportedOptions = new HashSet<>(4);
        supportedOptions.add(OpenAny.class.getCanonicalName());
        supportedOptions.add(OpenAnys.class.getCanonicalName());
        return supportedOptions;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        for (String ss : generatedFiles) {
            File ff = new File(binPath + ss);
            if (!ff.delete())
                System.err.println("无法删除文件 " + ff);
        }
        boolean atLeast1Deleted = true;
        int i = 0;
        while (atLeast1Deleted) {
            atLeast1Deleted = false;
            for (String ss : generatedFiles) {
                File ff = new File(binPath + ss);
                while ((ff = ff.getParentFile()) != null) {
                    if (ff.isDirectory() && ff.listFiles().length == 0) {
                        if (!ff.delete())
                            System.err.println("无法删除目录 " + ff);
                        atLeast1Deleted = true;
                        i++;
                    }
                }
            }
        }
        System.out.println("删除了 " + i + " 个空目录");
    }

    public boolean hook() {
        return true;
    }
}