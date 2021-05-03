package roj.kscript.parser;

import roj.annotation.Internal;
import roj.collect.MyHashMap;
import roj.collect.ReuseStack;
import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.kscript.api.API;
import roj.kscript.api.ErrorHandler;
import roj.kscript.ast.*;
import roj.kscript.func.KFunction;
import roj.kscript.parser.expr.Expression;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LabelInfo;
import roj.kscript.util.SwitchMap;
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
     * todo 常量（自定义来没有修改过的变量）传播， 删除未使用的变量, ASTree
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

    public void setErrorHandler(ErrorHandler handler) {
        if(handler == null)
            throw new NullPointerException("handler");
        this.handler = handler;
    }

    public KParser(ContextPrimer ctx, ErrorHandler handler) {
        this.ctx = ctx;
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
        this.file = parent.file;
        this.ctx = parent.ctx.makeChild();
        this.wr = parent.wr;

        this.success = true;
        //this.undefVars = parent.undefVars;

        this.arguments.clear();
        this.inBlock = false;

        this.namedLabels.clear();
        this.labelTmp = null;
        this.labelPath = 0;
        this.labels.clear();

        this.handler = parent.handler;

        return this;
    }

    /// region 解析

    public KFunction parse(File file) throws IOException, ParseException {
        wr.init(IOUtil.readFile(file));
        this.file = file.getName();
        return parse0();
    }

    public KFunction parse(String file, CharSequence text) throws ParseException {
        wr.init(text);
        this.file = file;
        return parse0();
    }

    private KFunction parse0() throws ParseException {
        tree = ASTree.builder(file);
        wr.setLineHandler(tree);

        Word w;
        while (true) {
            w = wr.nextWord();
            if (w.type() == WordPresets.EOF) {
                break;
            } else {
                statement(w);
            }
        }

        //_checkDel();

        //_checkUndefVar();

        if (success) {
            //System.out.println("---编译成功---");
            return tree.build(ctx);
        } else {
            throw wr.err("compile_error_occurs");
        }
    }

    /*private void _checkDel() throws ParseException {
        _onWarning(wr.nextWord(), "警告: var支持作用域.");
    }*/

    @Override
    public KFunction parseInnerFunc() throws ParseException {
        KFunction fn = API.cachedFP(depth + 1, this).parseInner(tree);
        wr.setLineHandler(tree);

        return fn;
    }

    private KFunction parseInner(ASTree parent) throws ParseException {
        tree = ASTree.builder(parent);
        wr.setLineHandler(tree);

        functionParser();

        return success ? tree.build(ctx) : null;
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
                _onError(w, "unexpected:" + w.val() + ":Name/'('");
                wr.retractWord();
                return;
        }
        wr.recycle(w);

        tree.funcName(name);

        if(name != null)
            except(Symbol.left_s_bracket);
        arguments();
        except(Symbol.right_s_bracket);

        except(Symbol.left_l_bracket);
        body();
        except(Symbol.right_l_bracket);
    }

    /**
     * 当前方法的参数
     */
    ToIntMap<String> arguments = new ToIntMap<>();

    /**
     * Unreachable statement检测
     */
    boolean inBlock;

    /// 参数
    private void arguments() throws ParseException {
        arguments.clear();

        Word word;

        int i = 0;
        byte flag = 0;

        o:
        while (true) {
            word = wr.nextWord();
            switch (word.type()) {
                case Symbol.right_s_bracket:
                    wr.retractWord();
                    break o;
                case WordPresets.LITERAL:
                    if (flag == 1)
                        _onError(word, "missing:,");
                    arguments.put(word.val(), i++);
                    flag = 1;
                    break;
                case Symbol.comma:
                    if (flag == 0) {
                        _onError(word, "unexpected:,");
                    }
                    flag = 0;
                    break;
            }
        }
    }

    /**
     * 函数体
     */
    @SuppressWarnings("fallthrough")
    private void body() throws ParseException {
        inBlock = true;

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
    private void statement(Word w) {
        if (labelTmp != null && labelPath-- == 0) {
            labelTmp = null;
        }

        try {
            switch (w.type()) {
                case Keyword.CASE:
                case Keyword.DEFAULT:
                    //LabelInfo info = labels.isEmpty() ? null : labels.last();
                    //if(info.i == LabelInfo.SWITCH) {
                        wr.retractWord();
                        return;
                    //}
                    //_onError(w, "not_statement");
                    //break;
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
                    _onWarning(w, "empty_statement");
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
                        Expression expr = API.cachedEP(0).read(this, (short) 0);
                        if (expr != null) {
                            expr.write(tree, true);
                            except(Symbol.semicolon);
                        }
                    } else {
                        _onError(w, "not_statement");
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
        if (w.type() == Symbol.semicolon) {
            tree.Std(OpCode.RETURN_EMPTY);
        } else {
            wr.retractWord();
            Expression expr = API.cachedEP(0).read(this, (short) 0);
            if (expr == null) {
                _onError(w, "What return it is??");
                return;
            }

            expr.write(tree, false);
            tree.Std(OpCode.RETURN);

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
            _onError(w, "unexpected:" + w.val() + ":type.name");
            wr.retractWord();
            return;
        } else {
            name = w.val();
        }
        wr.retractWord();
        wr.recycle(w);

        KFunction fn = API.cachedFP(depth + 1, this).parseInner(tree);

        wr.setLineHandler(tree);

        if (fn == null) {
            success = false;
            // 无效的函数
            return;
        }

        // 定义函数, 整个地方都可以用
        ctx.global(name, fn);
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

    private void _try() throws ParseException {
        except(Symbol.left_l_bracket);

        LabelNode catchNode = new LabelNode();
        LabelNode finallyNode = new LabelNode();
        LabelNode end = new LabelNode();

        tree.TryEnter(catchNode, finallyNode, end);

        body();

        except(Symbol.right_l_bracket);

        tree.Std(OpCode.TRY_EXIT)/*.Goto(end)*/;

        byte flag = 0;

        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Keyword.FINALLY:
                    if ((flag & 1) != 0)
                        throw wr.err("duplicate.finally");
                    tree.node0(finallyNode);
                    except(Symbol.left_l_bracket);
                    body();
                    except(Symbol.right_l_bracket);
                    // END ?
                    tree.TryEnd(end);
                    flag |= 1;
                    break;
                case Keyword.CATCH:
                    if ((flag & 2) != 0)
                        throw wr.err("duplicate.catch");

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

                    tree.node0(catchNode);

                    if (hasVar) {
                        // todo check
                        ctx.local(w.val(), null, catchNode); // 定义 catch(e)
                        // end try | e = pop();

                        tree.Set(w.val());
                    }

                    body();
                    except(Symbol.right_l_bracket);

                    // END ?
                    tree.TryEnd(end);

                    flag |= 2;
                    break;
                default:
                    wr.retractWord();
                    break o;
            }
        }

        if (flag == 0) {
            // 孤立的try
            _onError(w, "standalone.try");
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
            _onError(w, "goto.unknown_label");
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
                    _onError(w, "goto.unknown_label");
                }
            }
            break;
            case Symbol.semicolon:
                if (labels.isEmpty()) {
                    _onError(w, "break.not_in_label");
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
                    return;
                }
            }
        } else {
            if(isBreak ? info.onBreak != null : info.onContinue != null) {
                tree.Goto(isBreak ? info.onBreak : info.onContinue);
                return;
            }
        }
        _uLabel(w);

        except(Symbol.semicolon);

        _chkBlockEnd();
    }

    private void _uLabel(Word w) throws ParseException {
        _onError(w, "goto.illegal_label");
        except(Symbol.semicolon);
    }

    private void _throw() throws ParseException {
        Word w = wr.nextWord();
        wr.retractWord();

        Expression expr = API.cachedEP(0).read(this, (short) 0);
        if(expr == null/* || expr.type() != -1*/) {
            _onError(w, "empty_statement");
            return;
        }
        expr.write(tree, false);
        tree.Std(OpCode.THROW);

        except(Symbol.semicolon);

        _chkBlockEnd();
    }

    private void _chkBlockEnd() throws ParseException {
        if(inBlock) {
            Word w = wr.nextWord();
            if (w.type() != Symbol.right_l_bracket) {
                _onError(w, "unreachable_statement");
            }
            wr.retractWord();
            wr.recycle(w);
        }
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
            inBlock = false;
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

        ExpressionParser parser = API.cachedEP(0);

        LabelNode ifFalse = new LabelNode();

        Expression equ = parser.read(this, (short) (checkBracket ? 16 : 0), ifFalse);
        if (equ == null) {
            _onError(wr.readWord(), "empty_if_statement");
            return null;
        }

        equ.write(tree, false);

        // todo
        tree.If(ifFalse, IfNode.IS_TRUE);

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
            //wr.retractWord();
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

        final ExpressionParser parser = API.cachedEP(0);
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

        Expression expr = API.cachedEP(0).read(this, (short) 256);
        if(expr == null)
            throw wr.err("empty_switch");

        KType cstv = expr.isConstant() ? expr.asCst().val() : null;

        expr.write(tree, false);

        except(Symbol.right_s_bracket);
        except(Symbol.left_l_bracket);

        SwitchMap nodeMap = new SwitchMap();
        LabelNode end = new LabelNode(), def = null;

        // 这个可以删除，因为优化并没啥软用，不会有人switch常量的
        Node last = tree.last();
        SwitchNode sw = tree.Switch(end, nodeMap);

        while (true) {
            Word wd = wr.nextWord();
            switch (wd.type()) {
                case Keyword.CASE:

                    expr = API.cachedEP(0).read(this, (short) 256);
                    if(expr == null) {
                        _onError(wd, "case.empty");
                        return;
                    }
                    if(!(expr = expr.compress()).isConstant()) {
                        _onError(wd, "case.not_constant");
                    }
                    except(Symbol.colon);

                    final LabelNode label = tree.Label();
                    if(nodeMap.put(expr.asCst().val(), label) != null) {
                        _onError(wd, "case.duplicate");
                    }

                    if(cstv != null) {
                        if(!expr.asCst().val().equalsTo(cstv)) {
                            break;
                        } else {
                            nodeMap.put(null, label);
                        }
                    }

                    switchBlock(end);
                    tree.Goto(end);
                    break;
                case Keyword.DEFAULT:
                    except(Symbol.colon);
                    if(def != null) {
                        _onError(wd, "duplicate_default");
                        continue;
                    }

                    tree.node0(def = end);
                    end = new LabelNode();

                    switchBlock(end);
                    tree.Goto(end);
                    break;
                default:
                    _onError(wd, "unexpected:" + wd.val());
            }
            wr.recycle(wd);

            if(cstv != null) {
                Node label = nodeMap.get(null);
                if(label != null) {
                    if(def == null) {
                        last.next = sw.next;
                    } else {
                        last.next = new GotoNode((LabelNode) label);
                    }
                    // found
                } else  if(def != null) {
                    // def
                    last.next = new GotoNode(end);
                } else {
                    last.next = null;
                    tree.last(last);
                    // clear all nodes after this switch
                    // because no code will be executed
                    return;
                }
            }

            tree.node0(end);
        }
    }

    private void switchBlock(LabelNode endPoint) throws ParseException {
        enterCycle(null, endPoint);
        try {
            o:
            while (true) {
                Word word = wr.nextWord();
                switch (word.type()) {
                    case Keyword.CASE:
                    case Keyword.DEFAULT:
                        wr.retractWord();
                    case WordPresets.EOF:
                        break o;
                }
                if (word.type() == Symbol.left_l_bracket) {
                    body();
                    except(Symbol.right_l_bracket);
                } else {
                    inBlock = false;
                    statement(word);
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
    private void define(int type) throws ParseException {
        Word w;
        String name = null;
        int first = 0;

        o:
        while (wr.hasNext()) {
            w = wr.nextWord();
            switch(w.type()) {
                case WordPresets.LITERAL:
                    if(name != null) {
                        _onError(w, "unexpected:" + w.val());
                        return;
                    }

                    name = w.val();

                    if (ctx.exists(name) || arguments.containsKey(name)) {
                        _onWarning(w, "variable.exists");
                    }

                    first = 1;

                    break;
                case Symbol.assign:
                    if(name == null) {
                        _onError(w, "unexpected:=");
                        return;
                    }

                    Expression expr = API.cachedEP(0).read(this, (short) 0);
                    if (expr == null) {
                        _onError(w, "not_statement");
                        return;
                    }

                    KType val;

                    // todo if duplicate?
                    if (expr.type() != -1 && !ctx.exists(name)) {
                        val = expr.asCst().val();
                    } else {
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
                        _onError(w, "const_should_initialize");
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
                    if (name != null && type == Keyword.CONST) {
                        _onError(w, "const_should_initialize");
                    }

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

        Expression expr = API.cachedEP(0).read(this, (short) 0);
        if (expr == null) {
            _onError(k, "not_statement");
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
        if (!ctx.exists(name)) {
            final int i = arguments.getOrDefault(name, -1);
            if (i != -1) {
                ctx.loadArg(i, name);
            }
        } else {
            ctx.updateRegion(name, tree.last());
        }
    }

    @Override
    public void assignVariable(String name) {
        if(ctx.isConst(name)) Helpers.throwAny(wr.err("write_to_const"));
    }

    @Override
    public JSLexer getLexer() {
        return wr;
    }

    //endregion
    // region 输出错误

    private boolean success = true;     // 语法解析结果
    /*private ToIntMap<String> undefVars; // 未定义变量

    private void _checkUndefVar() {
        for (String name : undefVars.keySet()) {
            KType type = context.getNullable(name);
            if (type == null || type.getType() != Type.FUNCTION) {
                _onError(new Word().reset(0, undefVars.getInt(name), ""), "undefined_Variable");
            }
        }
        undefVars.clear();
    }*/

    private void _onError(ParseException e) {
        success = false;
        handler.handle("ERR", file, e);
    }

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
