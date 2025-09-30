import {ID_CLASSLIST, ID_EVENTHANDLER, ID_NAMESPACE, ID_STYLELIST} from "./constant.js";

/**
 * 开发时获取带名称的符号
 * @param name 符号名称
 * @returns {symbol}
 */
function debugSymbol(name) {
	return import.meta.env.DEV?Symbol(name):Symbol();
}
export {debugSymbol};

/**
 * 创建 DOM 元素
 * @param {string} type - 元素标签类型
 * @param {Object} [props] - 元素属性对象（可包含特殊属性 ns 用于命名空间）
 * @param {...*} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {HTMLElement} 创建的 DOM 元素
 */
function createElement(type, props, ...children) {
	// 创建元素
	const element = document.createElementNS(props && props[ID_NAMESPACE] || "http://www.w3.org/1999/xhtml", type);

	// 设置属性
	if (props) {
		for (const [key, value] of Object.entries(props)) {
			if (key === ID_NAMESPACE) continue;
			setAttribute(element, key, value);
		}
	}

	appendChildren(element, children);
	return element;
}

/**
 * 创建文档片段
 * @param {...*} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {DocumentFragment} 文档片段对象
 */
function createFragment(...children) {
	const fragment = document.createDocumentFragment();
	appendChildren(fragment, children);
	return fragment;
}

/**
 * 往父元素中插入子元素
 * @param {Element} parent - 父元素
 * @param {Array} children - 子元素列表（支持字符串、节点、响应式变量）
 */
function appendChildren(parent, children) {
	for (const child of children) {
		if (child == null) continue;
		parent.appendChild(createChildNode(child));
	}
}

/**
 * 转换为节点
 * @param {any} child
 * @returns {Node}
 */
function createChildNode(child) {
	return child instanceof Node ? child : new Text(child);
}

/**
 * 设置属性
 * @param {HTMLElement} element
 * @param {string} key
 * @param {string|Reactive<string>} value
 */
function setAttribute(element, key, value) {
	// 调试属性
	if (import.meta.env.DEV && key.startsWith("__")) {
		element[key] = value;
		return;
	}

	if (key.startsWith(ID_STYLELIST)) {
		element.style[key.substring(ID_STYLELIST.length)] = value;
	} else
	if (key.startsWith(ID_CLASSLIST)) {
		element.classList.toggle(key.substring(ID_CLASSLIST.length), value);
	} else
	if (key.startsWith(ID_EVENTHANDLER)) {
		let attrib; // once, capture, passive等
		if (Array.isArray(value))
			[value, attrib] = value;

		element.addEventListener(key.substring(ID_EVENTHANDLER.length), value, attrib);
	} else {
		element.setAttribute(key, value);
	}
}

export {createElement, createFragment, appendChildren};
export {_left, _right, _middle, _button, _children, _prevent, _stop, _delegate} from './runtime_shared.js';
