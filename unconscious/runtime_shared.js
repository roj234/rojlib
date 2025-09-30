export const PASSIVE_EVENT = {passive: true};
export const ONCE_EVENT = {once: true};

/**
 * 开发时获取带名称的符号
 * @param name 符号名称
 * @returns {symbol}
 */
export function debugSymbol(name) {
	return import.meta.env.DEV?Symbol(name):Symbol();
}

/**
 * 存储简单类型时可使用AS_IS序列化器
 * @template T
 * @type {function(T): T}
 */
export const AS_IS = t => t;

/**
 * 创建仅响应鼠标左键的事件处理器
 * @param {Function} handler - 原始事件处理函数
 * @returns {Function} 包装后的事件处理函数
 */
function _left(handler) {return _button(handler, 0);}
/**
 * 创建仅响应鼠标中键的事件处理器
 * @param {Function} handler - 原始事件处理函数
 * @returns {Function} 包装后的事件处理函数
 */
function _middle(handler) {return _button(handler, 1);}
/**
 * 创建仅响应鼠标右键的事件处理器
 * @param {Function} handler - 原始事件处理函数
 * @returns {Function} 包装后的事件处理函数
 */
function _right(handler) {return _button(handler, 2);}
/**
 * 通用按钮事件处理器包装器
 * @param {Function} handler - 原始事件处理函数
 * @param {number} button - 要监听的按钮编号（0:左键, 1:中键, 2:右键）
 * @returns {Function} 包装后的事件处理函数
 */
function _button(handler, button) {
	return e => {
		if (e.button !== button) return;
		handler(e);
	};
}
/**
 * 阻止事件默认行为的事件处理器包装器
 * @param {Function} handler - 原始事件处理函数
 * @returns {Function} 包装后的事件处理函数
 */
function _prevent(handler) {
	return e => {
		e.preventDefault();
		handler(e);
	};
}
/**
 * 阻止事件冒泡的事件处理器包装器
 * @param {Function} handler - 原始事件处理函数
 * @returns {Function} 包装后的事件处理函数
 */
function _stop(handler) {
	return e => {
		e.stopPropagation();
		handler(e);
	};
}

/**
 * 直接子元素事件代理（仅匹配第一级子元素）
 * @param {Function} handler - 原始事件处理函数
 * @param {string} selector - CSS 元素选择器
 * @returns {Function} 包装后的事件处理函数
 * @example
 * // 匹配直接子元素 <li>
 * \@onclick.children("li")
 *
 * @property {Element} event.delegateTarget - 匹配到的直接子元素
 */
function _children(handler, selector) {
	return e => {
		const top = e.currentTarget;
		let target = e.target;
		if (top === target) return;

		while (target.parentElement !== top) {
			target = target.parentElement;
		}

		if (selector && target.matches(selector)) {
			e.delegateTarget = target;
			handler(e);
		}
	};
}

/**
 * 通用元素委托事件处理器（支持任意层级匹配）
 * @param {Function} handler - 原始事件处理函数
 * @param {string} selector - 标准 CSS 选择器（不支持 :scope 伪类）
 * @returns {Function} 包装后的事件处理函数
 * @example
 * // 匹配容器内任意层级的 .btn 元素
 * \@onclick.delegate(".btn")
 *
 * @property {Element} event.delegateTarget - 首个匹配选择器的祖先元素
 */
function _delegate(handler, selector) {
	return e => {
		const top = e.currentTarget;
		let target = e.target;
		while (target) {
			if (target === top) return;
			if (target.matches(selector)) break;
			target = target.parentElement;
		}

		e.delegateTarget = target;
		handler(e);
	};
}

export {_left, _right, _middle, _button, _children, _prevent, _stop, _delegate};