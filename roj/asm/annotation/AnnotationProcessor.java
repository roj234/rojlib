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

package roj.asm.annotation;

import roj.asm.transform.AccessTransformer;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.util.ByteWriter;

import javax.annotation.Nullable;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ASM Common @OpenAny Processor
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 16:43
 */
public class AnnotationProcessor extends AbstractProcessor implements Runnable {
    protected static final List<ZipFile> openStreams = new ArrayList<>();
    protected static final Map<String, Object> classData = new MyHashMap<>();

    protected Messager reporter;
    protected Filer filer;

    List<String> generatedFiles = new ArrayList<>();
    protected StringBuilder atData = new StringBuilder();
    protected File atPath;

    public File getAtPath() {
        return atPath;
    }

    public static void gc() throws IOException {
        classData.clear();
        for (ZipFile zf : openStreams) {
            zf.close();
        }
        openStreams.clear();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        filer = env.getFiler();
        reporter = env.getMessager();

        Map<String, String> options = env.getOptions();
        String classPath = options.get("cp");
        String atPath = options.get("tPath");

        if (classPath == null || atPath == null)
            throw new IllegalArgumentException("RojASM @OpenAny注解处理程序 v2.0 beta\n" +
                    "参数:\n" +
                    "   -Acp=<what AT can transform>\n" +
                    "   -AtPath=<AT saving path>");

        this.atPath = new File(atPath);
        File dir = this.atPath.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalArgumentException("无法创建AT保存路径");
        }

        try {
            initZip(classPath.split(File.pathSeparator));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (hook())
            Runtime.getRuntime().addShutdownHook(new Thread(this, "PostCleaner"));
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        List<OpenAny> list = new ArrayList<>();

        Set<? extends Element> annotated = roundEnvironment.getElementsAnnotatedWith(OpenAny.class);
        annotated.forEach((element) -> list.add(element.getAnnotation(OpenAny.class)));

        annotated = roundEnvironment.getElementsAnnotatedWith(OpenAnys.class);
        annotated.forEach((element) -> Collections.addAll(list, element.getAnnotation(OpenAnys.class).value()));

        Map<String, Set<String>> openAnyData = new HashMap<>();

        processAClass(list, openAnyData);

        for (Map.Entry<String, Set<String>> entry : openAnyData.entrySet()) {
            String className = entry.getKey();
            Set<String> names = entry.getValue();

            if (!names.contains("$COMPILE_ONLY$")) {
                for (String name : names) {
                    atData.append("public-f ").append(className).append(' ').append(name).append('\n');
                }
            }

            byte[] data = getBytesAndTransform(className, names);
            if (data != null) {
                try {
                    JavaFileObject classFile = filer.createClassFile(className.replace('/', '.'));
                    generatedFiles.add(classFile.toUri().getPath());
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
            ByteWriter.encodeUTF(atData).writeToStream(fos);
        } catch (IOException ignored) {}
        return true;
    }

    public void processAClass(List<OpenAny> list, Map<String, Set<String>> openAnyDatas) {
        for (int i = 0; i < list.size(); i++) {
            OpenAny oa = list.get(i);
            String classQualifiedName = oa.value().replace('.', '/').replace(':', '/');
            Set<String> data = openAnyDatas.computeIfAbsent(classQualifiedName, (key) -> new MyHashSet<>());
            for (String s : oa.names()) {
                data.add(s);
            }
            if (oa.compileOnly()) {
                data.add("$COMPILE_ONLY$");
            }
        }
    }

    byte[] getBytesAndTransform(String fullClassName, Set<String> names) {
        Object is = classData.get(fullClassName + ".class");
        try {
            if (is instanceof InputStream) {
                byte[] code = IOUtil.read((InputStream) is);
                classData.put(fullClassName + ".class", code);
                return AccessTransformer.openSome(code, names);
            } else if (is instanceof byte[]) {
                return AccessTransformer.openSome((byte[]) is, names);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    protected void initZip(String[] list) throws IOException {
        if (classData.isEmpty())
            for (String s : list) initZip(new File(s));
    }

    protected void initZip(File file) throws IOException {
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
                on(Diagnostic.Kind.WARNING, "非UTF-8编码的ZIP文件 " + file + " 中出现了非ASCII字符, 上一个节点: " + (zn == null ? "~NULL~" : zn.getName()));
                return;
            }
            if (zn.isDirectory()) continue;
            if (!zn.getName().endsWith(".class")) continue;
            if (classData.put(zn.getName(), zf.getInputStream(zn)) != null) {
                on(Diagnostic.Kind.NOTE, "重复的类文件 " + zn.getName());
            }
        }
    }

    public void onError(@Nullable Element element, String text) {
        if (element == null) {
            reporter.printMessage(Diagnostic.Kind.ERROR, text);
        } else {
            reporter.printMessage(Diagnostic.Kind.ERROR, text, element);
        }
    }

    public void on(Diagnostic.Kind kind, String text) {
        if(reporter == null) {
            System.out.println("!警告: " + text);
        } else {
            reporter.printMessage(kind, text);
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
        for (int i = 0; i < generatedFiles.size(); i++) {
            File file = new File(generatedFiles.get(i));
            if (file.isFile() && !file.delete()) {
                System.err.println("无法删除文件 " + file);
            }
        }
        FileUtil.removeEmptyPaths(generatedFiles);
    }

    public boolean hook() {
        return true;
    }
}