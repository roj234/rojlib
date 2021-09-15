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
package roj.kscript.parser;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.collect.MyHashMap;
import roj.collect.ReuseStack;
import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.kscript.api.ErrorHandler;
import roj.kscript.ast.*;
import roj.kscript.func.KFunction;
import roj.kscript.parser.expr.Binary;
import roj.kscript.parser.expr.ExprParser;
import roj.kscript.parser.expr.Expression;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LabelInfo;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.Variable;
import roj.kscript.vm.KScriptVM;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class KParser implements ParseContext {
    /**
     * todo 常量（自定义来没有修改过的变量）传播， 删除未使用的(var)变量
     */

    /**
     * 分词器
     */
    JSLexer wr;
    /**
     * 语法树
     */
    ASTree tree;
    /**
     * 文件名
     */
    String file;

    /**
     * 作用域
     */
    ContextPrimer ctx;

    /**
     * 缓存索引
     */
    private final int depth;

    /**
     * 错误处理器
     */
    private ErrorHandler handler;

    /**
     * 严格模式
     */
    boolean strict;

    public void setErrorHandler(ErrorHandler handler) {
        if(handler == null)
            throw new NullPointerException("handler");
        this.handler = handler;
    }

    public KParser(ContextPrimer ctx, ErrorHandler handler) {
        this.ctx = ctx.makeChild();
        this.depth = -1;
        this.handler = handler;
        this.wr = new JSLexer();
    }

    public KParser(ContextPrimer ctx) {
        this(ctx, (type, file, e) -> {
            System.err.print(type + ": " + file + "   ");
            e.printStackTrace();
        });
    }

    @Internal
    public KParser(int depth) {
        this.depth = depth;
    }

    @Internal
    public KParser reset(KParser parent) {
        if(parent != null) {
            this.file = parent.file;
            this.ctx = parent.ctx.makeChild();
            ctx.setParameter(this.parameters);
            this.wr = parent.wr;

            this.handler = parent.handler;
            this.strict = parent.strict;
        }

        this.success = true;

        this.parameters.clear();
        this.sectionFlag = 0;

        this.namedLabels.clear();
        this.labelTmp = null;
        this.labelPath = 0;
        this.labels.clear();

        this.tryLevel = 0;
        this.maxTryLevel = 0;
        this.catchTryLevel = 0;
        this.maxCatchTryLevel = 0;

        return this;
    }

    /// region 解析

    public KFunction parse(File file) throws IOException, ParseException {
        wr.init(IOUtil.read(file));
        this.file = file.getName();
        return parse0();
    }

    public KFunction parse(String file, CharSequence text) throws ParseException {
        wr.init(text);
        this.file = file;
        return parse0();
    }

    private KFunction parse0() throws ParseException {
        reset(null);

        tree = ASTree.builder(file);
        wr.setLineHandler(tree);

        Word w;
        try {
            while (true) {
                w = wr.nextWord();
                if (w.type() == WordPresets.EOF) {
                    break;
                } else {
                    statement(w);
                }
            }
        } catch (NotStatementException e) {
            _onError(wr.err("statement.not"));
        }

        //_checkDel();

        _checkUnused();

        if (success) {
            return checkAndBuild();
        } else {
            throw wr.err("compile_error_occurs");
        }
    }

    private KFunction checkAndBuild() {
        if(maxTryLevel > 65535 || maxCatchTryLevel > 65535)
            throw new IllegalArgumentException("What fucking code you write???");
        return tree.build(ctx, (maxTryLevel << 16) | maxCatchTryLevel);
    }

    @Override
    public KFunction parseInnerFunc(short type) throws ParseException {
        KFunction fn = KScriptVM.retainScriptParser(depth + 1, this).parseInner(tree);
        wr.setLineHandler(tree);

        return fn;
    }

    private KFunction parseInner(ASTree parent) throws ParseException {
        tree = ASTree.builder(parent);
        wr.setLineHandler(tree);

        functionParser();

        return success ? checkAndBuild() : null;
    }

    //endregion
    //region 函数

    private void functionParser() throws ParseException {
        String name;
        Word w = wr.nextWord();
        switch(w.type()) {
            case WordPresets.LITERAL:  // named
                name = w.val();
                break;
            case Symbol.left_s_bracket: // anonymous
                name = null;
                break;
            default:
                _onError(w, "unexpected:" + w.val() + ":nob");
                wr.retractWord();
                return;
        }
        wr.recycle(w);

        tree.funcName(name);

        if(name != null)
            except(Symbol.left_s_bracket);
        parameters();
        except(Symbol.right_s_bracket);

        except(Symbol.left_l_bracket);
        body();
        except(Symbol.right_l_bracket);

        _checkUnused();
    }

    private void _checkUnused() throws ParseException {
        wr.retractWord();
        Word wd = wr.nextWord();
        for (int i = 0; i < ctx.locals.size(); i++) {
            Variable v = ctx.locals.get(i);
            if(v.end == null)
                _onWarning(wd, "unused.let:" + v.name);
        }
        for (String s : ctx.unusedGlobal) {
            _onWarning(wd, "unused.var:" + s);
        }
    }

    /**
     * 当前方法的参数
     */
    ToIntMap<String> parameters = new ToIntMap<>();

    /**
     * Unreachable statement检测 <BR>
     *     bit1 <BR>
     *     bit2 <BR>
     *     bit3 <BR>
     */
    byte sectionFlag;

    /// 参数
    private void parameters() throws ParseException {
        parameters.clear();

        Word word;

        int i = 0;
        int flag = 0;

        o:
        while (true) {
            word = wr.nextWord();
            switch (word.type()) {
                case Symbol.right_s_bracket:
                    wr.retractWord();
                    break o;
                case Symbol.equ:
                    if ((flag & 4) != 0)
                        _onError(word, "rest.unable_default");
                    if ((flag & 8) != 0)
                        _onError(word, "duplicate:=");
                    flag |= 8;
                    Expression def = KScriptVM.retainExprParser(0).read(this, (short) 16);
                    if(def == null) {
                        _onError(word = wr.readWord(), "unexpected:" + word.val() + ":type.expr");
                        wr.retractWord();
                    } else if(!def.isConstant()) {
                        _onWarning(wr.readWord(), "default_prefers_constant");
                        wr.retractWord();
                    }
                    ctx.setDefault(i - 1, def);
                    break;
                case Symbol.rest:
                    if ((flag & 2) != 0)
                        _onError(word, "duplicate:...");
                    flag |= 2;
                    break;
                case WordPresets.LITERAL:
                    if ((flag & 1) != 0)
                        _onError(word, "missing:,");
                    else if((flag & 4) != 0)
                        _onError(word, "rest.last_formal_parameter");
                    else if((flag & 2) != 0) {
                        flag |= 4;
                        ctx.setRestId(i);
                    }
                    parameters.put(word.val(), i++);
                    flag |= 1;
                    break;
                case Symbol.comma:
                    if ((flag & 1) == 0) {
                        _onError(word, "unexpected:,:type.literal");
                    }
                    flag &= ~9;
                    break;
            }
        }
    }

    /**
     * 函数体
     */
    @SuppressWarnings("fallthrough")
    private void body() throws ParseException {
        sectionFlag |= 1;

        ctx.enterRegion();

        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case WordPresets.EOF:
                case Symbol.right_l_bracket:
                    wr.retractWord();
                    break o;
                default:
                    wr.recycle(w);
                    statement(w);
            }
        }

        ctx.endRegion();
    }

    /**
     * 语句
     */
    @SuppressWarnings("fallthrough")
    private void statement(Word w) {
        if (labelTmp != null && labelPath-- == 0) {
            labelTmp = null;
        }

        try {
            switch (w.type()) {
                case Keyword.CASE:
                case Keyword.DEFAULT:
                    if((sectionFlag & 4) != 0) {
                        wr.retractWord();
                    } else {
                        _onError(w, "statement.not");
                    }
                    return;
                case Keyword.VAR:
                case Keyword.CONST:
                case Keyword.LET:
                    define(w.type());
                    break;
                case Keyword.CONTINUE:
                case Keyword.BREAK:
                    _break(w.type() == Keyword.BREAK);
                    break;
                case Keyword.GOTO:
                    _goto();
                    break;
                case Keyword.SWITCH:
                    _switch();
                    break;
                case Keyword.RETURN:
                    _return();
                    break;
                case Keyword.FOR:
                    _for();
                    break;
                case Keyword.WHILE:
                    _while();
                    break;
                case WordPresets.LITERAL:
                    wr.retractWord();
                    assign();
                    break;
                case Keyword.THROW:
                    _throw();
                    break;
                case Keyword.IF:
                    _if();
                    break;
                case Keyword.DO:
                    _do();
                    break;
                case Keyword.FUNCTION:
                    _func();
                    break;
                case Keyword.TRY:
                    _try();
                    break;
                case Symbol.semicolon:
                    _onWarning(w, "statement.empty");
                    break;
                case Symbol.left_l_bracket:
                    body();
                    except(Symbol.right_l_bracket);
                    break;
                default:
                    boolean flag = Symbol.is(w);
                    if(!flag) {
                        switch(w.type()) {
                            case Keyword.NEW:
                            case Keyword.NULL:
                            case Keyword.UNDEFINED:
                            case Keyword.THIS:
                            case Keyword.ARGUMENTS:
                            case Keyword.DELETE:
                                flag = true;
                        }
                    }

                    if (flag) {
                        wr.retractWord();
                        Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
                        if (expr != null) {
                            expr.write(tree, true);
                            except(Symbol.semicolon);
                        }
                    } else {
                        _onError(w, "statement.not");
                    }
                    break;
            }
        } catch (ParseException e) {
            _onError(e);
        }
    }

    /**
     * 返回
     */
    private void _return() throws ParseException {
        Word w = wr.nextWord();
        if(depth == -1) {
            _onError(w, "return.on_top");
        }

        if (w.type() == Symbol.semicolon) {
            tree.Std(Opcode.RETURN_EMPTY);
        } else {
            wr.retractWord();
            Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
            if (expr == null) {
                _onError(w, "return.unexpected");
                return;
            }

            expr.write(tree, false);
            tree.Std(Opcode.RETURN);

            except(Symbol.semicolon);
        }

        _chkBlockEnd();
    }

    /**
     * 内部函数
     */
    private void _func() throws ParseException {
        String name;
        Word w = wr.nextWord();
        if (w.type() != WordPresets.LITERAL) {
            // no anonymous not in expr
            _onError(w, "unexpected:" + w.val() + ":type.literal");
            wr.retractWord();
            return;
        } else {
            name = w.val();
        }
        wr.retractWord();
        wr.recycle(w);

        KFunction fn = KScriptVM.retainScriptParser(depth + 1, this).parseInner(tree);

        wr.setLineHandler(tree);

        if (fn == null) {
            success = false;
            // 无效的函数
            return;
        }

        // 定义函数, 整个地方都可以用
        if(ctx.selfExists(name)) {
            tree.Load(fn).Set(name);
        } else {
            ctx.global(name, fn);
            ctx.unusedGlobal.remove(name);
        }
    }

    // endregion
    // region 条件

    private final ReuseStack<Map<String, LabelInfo>> namedLabelChunk = new ReuseStack<>();
    private final MyHashMap<String, LabelInfo> namedLabels = new MyHashMap<>();
    private final ReuseStack<LabelInfo> labels = new ReuseStack<>();

    // 支持在for while do switch上放的label
    private LabelInfo labelTmp;
    private byte labelPath = 0;

    private void _namedLabel(String val) {
        namedLabels.put(val, labelTmp = new LabelInfo(tree.Label()));
        labelPath = 1;
    }

    private int tryLevel, maxTryLevel, catchTryLevel, maxCatchTryLevel;

    private void _try() throws ParseException {
        except(Symbol.left_l_bracket);

        LabelNode catchTo = new LabelNode(),
                finallyTo = new LabelNode(),
                end = new LabelNode();

        TryEnterNode try_id = tree.TryEnter(catchTo, finallyTo, end);

        maxTryLevel = Math.max(maxTryLevel, ++tryLevel);

        body();

        tryLevel--;

        except(Symbol.right_l_bracket);

        TryNormalNode normal = new TryNormalNode();
        tree.node0(normal);

        byte flag = 0;

        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Keyword.FINALLY:
                    if ((flag & 1) != 0)
                        throw wr.err("duplicate:finally");
                    tree.node0(finallyTo);
                    except(Symbol.left_l_bracket);
                    body();
                    except(Symbol.right_l_bracket);

                    // END ?
                    tree.TryRegionEnd(end);

                    flag |= 1;
                break;
                case Keyword.CATCH:
                    if ((flag & 2) != 0)
                        throw wr.err("duplicate:catch");

                    w = wr.nextWord();
                    boolean hasVar = false;
                    switch (w.type()) {
                        case Symbol.left_s_bracket: // (
                            w = wr.nextWord();
                            if (w.type() != WordPresets.LITERAL)
                                throw wr.err("unexpected:" + w);
                            except(Symbol.right_s_bracket);
                            except(Symbol.left_l_bracket);

                            hasVar = true;
                            break;
                        case Symbol.left_l_bracket: // {
                            break;
                        default:
                            _onError(w, "unexpected:" + w.val() + ':' + Symbol.byId(Symbol.left_s_bracket));
                            wr.retractWord();
                            return;
                    }

                    tree.node0(catchTo);

                    if (hasVar) {
                        ctx.local(w.val(), null, catchTo); // 定义 catch(e)
                        // end try | e = pop();

                        tree.Set(w.val());
                    } else {
                        tree.Std(Opcode.POP);
                    }

                    maxCatchTryLevel = Math.max(maxCatchTryLevel, ++catchTryLevel);

                    body();
                    except(Symbol.right_l_bracket);

                    catchTryLevel--;

                    // END ?
                    tree.TryRegionEnd(end);

                    flag |= 2;
                break;
                default:
                    wr.retractWord();
                    break o;
            }
        }

        normal.setTarget((normal.gotoFinal = (flag & 1) != 0) ? finallyTo : end);

        if (flag == 0) {
            // 孤立的try
            _onError(w, "try.alone");
            return;
        }

        tree.node0(end);
    }

    /**
     * goto <x>
     */
    private void _goto() throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != WordPresets.LITERAL) {
            _uLabel(w);
            return;
        }
        LabelInfo info = namedLabels.get(w.val());
        if (info != null && info.head != null) {
            tree.Goto(info.head);
        } else {
            _onError(w, "goto.unknown:" + w.val());
        }
        except(Symbol.semicolon);
    }

    /**
     * break [x] and continue [x]
     */
    private void _break(boolean isBreak) throws ParseException {
        Word w = wr.nextWord();
        switch (w.type()) {
            case WordPresets.LITERAL: {
                LabelInfo info = namedLabels.get(w.val());
                if (info != null) {
                    __break(isBreak, w, info);

                    except(Symbol.semicolon);
                } else {
                    _onError(w, "goto.unknown" + w.val());
                }
            }
            break;
            case Symbol.semicolon:
                if (labels.isEmpty()) {
                    _onError(w, "goto.not_label");
                } else {
                    __break(isBreak, w, null);
                }
                break;
            default:
                _uLabel(w);
                break;
        }
    }

    private void __break(boolean isBreak, Word w, LabelInfo info) throws ParseException {
        if(info == null) {
            for (LabelInfo info1 : labels) {
                if(isBreak ? info1.onBreak != null : info1.onContinue != null) {
                    tree.Goto(isBreak ? info1.onBreak : info1.onContinue);
                    _chkBlockEnd();
                    return;
                }
            }
        } else {
            if(isBreak ? info.onBreak != null : info.onContinue != null) {
                tree.Goto(isBreak ? info.onBreak : info.onContinue);
                _chkBlockEnd();
                return;
            }
        }
        _uLabel(w);
        _chkBlockEnd();
    }

    private void _uLabel(Word w) throws ParseException {
        _onError(w, "goto.illegal_label");
        except(Symbol.semicolon);
    }

    private void _throw() throws ParseException {
        Word w = wr.nextWord();
        wr.retractWord();

        Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
        if(expr == null/* || expr.type() != -1*/) {
            _onError(w, "statement.empty");
            return;
        }
        expr.write(tree, false);
        tree.Std(Opcode.THROW);

        except(Symbol.semicolon);

        _chkBlockEnd();
    }

    private void _chkBlockEnd() throws ParseException {
        if((sectionFlag & 1) != 0) {
            Word w = wr.nextWord();
            if (w.type() != Symbol.right_l_bracket) {
                _onError(w, "statement.unreachable");
                wr.retractWord();
                wr.recycle(w);
                return;
            }

            wr.retractWord();
            wr.recycle(w);
        }
        sectionFlag |= 2;
    }

    /**
     * if 条件判断语句
     */
    private void _if() throws ParseException {
        LabelNode ifFalse = condition(true, Symbol.right_s_bracket);
        if (ifFalse == null) return;

        block();

        _else(ifFalse);
    }

    /**
     * 语句块 or 单行语句
     */
    private void block() throws ParseException {
        Word word = wr.nextWord();
        if (word.type() == Symbol.left_l_bracket) {
            body();
            except(Symbol.right_l_bracket);
        } else {
            sectionFlag &= ~1;
            statement(word);
        }
    }

    /**
     * 条件判断
     *
     * @return false 跳转点
     */
    @Nullable
    private LabelNode condition(boolean checkBracket, short end) throws ParseException {
        if (checkBracket)
            except(Symbol.left_s_bracket);

        ExprParser parser = KScriptVM.retainExprParser(0);

        LabelNode ifFalse = new LabelNode();

        Expression equ = parser.read(this, (short) (checkBracket ? 16 : 0), ifFalse);
        if (equ == null) {
            _onError(wr.readWord(), "statement.empty.if");
            return null;
        }

        equ.write(tree, false);

        if(!(equ instanceof Binary)) // 简单表达式 => IS_TRUE, 复杂的话有，嗯，Binary todo 测试
            tree.If(ifFalse, IfNode.TRUE);

        except(end);

        return ifFalse;
    }


    /**
     * else 语句 <BR>
     * 和数学书上写的一样, 它是可选的
     *
     * @param ifFalse false跳转点
     */
    private void _else(LabelNode ifFalse) throws ParseException {
        Word word = wr.nextWord();
        if (word.type() != Keyword.ELSE) {
            wr.retractWord();
            // if false goto : false
            //   ifTrue
            // : false
            tree.node0(ifFalse);
            return;
        }

        LabelNode end = new LabelNode();

        word = wr.nextWord();
        tree.Goto(end).node0(ifFalse);
        if (word.type() == Symbol.left_l_bracket) {
            body();
            except(Symbol.right_l_bracket);
        } else {
            statement(word);
        }
        tree.node0(end);

        // if (xx) {} else if() {}
        //      is equals to
        // if (xx) {} else { if() {} }
    }

    /**
     * for循环 <BR>
     *     重要通知：不要管作用域了，<BR>
     *     重复一遍，不要管作用域了
     */
    private void _for() throws ParseException {
        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        boolean createdVar;

        except(Symbol.left_s_bracket);

        Word w = wr.nextWord();
        switch (w.type()) {
            case Symbol.semicolon:
                createdVar = false;
                break;
            case Keyword.VAR:
            case Keyword.LET:
            case Keyword.CONST:
                ctx.enterRegion();
                createdVar = true;
                define(w.type());
                break;
            default:
                _onError(w, "unexpected:" + w.val() + ":var or ;");
                return;
        }

        LabelNode breakTo = condition(false, Symbol.semicolon);
        if (breakTo == null) {
            _onError(w, "not_condition");
            return;
        }

        List<Expression> execLast = new ArrayList<>();

        final ExprParser parser = KScriptVM.retainExprParser(0);
        do {
            Expression expr = parser.read(this, (short) (16 | 1024));
            if (expr == null)
                break;
            execLast.add(expr);

        } while (wr.nextWord().type() == Symbol.colon);
        wr.retractWord();

        except(Symbol.right_s_bracket);

        enterCycle(continueTo, breakTo);

        try {
            block();
        } finally {
            endCycle();
        }

        if (!execLast.isEmpty()) {
            LabelNode ol = new LabelNode(continueTo);
            tree.node0(continueTo);
            for (Expression expr : execLast) {
                expr.write(tree, true);
            }
            tree.Goto(ol);
        } else {
            tree.Goto(continueTo);
        }

        tree.node0(breakTo);

        if (createdVar) {
            ctx.endRegion();
        }
    }

    @Deprecated
    private void endCycle() {
        labels.pop();
        namedLabelChunk.pop();
    }

    private void enterCycle(LabelNode continueTo, LabelNode breakTo) {
        LabelInfo info;
        if (labelTmp != null) {
            info = labelTmp;
            labelTmp.onBreak = breakTo;
            labelTmp.onContinue = continueTo;
            labelTmp = null;
            labelPath = 0;
        } else {
            info = new LabelInfo(null, breakTo, continueTo);
        }

        labels.push(info);
        namedLabelChunk.push(new MyHashMap<>());
    }

    /**
     * do-while循环
     */
    private void _do() throws ParseException {
        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        LabelNode breakTo = new LabelNode();

        enterCycle(continueTo, breakTo);

        try {
            block(); // do {]
        } finally {
            endCycle();
        }

        except(Keyword.WHILE, "while");
        LabelNode breakTo1 = condition(true, Symbol.right_s_bracket);
        if (breakTo1 == null) {
            return;
        }

        tree.Goto(continueTo).Node(breakTo).node0(breakTo1);

        except(Symbol.semicolon);
    }

    /**
     * a
     * while循环
     */
    private void _while() throws ParseException {
        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        LabelNode breakTo = condition(true, Symbol.right_s_bracket);
        if (breakTo == null)
            return;

        enterCycle(continueTo, breakTo);

        try {
            block();
        } finally {
            endCycle();
        }

        tree.Goto(continueTo).node0(breakTo);
    }

    /**
     * switch
     */
    private void _switch() throws ParseException {
        except(Symbol.left_s_bracket);

        Node last = tree.last();

        Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 256);
        if(expr == null)
            throw wr.err("statement.empty.switch");

        KType cstv = expr.isConstant() ? expr.asCst().val() : null;

        expr.write(tree, false);

        except(Symbol.left_l_bracket);

        SwitchMap nodeMap = new SwitchMap();
        LabelNode end = new LabelNode();

        SwitchNode sw = tree.Switch(null, nodeMap);

        byte prev = sectionFlag;
        sectionFlag |= 4;

        o:
        while (wr.hasNext()) {
            Word wd = wr.nextWord();
            switch (wd.type()) {
                case Keyword.CASE:

                    expr = KScriptVM.retainExprParser(0).read(this, (short) 512);
                    if(expr == null) {
                        _onError(wd, "case.empty");
                        sectionFlag = prev;
                        return;
                    }
                    if(!(expr = expr.compress()).isConstant()) {
                        _onError(wd, "case.not_constant");
                    }
                    wr.retractWord();
                    except(Symbol.colon);

                    final LabelNode label = tree.Label();
                    if(nodeMap.put(expr.asCst().val(), label) != null) {
                        _onError(wd, "case.duplicate:" + expr.asCst().val());
                    }

                    if(cstv != null) {
                        if(!expr.asCst().val().equalsTo(cstv)) {
                            ASTree tmp = tree;
                            tree = ASTree.builder(tree);
                            switchBlock(end);
                            tree = tmp;
                            break;
                        } else {
                            nodeMap.put(null, label);
                        }
                    }

                    switchBlock(end);
                    break;
                case Keyword.DEFAULT:
                    except(Symbol.colon);
                    if(sw.def != null) {
                        _onError(wd, "duplicate:default");
                        continue;
                    }

                    tree.node0(sw.def = new LabelNode());

                    switchBlock(end);
                    break;
                case Symbol.right_l_bracket:
                    break o;
                default:
                    _onError(wd, "unexpected:" + wd.val());
                    sectionFlag = prev;
                    return;
            }

            sectionFlag = prev;
            wr.recycle(wd);

            if(nodeMap.isEmpty()) {
                _onWarning(wd, "switch.empty");
                if(sw.def != null) {
                    Node next = sw.def.next;
                    System.out.println("empty.next= " + next);
                    tree.last_A(next == null ? last : next);
                } else {
                    tree.last_A(last);
                }
            } else if (cstv != null) {
                _onWarning(wd, "switch.constant");
                // todo test
                Node label = nodeMap.get(null);
                // drop into case
                if (label != null) {
                    if (sw.def == null) {
                        last.next = sw.next;
                    } else {
                        last.next = new GotoNode((LabelNode) label);
                    }
                    // found
                } else if (sw.def != null) {
                    // using default
                    last.next = new GotoNode(end);
                } else {
                    // nothing collided

                    last.next = null;
                    tree.last_A(last);
                    return;
                }
            }

            if (sw.def == null)
                sw.def = end;

            tree.node0(end);
            except(Symbol.right_l_bracket);
        }
    }

    @SuppressWarnings("fallthrough")
    private void switchBlock(LabelNode endPoint) throws ParseException {
        enterCycle(null, endPoint);
        try {
            o:
            while (wr.hasNext()) {
                Word w = wr.nextWord();
                switch (w.type()) {
                    case Keyword.CASE:
                    case Keyword.DEFAULT:
                    case Symbol.right_l_bracket:
                        wr.retractWord();
                    case WordPresets.EOF:
                        break o;
                }
                if (w.type() == Symbol.left_l_bracket) {
                    body();
                    except(Symbol.right_l_bracket);
                } else {
                    sectionFlag &= ~3;
                    statement(w);
                    if((sectionFlag & 2) != 0) {
                        break;
                    }
                }
            }
        } finally {
            endCycle();
        }
    }

    // endregion
    // region 表达式和变量

    /**
     * 定义变量 var/const/let
     */
    @SuppressWarnings("fallthrough")
    private void define(int type) throws ParseException {
        Word w;
        String name = null;
        int first = 0;

        o:
        while (wr.hasNext()) {
            w = wr.nextWord();
            switch(w.type()) {
                case Symbol.left_m_bracket:

                    break;
                case WordPresets.LITERAL:
                    if(name != null) {
                        _onError(w, "unexpected:" + w.val());
                        return;
                    }

                    name = w.val();

                    if (ctx.exists(name) || parameters.containsKey(name)) {
                        _onWarning(w, "var.exist");
                    }

                    first = 1;

                    break;
                case Symbol.assign:
                    if(name == null) {
                        _onError(w, "unexpected:=");
                        return;
                    }

                    Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
                    if (expr == null) {
                        _onError(w, "statement.not");
                        return;
                    }

                    KType val;

                    // Note: 变量第一次初始化时才可以用def，后面就必须用opcode了
                    // Note2: 这里检测同级就好
                    boolean exist = ctx.selfExists(name);
                    if (expr.type() != -1 && !exist) {
                        val = expr.asCst().val();
                    } else {
                        if(exist && type == Keyword.CONST) {
                            _onError(w, "var.redefine");
                        }

                        expr.write(tree, false);
                        tree.Set(name);

                        val = null;
                    }

                    switch (type) {
                        case Keyword.LET:
                            ctx.local(name, val, tree.last());
                            break;
                        case Keyword.CONST:
                            ctx.Const(name, val);
                            break;
                        case Keyword.VAR:
                            ctx.global(name, val);
                            break;
                    }
                    name = null;
                    break;
                case Symbol.comma:
                    if (name != null && type == Keyword.CONST) {
                        _onError(w, "var.initialize_const");
                    }

                    if(first++ == 1) {
                        // lazy define
                        if(name != null) {
                            switch (type) {
                                case Keyword.LET:
                                    ctx.local(name, KUndefined.UNDEFINED, tree.last());
                                    break;
                                case Keyword.CONST:
                                    ctx.Const(name, KUndefined.UNDEFINED);
                                    break;
                                case Keyword.VAR:
                                    ctx.global(name, KUndefined.UNDEFINED);
                                    break;
                            }
                        }

                        name = null;
                        break;
                    }
                default:
                    _onError(w, "unexpected:" + w.val() + ":;");
                case Symbol.semicolon:
                    if(name != null) {
                        switch (type) {
                            case Keyword.LET:
                                ctx.local(name, KUndefined.UNDEFINED, tree.last());
                                break;
                            case Keyword.CONST:
                                _onError(w, "var.initialize_const");
                                break;
                            case Keyword.VAR:
                                ctx.global(name, KUndefined.UNDEFINED);
                                break;
                        }
                    }

                    break o;
            }

            wr.recycle(w);
        }
    }

    /**
     * 标准单个表达式
     */
    private void assign() throws ParseException {
        // check label
        Word k = wr.nextWord();
        if (k.type() == WordPresets.LITERAL) {
            if(wr.readWord().type() == Symbol.colon) {
                _namedLabel(k.val());
                return;
            }
        }
        wr.retractWord();

        Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
        if (expr == null) {
            _onError(k, "statement.not");
        } else {
            expr.write(tree, true);
        }

        except(Symbol.semicolon);
    }

    /**
     * @return 常量值
     */
    @Nullable
    @Override
    public KType maybeConstant(String name) {
        return ctx.isConst(name) ? ctx.globals.get(name) : null;
    }

    /**
     * 使用（访问）变量
     */
    @Override
    public void useVariable(String name) {
        ctx.chainUpdate(name);
        if (!ctx.selfExists(name)) {
            int i = parameters.getOrDefault(name, -1);
            if (i != -1) {
                ctx.loadPar(i, name);
            }
        } else {
            // 只有存在才会更新
            ctx.updateRegion(name, tree.last());
        }
    }

    @Override
    public void assignVariable(String name) {
        ctx.chainUpdate(name);
        if(ctx.isConst(name)) Helpers.throwAny(wr.err("var.write_const"));
    }

    @Override
    public JSLexer getLexer() {
        return wr;
    }

    //endregion
    // region 输出错误

    private boolean success = true;     // 语法解析结果

    private void _onError(ParseException e) {
        success = false;
        handler.handle("ERR", file, e);
    }

    /**
     * Notice: 语法级错误(return ,,,;)需要return, 定义级的(const v;)没必要 <BR>
     *     不过随便了, 反正是[错误]
     */
    private void _onError(Word word, String v) {
        success = false;
        handler.handle("ERR", file, wr.err(v, word));
    }

    private void _onWarning(Word word, String v) {
        handler.handle("WARN", file, wr.err(v, word));
    }

    private void except(short id) throws ParseException {
        except(id, Symbol.byId(id));
    }

    private void except(short id, String s) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != id) {
            _onError(w, "unexpected:" + w.val() + ':' + s);
            wr.retractWord();
        }
        wr.recycle(w);
    }

    //endregion
}
