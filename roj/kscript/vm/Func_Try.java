package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.asm.Node;
import roj.kscript.asm.TryEnterNode;
import roj.kscript.func.KFunction;
import roj.kscript.type.KError;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2020/9/27 12:28
 */
public final class Func_Try extends Func {
	public Func_Try(Node begin, Frame frame) {
		super(begin, frame);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public KType invoke(@Nonnull IObject $this, ArgList param) {
		Frame f = curr = getIdleFrame();
		f.init($this, param);

		Node p = begin;

		TryEnterNode info = null;
		ScriptException se = null;
		/**
		 * 0 : none, 1: caught, 2: finally_throw, 3: finally_eat
		 */
		int stg = 0;

		execution:
		while (true) {
			// 当前流程走完了
			if (p == null) {
				switch (stg) {
					case 0: // 不在try中
						break execution;
					case 1: // catch执行完毕
						if ((p = info.fin) != null) { // 若有finally则执行
							stg = 3;
							break;
						}
					case 3: // catch 或者 [正常执行 的 finally] 完毕
						p = info.end;

						/** if stack is empty, then return {@link ErrorInfo#NONE} **/
						ErrorInfo stack = f.popError();
						info = stack.info;
						se = stack.e;
						stg = stack.stage;
						break;
					case 2: // 没有catch的finally
						f.reset();
						throw se;
				}
			}
			f.linear(p);
			try {
				p = p.exec(f);
			} catch (TCOException e) {
				if (e == TCOException.TCO_RESET) {
					p = begin;

					info = null;
					se = null;
					stg = 0;
				} else {
					throw e;
				}
			} catch (Throwable e) {
				if (e == ScriptException.TRY_EXIT) {
					// try正常执行，进入finally
					p = info.fin;
					stg = 3;
				} else {
					if (info != null) {
						// try的catch中又碰到了try且给你送了个异常
						f.pushError(stg, info, se);
					}

					se = collect(f, p, e);

					if ((info = f.popTryOrNull()) == null) {
						// 未捕捉的异常
						f.reset();
						throw se;
					}

					// 清栈
					f.stackClear();

					// 如果有 catch
					if ((p = info.handler) != null) {
						// 将转换后的KType错误对象压入栈顶
						f.push(new KError(se));
						stg = 1;
					} else {
						// finally
						// 无需检测是否null, 一个try不可能既没有catch,也没有finally
						p = info.fin;
						stg = 2;
					}
				}
			}
		}

		return f.returnVal();
	}

	public KFunction onReturn(Frame frame) {
		return new Func_Try(begin, curr.closure()).set(source, name, clazz);
	}
}
