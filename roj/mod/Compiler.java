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

import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
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
import java.util.function.Function;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/1 1:54
 */
public class Compiler {
    public static String BASE_PATH = null;

    public static boolean compile(List<String> optionList, List<File> fileList, @Nullable PrintStream errorOutput, @Nullable Set<String> skipErrors, boolean showErrorCode, @Nullable Processor processor) {
        if(fileList.isEmpty()) {
            CmdUtil.warning("没有源文件!", true);
            return false;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null)
            throw new InternalError("请安装JDK");
        StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.CHINA, StandardCharsets.UTF_8);

        Iterable<? extends JavaFileObject> files = fm.getJavaFileObjectsFromFiles(fileList);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fm,
                diagnostics,
                optionList,
                null,
                files);

        if(processor != null)
            task.setProcessors(Collections.singleton(processor));

        boolean result = false;
        try {
            result = task.call();
        } catch (ArrayIndexOutOfBoundsException e) {
            diagnostics.report(new MyDiagnostic("用户类文件中的class读取失败", Diagnostic.Kind.ERROR));
        }
        CmdUtil.bg(result ? CmdUtil.Color.BLUE : CmdUtil.Color.RED, false);
        CmdUtil.fg(CmdUtil.Color.WHITE, true);

        if(skipErrors == null)
            skipErrors = Collections.emptySet();
        if(errorOutput == null)
            errorOutput = System.out;

        EnumMap<Diagnostic.Kind, MutableInt> kinds = new EnumMap<>(Diagnostic.Kind.class);
        for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
            if(!skipErrors.contains(diag.getCode()))
                errorOutput.println(buildErrorMessage( diag, kinds));
            if(showErrorCode)
                errorOutput.println(diag.getCode());
        }
        for (Map.Entry<Diagnostic.Kind, MutableInt> entry : kinds.entrySet()) {
            errorOutput.println(entry.getValue().getValue() + " 个 " + getErrorMsg(entry.getKey(), null));
        }
        CmdUtil.reset();
        return result;
    }

    private static String buildErrorMessage(Diagnostic<? extends JavaFileObject> diag, EnumMap<Diagnostic.Kind, MutableInt> map) {
        CharList sb = new CharList();
        if(diag.getSource() != null) {
            String file = diag.getSource().toUri().getPath();
            sb.append(file, 1, file.length() - 1).append(':');
            if(BASE_PATH != null) {
                sb.replace(BASE_PATH, "");
            }
            if(diag.getLineNumber() >= 0)
                sb.append(diag.getLineNumber()).append(':');
            sb.append(' ');
        }
        sb.append(getErrorMsg(diag.getKind(), map)).append(':').append(' ').append(diag.getMessage(Locale.CHINA)).append('\n');
        if(diag.getLineNumber() >= 0) {
            sb.append(getNearCode(diag.getSource(), diag.getLineNumber())).append('\n');
            for (int i = 0; i < diag.getColumnNumber(); i++) {
                sb.append(' ');
            }
            sb.set(sb.length() - 1, '^');
            sb.append('\n');
        }

        return sb.toString();
    }

    private static String getNearCode(JavaFileObject source, long lineNumber) {
        if(lineNumber == -1)
            return "";
        try {
            SimpleLineReader is = new SimpleLineReader(IOUtil.readUTF(source.openInputStream()), false);
            return is.get((int) lineNumber - 1);
        } catch (IOException e) {
            e.printStackTrace();
            return "<ERROR> failed to get " + source.getName() + " due to " + e;
        }
    }

    static final Function<Diagnostic.Kind, MutableInt> mutator = (kind1) -> new MutableInt(0);
    private static String getErrorMsg(Diagnostic.Kind kind, EnumMap<Diagnostic.Kind, MutableInt> kinds) {
        switch (kind) {
            case NOTE:
                return "注";
            case ERROR:
                if(kinds != null)
                    kinds.computeIfAbsent(Diagnostic.Kind.ERROR, mutator).increment();
                return "错误";
            case OTHER:
                return "其他";
            case WARNING:
                if(kinds != null)
                    kinds.computeIfAbsent(Diagnostic.Kind.WARNING, mutator).increment();
                return "警告";
            case MANDATORY_WARNING:
                if(kinds != null)
                    kinds.computeIfAbsent(Diagnostic.Kind.WARNING, mutator).increment();
                return "强警告";
        }
        throw new IllegalArgumentException();
    }

    private static class MyDiagnostic implements Diagnostic<JavaFileObject> {
        final String msg;
        final Kind kind;

        private MyDiagnostic(String msg, Kind kind) {
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