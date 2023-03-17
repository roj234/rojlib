package roj.mildwind.bridge;

import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2023/6/22 0022 2:44
 */
interface AsmMethodInvoker {
	int canInvoke(Arguments arg);
	JsObject invoke(Object self, Arguments arg);
}
