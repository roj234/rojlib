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
package roj.dev;

import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.ui.CmdUtil;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/6/1 1:54
 */
public class Compiler implements DiagnosticListener<JavaFileObject> {
    private static boolean showErrorCode;

    private static final JavaCompiler compiler;
    static {
        compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null)
            throw new InternalError("请安装JDK");
    }

    public static void showErrorCode(boolean show) {
        showErrorCode = show;
    }

    private final String basePath;
    private final PrintStream out;
    private final Set<String> skipErrors;
    private final List<Processor> processor;
    private int warnings, errors;
    private final CharList buf;
    private List<ByteListOutput> compiled;

    public Compiler() {
        this(null, null, null, "");
    }

    public Compiler(PrintStream out) {
        this(null, out, null, "");
    }

    public Compiler(PrintStream out, Set<String> skipErrors) {
        this(null, out, skipErrors, "");
    }

    public Compiler(List<Processor> processor, PrintStream out, Set<String> skipErrors, String basePath) {
        this.processor = processor;
        this.out = out == null ? System.out : out;
        this.skipErrors = skipErrors == null ? Collections.emptySet() : skipErrors;
        this.basePath = basePath;
        this.buf = new CharList(1024);
    }

    public synchronized boolean compile(List<String> options, List<File> files) {
        if(files.isEmpty()) {
            return true;
        }

        warnings = 0;
        errors = 0;
        buf.clear();

        DelegatedSFM fm = new DelegatedSFM(compiler.getStandardFileManager(
                this,
                Locale.getDefault(),
                StandardCharsets.UTF_8), basePath);
        compiled = fm.getOutputs();

        Iterable<? extends JavaFileObject> unit = fm.getJavaFileObjectsFromFiles(files);
        JavaCompiler.CompilationTask task = compiler.getTask(
                new OutputStreamWriter(out),
                fm,
                this,
                options,
                null,
                unit);

        if(processor != null)
            task.setProcessors(processor);

        boolean result = false;
        try {
            result = task.call();
        } catch (ArrayIndexOutOfBoundsException e) {
            this.report(new MyDiagnostic("用户类文件中的class读取失败", Diagnostic.Kind.ERROR));
        }

        CmdUtil.bg(result ? CmdUtil.Color.BLUE : CmdUtil.Color.RED, false);
        CmdUtil.fg(CmdUtil.Color.WHITE, true);
        System.out.println(buf);

        if(errors > 0) out.println(errors + " 个 错误");
        if(warnings > 0) out.println(warnings + " 个 警告");
        CmdUtil.reset();
        return result;
    }

    public List<ByteListOutput> getCompiled() {
        return compiled;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diag) {
        if(!skipErrors.contains(diag.getCode())) {
            CharList sb = buf;
            if(diag.getSource() != null) {
                String file = diag.getSource().toUri().getPath();
                int off = 1;
                if(file.startsWith(basePath, 1)) {
                    off += basePath.length() + 1;
                }
                sb.append(file, off, file.length()).append(':');
                if(diag.getLineNumber() >= 0)
                    sb.append(diag.getLineNumber()).append(':');
                sb.append(' ');
            }
            sb.append(errorMsg(diag.getKind())).append(':').append(' ').append(diag.getMessage(Locale.CHINA)).append('\n');
            if(diag.getLineNumber() >= 0) {
                sb.append(getNearCode(diag.getSource(), diag.getLineNumber())).append('\n');
                for (int i = 0; i < diag.getColumnNumber(); i++) {
                    sb.append(' ');
                }
                sb.set(sb.length() - 1, '^');
                sb.append('\n');
            }
            if(showErrorCode)
                out.println(diag.getCode());
        }
    }

    private static String getNearCode(JavaFileObject source, long lineNumber) {
        if(lineNumber == -1)
            return "";
        try {
            return SimpleLineReader.readSingleLine(IOUtil.readUTF(source.openInputStream()), (int) lineNumber);
        } catch (IOException e) {
            e.printStackTrace();
            return "<ERROR> failed to get " + source.getName() + " due to " + e;
        }
    }

    private String errorMsg(Diagnostic.Kind kind) {
        switch (kind) {
            case NOTE:
                return "注";
            case ERROR:
                errors++;
                return "错误";
            case OTHER:
                return "其他";
            case WARNING:
                warnings++;
                return "警告";
            case MANDATORY_WARNING:
                warnings++;
                return "强警告";
        }
        return "";
    }
}