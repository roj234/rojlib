package roj.mod;

import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.SimpleLineReader;
import roj.ui.CmdUtil;

import javax.annotation.Nullable;
import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Compiler.java
 */
public class Compiler {
    public static boolean compile(List<String> optionList, List<File> fileList, @Nullable PrintStream errorOutput, @Nullable String baseLocation, @Nullable Set<String> skipErrors, boolean showErrorCode, @Nullable Processor processor) throws IOException {
        if(fileList.isEmpty()) {
            CmdUtil.warning("没有源文件!", true);
            return false;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null)
            throw new InternalError("错误：没有安装JDK!!!!");
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.CHINA, StandardCharsets.UTF_8);

        Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(fileList);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                optionList,
                null,
                compilationUnit);

        if(processor != null)
            task.setProcessors(Collections.singleton(processor));

        boolean result = false;
        try {
            result = task.call();
        } catch (ArrayIndexOutOfBoundsException e) {
            diagnostics.report(new CustomtDiagnostic("用户类文件中的class读取失败", Diagnostic.Kind.ERROR));
        }
        CmdUtil.bg(result ? CmdUtil.Color.BLUE : CmdUtil.Color.RED, false);
        CmdUtil.fg(CmdUtil.Color.WHITE, true);

        if(skipErrors == null)
            skipErrors = Collections.emptySet();
        if(baseLocation == null)
            baseLocation = "";
        if(errorOutput == null)
            errorOutput = System.out;

        EnumMap<Diagnostic.Kind, MutableInt> kinds = new EnumMap<>(Diagnostic.Kind.class);
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if(!skipErrors.contains(diagnostic.getCode()))
                errorOutput.println(buildErrorMessage(baseLocation, diagnostic, kinds));
            if(showErrorCode)
                errorOutput.println(diagnostic.getCode());
        }
        for (Map.Entry<Diagnostic.Kind, MutableInt> entry : kinds.entrySet()) {
            errorOutput.println(entry.getValue().getValue() + " 个 " + getErrorMsg(entry.getKey(), null));
        }
        CmdUtil.reset();
        return result;
    }

    private static String buildErrorMessage(String start, Diagnostic<? extends JavaFileObject> diagnostic, EnumMap<Diagnostic.Kind, MutableInt> map) throws IOException {
        StringBuilder sb = new StringBuilder();
        if(diagnostic.getSource() != null) {
            String file = diagnostic.getSource().toUri().toString();
            sb.append(file, 6, file.length()).append(':');
            if(diagnostic.getLineNumber() >= 0)
                sb.append(diagnostic.getLineNumber()).append(':');
            sb.append(' ');
        }
        sb.append(getErrorMsg(diagnostic.getKind(), map)).append(':').append(' ').append(diagnostic.getMessage(Locale.CHINA)).append('\n');
        if(diagnostic.getLineNumber() >= 0) {
            sb.append(getNearCode(diagnostic.getSource(), diagnostic.getLineNumber())).append('\n');
            for (int i = 0; i < diagnostic.getColumnNumber(); i++) {
                sb.append(' ');
            }
            sb.setCharAt(sb.length() - 1, '^');
            sb.append('\n');
        }

        return sb.toString();
    }

    private static String getNearCode(JavaFileObject source, long lineNumber) throws IOException {
        if(lineNumber == -1)
            return "";
        SimpleLineReader is = new SimpleLineReader(IOUtil.readAsUTF(source.openInputStream()), false);
        return is.get((int) lineNumber - 1);
    }

    private static String getErrorMsg(Diagnostic.Kind kind, EnumMap<Diagnostic.Kind, MutableInt> kinds) {
        switch (kind) {
            case NOTE:
                return "注";
            case ERROR:
                if(kinds != null)
                    kinds.computeIfAbsent(kind, (kind1) -> new MutableInt(0)).increment();
                return "错误";
            case OTHER:
                return "其他";
            case WARNING:
                if(kinds != null)
                    kinds.computeIfAbsent(Diagnostic.Kind.WARNING, (kind1) -> new MutableInt(0)).increment();
                return "警告";
            case MANDATORY_WARNING:
                if(kinds != null)
                    kinds.computeIfAbsent(Diagnostic.Kind.WARNING, (kind1) -> new MutableInt(0)).increment();
                return "强警告";
        }
        throw new IllegalArgumentException();
    }

    private static class CustomtDiagnostic implements Diagnostic<JavaFileObject> {
        final String msg;
        final Kind kind;

        private CustomtDiagnostic(String msg, Kind kind) {
            this.msg = msg;
            this.kind = kind;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public JavaFileObject getSource() {
            return null;
        }

        @Override
        public long getPosition() {
            return -1;
        }

        @Override
        public long getStartPosition() {
            return 0;
        }

        @Override
        public long getEndPosition() {
            return 0;
        }

        @Override
        public long getLineNumber() {
            return -1;
        }

        @Override
        public long getColumnNumber() {
            return 0;
        }

        @Override
        public String getCode() {
            return "";
        }

        @Override
        public String getMessage(Locale locale) {
            return msg;
        }
    }
}