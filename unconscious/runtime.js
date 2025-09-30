import {ID_CLASSLIST, ID_EVENTHANDLER, ID_NAMESPACE, ID_STYLELIST, VERSION} from "./constant.js";
import {debugSymbol} from "./runtime_shared.js";

export {_left, _right, _middle, _button, _children, _prevent, _stop, _delegate} from './runtime_shared.js';

/**
 * 响应式对象
 * @template T
 * @typedef {Object} Reactive
 * @property {T} value 不响应式的源对象
 */

/**
 * 可渲染DOM元素
 * @typedef {string|JSX.Element|Readonly<Reactive<string|JSX.Element>>} Renderable
 */

/**
 * 多个可渲染DOM元素
 * @typedef {Array<Renderable>} Fragment
 */

/**
 * 组件函数
 * @typedef {function(props: Record<string, any>, children: Renderable[]): Renderable} Component
 */

/**
 * persist state的前缀，通常为项目名称
 * @typedef {string} UC_PERSIST_STORE='default'
 */
/**
 * persist state修改时是否立即保存，如果关闭会在下列任一条件满足时保存:
 * a. 标签页关闭时beforeunload
 * b. 标签页失去焦点时visibilitychange
 * c. 一分钟没有新修改后
 * @typedef {boolean} UC_IMMEDIATE_STORE=false
 */
/**
 * 是否允许响应式变量的值为多个DOM根节点（Fragment/Array） —— 警告，可能影响性能！
 * @typedef {string} UC_REACTIVE_FRAGMENT=false
 */

function _devError(first, ...then) {
	console.error(
		'%cUnconscious%c '+first,
		'color: #fff; background-color: #007BFF; padding: 3px 5px;',
		'',
		...then
	);
}

//region DOM核心 (由Babel导入)
const LAZY_DISPOSE_FALLBACK = new Error();

/**
 * @template T
 * @type {Map<T, WeakRef<T>>}
 */
const weakRefs = new Map;
function _weakRefOf(obj) {
	let t = weakRefs.get(obj);
	if (!t) weakRefs.set(obj, t = new WeakRef(obj));
	return t;
}
//region DOM
/**
 * 创建 DOM 元素
 * @param {string} type - 元素标签类型
 * @param {Object} [props] - 元素属性对象（可包含特殊属性 ns 用于命名空间）
 * @param {...Renderable|Fragment} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {Element} 创建的 DOM 元素
 */
function createElement(type, props, ...children) {
	// 创建元素
	const element = document.createElementNS(props && props[ID_NAMESPACE] || "http://www.w3.org/1999/xhtml", type);

	// 设置属性
	if (props) {
		let weakSelf;
		for (const [key, value] of Object.entries(props)) {
			if (key === ID_NAMESPACE) continue;

			if (isReactive(value)) {
				if (import.meta.env.DEV && key.startsWith("on")) {
					throw new Error("事件监听不能响应式！");
				}

				if (!weakSelf) weakSelf = _weakRefOf(element);
				const update = () => setAttribute(weakSelf.deref(), key, unconscious(value));
				$watchOn(value, update, element);
			} else
				setAttribute(element, key, value);
		}
	}

	appendChildren(element, children);
	return element;
}

/**
 * 创建文档片段
 * @param {...Renderable|Fragment} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {Fragment} 文档片段对象
 */
function createFragment(...children) {
	return children.flat();
}

/**
 * 往父元素中插入子元素
 * @param {Element} parent - 父元素
 * @param {Fragment} children - 子元素列表（支持字符串、节点、响应式变量）
 */
function appendChildren(parent, children) {
	for (const child of children.flat()) {
		if (child == null) continue;

		if (isReactive(child)) {
			let childNode = {parentElement:parent};
			let callback;
			if (UC_REACTIVE_FRAGMENT) {
				let cleanup;

				callback = () => {
					let value = unconscious(child);
					cleanup && cleanup();

					// 针对文本更新
					if (childNode instanceof Text && typeof value !== "object") {
						childNode.textContent = value ?? "";
						return;
					}

					const parent = childNode.parentElement;
					if (parent == null) throw LAZY_DISPOSE_FALLBACK;

					if (Array.isArray(value)) {
						const elements = [];
						for (const el of value) {
							if (el == null) continue;
							const newChild = createChildNode(el);
							if (childNode.parentNode) {
								parent.insertBefore(newChild, childNode);
							} else {
								parent.appendChild(newChild);
							}
							$disposable(newChild, listenerKey);
							elements.push(newChild);
						}

						// 永远保留一个DOM占位符，这样下次插入时不会丢失它的位置信息
						if (!elements.length) elements.push(createChildNode(""));

						if (childNode.parentNode) $dispose(childNode, listenerKey);

						// 多元素时，返回副作用清理函数
						return cleanup = () => {
							childNode = null;
							for (const element of elements) {
								if (element.parentElement) {
									if (!childNode) childNode = element;
									else $dispose(element, listenerKey);
								}
							}
						};
					} else {
						// 永远保留一个DOM占位符，这样下次插入时不会丢失它的位置信息
						const newChild = createChildNode(value ?? "");

						if (childNode.parentNode) {
							childNode.replaceWith(newChild);
							$dispose(childNode, listenerKey);
						} else {
							parent.appendChild(newChild);
						}

						fireAppended(parent, newChild);
						$disposable(newChild, listenerKey);
						childNode = newChild;
					}
				};
			} else {
				callback = () => {
					let value = unconscious(child);

					// 针对文本更新
					if (childNode instanceof Text && typeof value !== "object") {
						childNode.textContent = value ?? "";
						return;
					}

					if (value === childNode) return;

					const parent = childNode.parentElement;

					// 永远保留一个DOM占位符，这样下次插入时不会丢失它的位置信息
					const newChild = createChildNode(value ?? "");

					if (childNode.parentNode) {
						childNode.replaceWith(newChild);
						$dispose(childNode);
					} else {
						if (parent == null) throw LAZY_DISPOSE_FALLBACK;
						parent.appendChild(newChild);
					}

					fireAppended(parent, newChild);
					$disposable(newChild, listenerKey);
					childNode = newChild;
				};
			}
			const listenerKey = [child, callback];
			$watch(child, callback);
		} else {
			const newChild = createChildNode(child);
			parent.appendChild(newChild);
			fireAppended(parent, newChild);
		}
	}
}

/**
 * 发送append事件
 * @param {Element} parent
 * @param {Node} child
 */
function fireAppended(parent, child) {
	if (child instanceof DocumentFragment) {
		Object.defineProperty(child, "parentElement", {
			value: parent
		})
	}
	child.dispatchEvent(new CustomEvent("append", {detail:parent}));
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
//endregion
export {createElement, createFragment, appendChildren};
export * from './runtime_shared';
//region 行为
/**
 * 响应式 CSS 类动态绑定装饰器
 * @param {HTMLElement} element - 要操作的目标 DOM 元素
 * @param {Object<string, boolean|Reactive<boolean>>} properties - 类名映射对象
 * @example
 * const isActive = $state(false)
 * _classesBehaviour(el, { 'active': isActive, 'list-item': true })
 */
function _classesBehaviour(element, properties) {
	for (const key in properties) {
		const value = properties[key];
		const update = () => {element.classList.toggle(key, unconscious(value))};
		if (!isReactive(value)) update();
		else $watchOn(value, update, element);
	}
	return element;
}

/**
 * 响应式样式动态绑定装饰器
 * @param {HTMLElement} element - 要操作的目标 DOM 元素
 * @param {Object<string, string|number|Reactive<string|number>>} properties - 样式属性映射对象
 * @example
 * const fontSize = $state('16px')
 * _stylesBehaviour(el, {
 *   fontSize: fontSize,      // 驼峰格式
 *   'background-color': '#fff' // 或连字符格式
 * })
 *
 * @note 支持两种样式属性命名格式：
 * - CSS 驼峰式 (fontSize)
 * - CSS 原生连字符式 (background-color)
 */
function _stylesBehaviour(element, properties) {
	for (const key in properties) {
		const value = properties[key];
		const update = () => {element.style[key] = addPx(key, unconscious(value))};
		if (!isReactive(value)) update();
		else $watchOn(value, update, element);
	}
	return element;
}
function addPx(k, v) { return v; }
//endregion
export {_classesBehaviour, _stylesBehaviour};
//endregion
//region 响应式核心
/**
 * @param {Object} object
 * @returns {object is Reactive}
 * @note 实际返回值为Map(监听器)|undefined
 */
function isReactive(object) {return object && object[$LISTENERS];}
/**
 * @template T
 * @param {T|Reactive<T>} object
 * @returns {T}
 */
function unconscious(object) {
	while (isReactive(object)) object = object.value;
	return object;
}
function isPureObject(object) {return Object.prototype.toString.call(object) === "[object Object]";}

/**
 * 注册一个监听器以在元素被移除时移除
 * @param {Element|Text} element - 元素
 * @param {[Reactive<any>, Function]|Function} listenerKey - 监听器键
 */
function $disposable(element, listenerKey) {
	let set = element[$DISPOSABLE];
	if (!set) element[$DISPOSABLE] = set = new Set();
	set.add(listenerKey);
}

/**
 * 移除元素并删除它的响应式监听器
 * @param {Element|Text} element - 需要删除的元素
 * @param {[Reactive<any>, Function]|undefined} [keep=undefined] - 保留不取消注册的响应式监听器
 */
function $dispose(element, keep) {
	const listeners = element[$DISPOSABLE];
	if (listeners) {
		for (const key of listeners) {
			if (typeof key === "function") key();
			else if (key !== keep) $unwatch(...key);
		}
	}

	element.remove();
	if (element.children) {
		for (const child of Array.from(element.children))
			$dispose(child);
	}
}

const $DIRTY = debugSymbol("Dirty");
// 第一层代理直接在容器中保存数据
const $LISTENERS = debugSymbol("Listeners");
const $DISPOSABLE = debugSymbol(("DisposableRef"));
/**
 * 嵌套代理不能在holder容器中用symbol保存数据
 * @type {WeakMap<Object, Proxy>}
 */
const deepProxyCache = new WeakMap();
/**
 * 依赖捕获
 * @type {Set<Reactive>}
 */
let dependCapture;

/**
 * @type {ProxyHandler}
 */
const ShallowProxy = {
	get: getArray(false),
	set(target, prop, value, proxy) {
		const oldValue = target.value;
		if (prop === "value") {
			if (oldValue !== value) {
				target.value = value;
				$update(proxy);
			}
		} else {
			if (oldValue[prop] !== value) {
				oldValue[prop] = value;
				$update(proxy);
			}
		}

		return true;
	}
};
/**
 * @type {ProxyHandler}
 */
const DeepProxy = {
	get: getArray(true),
	set(target, prop, value, proxy) {
		if (target[prop] !== value) {
			target[prop] = value;
			$update(proxy);
		}
		return true;
	}
};
/**
 * @type {ProxyHandler}
 */
const DeepShallowProxy = {
	get: DeepProxy.get,
	set(target, prop, value, proxy) {
		if (prop === "value") {
			if (target.value !== value) {
				target.value = value;
				$update(proxy);
			}
		} else if (target.value[prop] !== value) {
			target.value[prop] = value;
			$update(proxy);
		}

		return true;
	}
};

const MUTATOR_METHODS = new Set([
	'push', 'pop', 'shift', 'unshift',
	'splice', 'reverse', 'sort', 'copyWithin',
	'fill',
	// Map/Set
	'clear', 'set', 'add', 'delete'
]);

/**
 *
 * @param {Object} target
 * @param {string|symbol} prop
 * @param {Proxy} initialProxy
 * @returns {Proxy|*}
 */
function getDeep(target, prop, initialProxy) {
	const value = target[prop];
	if (typeof value !== "object" || value == null || isReactive(value)) return value;

	let newProxy = deepProxyCache.get(value);
	if (newProxy == null) {
		newProxy = new Proxy(value, {
			get: (target, prop) => getDeep(target, prop, initialProxy),
			set: (target, prop, value) => DeepProxy.set(target, prop, value, initialProxy)
		});
		deepProxyCache.set(value, newProxy);
	}
	return newProxy;
}

/**
 *
 * @param {boolean} deep
 */
function getArray(deep) {
	return (target, prop, proxy) => {
		if (dependCapture != null) dependCapture.add(proxy);

		if (Object.hasOwn(target, prop)) return target[prop];

		const array = target.value;
		let value = array[prop];

		if (typeof value === "function") {
			// 无法知道什么是不是数组，可能的类型太多了
			// NodeList TypedArray Array ...
			// 所以createArrayLike
			if (MUTATOR_METHODS.has(prop) && array.length != null && !isPureObject(array)) {
				return function () {
					let v = value.apply(array, arguments);
					$update(proxy);
					return v;
				};
			} else {
				return value.bind(array);
			}
		} else {
			return deep ? getDeep(array, prop, proxy) : value;
		}
	};
}

/**
 * 将对象转换为响应式代理（支持浅层/深度响应式）
 * @template T
 * @param {T} object - 需要代理的目标对象
 * @param {boolean} [deep=false] - 是否启用深度响应
 * @returns {Reactive<T>} 响应式代理对象
 */
function $state(object, deep) {
	if (isReactive(object)) return object;

	return new Proxy(
		{
			value: object,
			[$LISTENERS]: new Map()
		},
		deep ? DeepShallowProxy : ShallowProxy
	);
}

/**
 * 监听响应式变量变化
 * @param {Reactive<any>|Array<Reactive<any>>} objects - 要监听的响应式变量或数组
 * @param {() => Function|void} listener - 变化回调函数（可返回清理函数）
 * @param {boolean} [triggerNow=true] - 是否立即触发回调
 * @note 监听多个响应式变量时，不得返回不允许重复调用的清理函数，可能会被调用多次
 */
function $watch(objects, listener, triggerNow=true) {
	if (!Array.isArray(objects))
		objects = [objects];

	let cleanup;
	if (triggerNow) cleanup = listener();

	for (const object of objects) {
		object[$LISTENERS].set(listener, cleanup);
	}
}

/**
 * 自动清理工具函数
 * @param {Reactive} object
 * @param {ReactiveListener} listener
 * @param {HTMLElement} element
 */
function $watchOn(object, listener, element) {
	let cleanup = listener();
	object[$LISTENERS].set(listener, cleanup);
	$disposable(element, [object, listener]);
}

/**
 * 取消监听响应式变量变化
 * @param {Reactive} object - 要取消监听的响应式变量
 * @param {Function} listener - 之前注册的回调函数
 */
function $unwatch(object, listener) {
	const listeners = object?.[$LISTENERS];
	if (!listeners) return
	const cleanup = listeners.get(listener);
	listeners.delete(listener);
	if (updatePending) updatePending.delete(listener);
	if (typeof cleanup === "function") cleanup();
}

/**
 * @type {ProxyHandler}
 */
const DevComputeProxy = {
	get: ShallowProxy.get,
	set() { return false; },
	deleteProperty() { return false; }
};

/**
 * 创建计算属性（自动捕获依赖）
 * @template T
 * @param {(oldValue: T|undefined) => T|undefined} callback - 计算函数（接收旧值作为参数，可通过 this 访问可写上下文）
 * @param {boolean} [lazy=false] - 是否延迟计算直到访问时
 * @param {Array<Reactive<any>>|undefined} [dependencies=undefined] - 如果你的处理函数很复杂, 第一次调用不会访问所有的依赖, 那么可从这个数组指定
 * @returns {Readonly<Reactive<T>>} 只读的响应式计算属性
 *
 * @note 实现细节：
 * - 计算属性设计为只读，修改操作要么通过 callback 内的 this，要么通过callback的返回值
 * - callback内的this什么时候用得到，可以看示例的动画实现
 * - callback可以返回undefined(null不行)不触发响应式更新
 *   - $state 是用 === 判断是否和原值相等触发更新的
 * - 运行时与 $state 共用 Proxy 实现以减小体积，这意味着：
 *   - 开发环境返回的计算属性是只读的
 *   - 生产环境返回的计算属性无写保护
 * - 我只能期待你在开发环境把各种分支都测试到了（
 */
function $computed(callback, lazy, dependencies) {
	const prevCapture = dependCapture;
	if (!dependencies) dependCapture = new Set();

	const holder = { value: callback(), [$LISTENERS]: new Map() };

	if (!dependencies) {
		dependencies = [...dependCapture];
		dependCapture = prevCapture;
	}

	if (lazy) holder[$DIRTY] = false;

	let proxy;

	const doUpdate = () => $update(proxy);

	if (import.meta.env.DEV) {
		const WritableProxyForLazyCompute = new Proxy(holder, ShallowProxy);

		const updateValue = () => {
			const oldValue = holder.value;
			const newValue = callback.call(WritableProxyForLazyCompute, oldValue);
			if (newValue === undefined) return;
			holder.value = newValue;

			$unwatch(oldValue, doUpdate);
			if (isReactive(newValue)) {
				_devError("不建议在计算属性中返回响应式属性 (不过好像也没有什么实际上问题)", newValue)
				$watch(newValue, doUpdate);
			} else {
				$update(proxy);
			}
		};

		proxy = new Proxy(holder, lazy ? {
			get: (target, prop, proxy) => {
				if (target[$DIRTY]) {
					target[$DIRTY] = false;
					updateValue();
				}
				return ShallowProxy.get(target, prop, proxy);
			},
			set() { return false; },
			deleteProperty() { return false; }
		} : DevComputeProxy);

		$watch(dependencies, lazy ? () => holder[$DIRTY] = true : updateValue, false);
	} else {
		const updateValue = () => {
			const oldValue = holder.value;
			const newValue = callback.call(proxy, oldValue);
			if (newValue === undefined) return;
			holder.value = newValue;

			$unwatch(oldValue, doUpdate);
			if (isReactive(newValue)) {
				$watch(newValue, doUpdate);
			} else {
				$update(proxy);
			}
		};

		proxy = new Proxy(holder, lazy ? {
			get: (target, prop, proxy) => {
				if (target[$DIRTY]) {
					target[$DIRTY] = false;
					updateValue();
				}
				return ShallowProxy.get(target, prop, proxy);
			}
		} : ShallowProxy);

		$watch(dependencies, lazy ? () => holder[$DIRTY] = true : updateValue, false);
	}

	return proxy;
}

/**
 * 事件监听函数
 * @typedef {function(): function|void} ReactiveListener
 */

/**
 * @type {null|Map<ReactiveListener, array<Map<ReactiveListener, function>>>}
 */
let updatePending;
/**
 * @type {function(): void}
 */
let batchUpdate;
if (import.meta.env.DEV) {
	let allUpdated;
	let cyclicDepend = new Map();
	/**
	 * @type {Map}
	 */
	const cyclicChecker = {
		set(k, v) {
			cyclicDepend.set(k, v);
			// 把新的也加入总列表
			allUpdated.set(k, v);
		},
		get(k) {
			let x = cyclicDepend.get(k);
			if (!x && allUpdated.has(k)) {
				throw new Error("循环依赖");
			}
			return x;
		},
		delete(k) {
			cyclicDepend.delete(k);
		}
	};
	batchUpdate = () => {
		let updated = updatePending;

		// 如果是不在递归
		if (!allUpdated) allUpdated = new Map(updated);
		else cyclicDepend = new Map();

		updatePending = cyclicChecker;

		for (const [listener, owners] of updated) {
			try {
				const cleaner = listener();
				for (const owner of owners) {
					owner.set(listener, cleaner);
				}
			} catch (e) {
				if (e !== LAZY_DISPOSE_FALLBACK) {
					if (import.meta.env.DEV) {
						_devError("事件派发失败", e, listener);
					} else {
						console.error("事件派发失败", e, listener);
					}
				} else {
					if (import.meta.env.DEV) {
						_devError("响应式元素被外部移除，并且未取消%O的监听器，应使用$dispose(%O)", owners, listener);
					}
				}

				for (const owner of owners) {
					owner.delete(listener);
				}
			}
		}

		if (cyclicDepend.size) {
			// 如果递归，那么不清空【已经更新过的变量】
			updatePending = cyclicDepend;
			queueMicrotask(batchUpdate);
		} else {
			updatePending = allUpdated = null;
		}
	};
} else {
	batchUpdate = () => {
		let updated = updatePending;
		updatePending = null;

		for (const [listener, owners] of updated) {
			const cleaner = listener();
			for (const owner of owners) {
				owner.set(listener, cleaner);
			}
		}
	};
}

/**
 * 强制触发响应式变量更新
 * @param {Reactive<any>|Array<Reactive<any>>} objects - 要更新的响应式变量或数组
 * @note 提供多个变量时，多个变量共有的监听器只触发一次
 */
function $update(objects) {
	if (!Array.isArray(objects))
		objects = [objects];

	if (!updatePending) {
		updatePending = new Map();
		queueMicrotask(batchUpdate);
	}

	for (const object of objects) {
		const listeners = isReactive(object);
		for (const [listener, cleanup] of listeners) {
			if (typeof cleanup === "function") cleanup();
			let owners = updatePending.get(listener);
			if (!owners) {
				updatePending.set(listener, owners = new Set());
			}
			owners.add(listeners);
		}
	}
}
//endregion
export {isReactive, unconscious, isPureObject};
export {$dispose, $disposable, $state, $watch, $watchOn, $unwatch, $computed, $update};
//region 组件核心 (由Babel导入)
/**
 * 创建组件
 * @param {Component} component
 * @param {Object<string, any>} props
 * @param {...Renderable|Fragment} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {Renderable|Readonly<Reactive<Renderable>>}
 */
function createComponent(component, props, ...children) {
	return new component(Object.freeze(props), Object.freeze(children));
}

/**
 * 一个受监控的$state，函数组件使用它标记需要被转移的值
 * @template T
 * @param {T} t
 * @returns {T}
 */
function preserveState(t) {
	return import.meta.env.DEV ? __HMR.transferState(t, arguments[1], arguments[2]) : t;
}

/**
 * 监听响应式变量变化
 * 由Babel处理的自动卸载事件监听器
 * 用于函数组件内部
 * @param {Reactive<any>|Array<Reactive<any>>} objects - 要监听的响应式变量或数组
 * @param {() => Function|void} listener - 变化回调函数（可返回清理函数）
 * @param {boolean} [triggerNow=true] - 是否立即触发回调
 * @see $watch
 */
function $watchWithCleanup(objects, listener, triggerNow) {
	throw new Error("assertion failure");
}

//endregion
export {createComponent, preserveState, $watchWithCleanup};

/**
 * @typedef PropertyCheckDecl
 * @property {any} type=null
 * @property {boolean} required=false
 */

/**
 *
 * @param {Array} props
 * @param {Object<string, PropertyCheckDecl>} declaration
 */
function checkProps(props, declaration) {
	if (!import.meta.env.DEV) return;

}

/**
 * @template T
 * @param {Reactive<T>|Object} t
 * @return {Reactive<T>}
 */
function assertReactive(t) {
	if (import.meta.env.DEV && !isReactive(t)) {
		throw new Error(t+" is not reactive");
	}
	return t;
}

export {checkProps, assertReactive};

//region 组件热重载实现
function _devUpdateComponentRef(proxy, instance) {
	if (isReactive(instance)) {
		const listener = () => {
			proxy.value = instance.value;
			$update(proxy);
		};
		$watch(instance, listener);
		proxy.$key = [instance, listener];
	}
}

/**
 * @typedef {Object} StateRepo
 * @property {any[]} props
 * @property {Map<string, any>} prevStates=null
 * @property {Map<string, any>} states
 */

if (import.meta.hot) {
	window.__HMR = {
		/**
		 * @type {Map<Object, [Renderable, Reactive<Renderable>, StateRepo][]>}
		 */
		instances: new Map(), // 模块ID -> 组件实例集合

		/**
		 * @type {string|null}
		 */
		reloading: null,
		/**
		 * @type {StateRepo|null}
		 */
		reloadingState: null,

		/**
		 * @param {IArguments} args
		 * @param {number} argc
		 * @param {string} moduleId
		 */
		checkArgument(args, argc, moduleId) {
			const props = args[0];
			if (Object.keys(props).length && argc < 1)
				throw new Error("模块"+moduleId+"不允许包含属性！");
			const children = args[1];
			if (children?.length && argc < 2)
				throw new Error("模块"+moduleId+"不允许子元素！");
		},

		/**
		 * @param {IArguments} args
		 * @param {string} moduleId
		 * @return {StateRepo}
		 */
		createStateRepo(args, moduleId) {
			return this.reloading === moduleId ? this.reloadingState : {
				props: Array.from(args),
				states: new Map()
			};
		},

		/**
		 *
		 * @template T
		 * @param {T} value
		 * @param {string} stateId
		 * @param {StateRepo} repo
		 * @return {T}
		 */
		transferState(value, stateId, repo) {
			if (!stateId) throw new Error("Missing parameter, is preprocessor working?");
			repo.states.set(stateId, value);
			if (repo.prevStates?.has(stateId)) {
				return repo.prevStates.get(stateId);
			}
			return value;
		},

		/**
		 *
		 * @param {string} moduleId
		 * @param {StateRepo} stateRepo
		 * @param {Renderable|Reactive<Renderable>} instance
		 * @return {Reactive<Renderable>}
		 */
		register(moduleId, stateRepo, instance) {
			if (this.reloading === moduleId) return instance;

			if (!this.instances.has(moduleId)) {
				this.instances.set(moduleId, new Set());
			}

			const proxy = {
				value: instance,
				[$LISTENERS]: new Map()
			};

			_devUpdateComponentRef(proxy, instance);

			this.instances.get(moduleId).add([instance, proxy, stateRepo]);
			return proxy;
		},

		/**
		 *
		 * @param {string} moduleId
		 * @param {Module} newModule
		 */
		updateModule(moduleId, newModule) {
			if (!newModule) return;

			const component = newModule.default;
			const instances = this.instances.get(moduleId);
			const newInstances = new Set();
			this.instances.set(moduleId, newInstances);
			this.reloading = moduleId;

			instances?.forEach(([instance, proxy, stateRepo]) => {
				if (proxy.$key) {
					$unwatch(proxy.$key[0], proxy.$key[1]);
					delete proxy.$key;
				}

				if (!proxy.value.isConnected) return;

				this.reloadingState = stateRepo;
				stateRepo.prevStates = stateRepo.states;
				stateRepo.states = new Map();

				let newInstance;
				try {
					newInstance = component.apply(null, stateRepo.props);
				} catch (e) {
					location.reload();
				}

				this.reloadingState = null;

				for (const key of stateRepo.states.keys()) {
					if (!stateRepo.prevStates.has(key)) {
						console.info("New state "+key+" added, no parity, reload.")
						location.reload();
					}
				}

				stateRepo.states = stateRepo.prevStates;

				proxy.value = newInstance;

				newInstances.add([
					newInstance,
					proxy,
					stateRepo
				]);

				_devUpdateComponentRef(proxy, proxy.value);

				// 触发更新事件，替换DOM节点，并卸载旧组件
				$update(proxy);
			});

			this.reloading = null;
		}
	};
}
//endregion

//region foreach列表
/**
 * 创建按需更新的列表，复用现有DOM元素
 * @template T
 * @param {Reactive<T[]>} list - 响应式列表
 * @param {(item: T, index: number) => Renderable} renderItem - 生成列表项元素的函数 (item, index) => Element
 * @param {(item: T, index: number) => any} [keyFunc] - 生成唯一标识的函数 (item, index) => any (默认使用item)
 * @returns {DocumentFragment} 包含动态列表的文档片段
 */
function $foreach(list, renderItem, keyFunc = (item) => item) {
	let parent = document.createDocumentFragment();
	let offset = 0;
	const currentKeys = new Map(); // key到DOM节点的映射

	const callback = () => {
		const newItems = unconscious(list);
		const newKeys = newItems.map(keyFunc).map(unconscious);
		const newKeySet = new Set(newKeys);

		// 处理删除
		for (const [key, node] of currentKeys) {
			if (!newKeySet.has(key)) {
				$dispose(unconscious(node));
				currentKeys.delete(key);
			}
		}

		// 处理新增/移动
		newItems.forEach((item, newIndex) => {
			const key = newKeys[newIndex];

			let node = currentKeys.get(key);
			if (!node) {
				// 创建新元素
				node = renderItem(unconscious(item), newIndex);

				currentKeys.set(key, node);
			}

			// 调整位置
			const nodeList = parent.childNodes;
			if (nodeList[newIndex+offset] !== unconscious(node)) {
				const referenceNode = newIndex < nodeList.length-offset
					? nodeList[newIndex+offset]
					: null;

				if (isReactive(node)) {
					const reactiveNode = node;
					$watch(reactiveNode, () => {
						let orig = reactiveNode.value;
						parent.replaceChild(orig, node);
						node = orig;
					}, false);
					node = unconscious(node);
				}
				parent.insertBefore(node, referenceNode);
			}
		});
	};

	parent.addEventListener("append", e => {
		parent = e.detail;
		offset = parent.childElementCount;
		if (!import.meta.env.DEV || isReactive(list)) {
			$watch(list, callback);
			$disposable(parent, [list, callback]);
		} else {
			_devError("你不需要对非响应式元素应用$foreach, Array.prototype.map即可");
			callback();
		}
	}, {once:true});
	return parent;
}

/**
 * 创建按需更新的列表，复用现有DOM元素
 * @template T
 * @param {Reactive<T[]>} list - 响应式列表
 * @param {(item: T, index: number) => Renderable} renderItem - 生成列表项元素的函数 (item, index) => Element
 * @param {Renderable} emptyElement 列表为空时显示的元素
 * @returns {Readonly<Reactive<JSX.Element>>}
 */
function $forElse(list, renderItem, emptyElement) {
	let template = null;
	return $computed(oldValue => {
		const value = unconscious(list);

		if (value.length && !template)
			template = $foreach(list, renderItem);

		return value.length ? template : emptyElement
	});
}

/**
 *
 * @template T
 * @param {ReactivePromise<T[]>} state - 异步响应式列表
 * @param {(item: T, index: number) => Renderable} renderItem - 生成列表项元素的函数 (item, index) => Element
 * @param {Renderable} emptyElement 列表为空时显示的元素
 * @param  {Renderable} loadingElement
 * @param  {(error: boolean | any) => Renderable} errorElement=null
 * @returns {Readonly<Reactive<JSX.Element>>}
 */
function $forElseAsyncState(state, renderItem, emptyElement, loadingElement, errorElement) {
	let template = null;
	return $computed(oldValue => {
		if (state.loading) return loadingElement;

		let list = unconscious(state);

		if (state.error) {
			if (errorElement) return errorElement(state.error);
			list = [];
		}

		if (list.length && !template)
			template = $foreach(state, renderItem);

		return list.length ? template : emptyElement
	});
}
//endregion
export {$foreach, $forElse, $forElseAsyncState};
//region 动画管理器
const $ANIM_CANCEL = debugSymbol("AnimationCancel");
const $ANIM_TIMER = debugSymbol("AnimationTimer");

/**
 * CSS动画管理器
 * @typedef {Object} AnimationOptions
 * @property {number} duration - 动画时长（毫秒）
 * @property {'ease'|'linear'|'ease-in'|'ease-out'|'ease-in-out'|string} [easing='linear'] - 补间函数
 * @property {Object<string, string|number>} from - 起始样式
 * @property {Object<string, string|number>} to - 结束样式
 * @property {boolean} acceptCancel - 需要接受取消
 */

/**
 * 执行CSS动画
 * @param {HTMLElement|Reactive<HTMLElement>} ref - 要执行动画的元素
 * @param {Partial<AnimationOptions>} options - 动画配置
 * @returns {Promise<boolean>} 动画完成Promise
 */
function $animate(ref, options) {
	return new Promise(resolve => {
		const element = unconscious(ref);

		cancelAnimation(element);

		Object.assign(element.style, options.from);

		element.style.transition = Object.entries(options.to)
			.map(([prop]) => `${prop} ${options.duration}ms ${options.easing || 'linear'}`)
			.join(', ');

		if (options.acceptCancel)
			element[$ANIM_CANCEL] = resolve;
		// 等待浏览器下一帧确保过渡存在初值
		element[$ANIM_TIMER] = setTimeout(() => {
			Object.assign(element.style, options.to);

			element[$ANIM_TIMER] = setTimeout(() => {
				resolve(true);
				cancelAnimation(element);
			}, options.duration);
		});
	});
}

/**
 * 中止动画
 * @param {HTMLElement} element - 要中止动画的元素
 * @note 返回的Promise将以false作为信息成功
 */
function cancelAnimation(element) {
	element[$ANIM_CANCEL]?.(false);
	clearTimeout(element[$ANIM_TIMER]);
	delete element[$ANIM_CANCEL];
	delete element[$ANIM_TIMER];
	element.style.transition = '';
}
//endregion
export {$animate, cancelAnimation};
//region 状态存储
/**
 * 全局状态存储
 * @template T
 * @typedef {Reactive<T> & Readonly<{
 *   $serialize: function(T): string,
 *   $deserialize: function(string): T,
 *   $snapshot: function(): T,
 *   $dispose: function(): void
 * }>} ObjectStore
 * @property $dispose 删除该存储
 * @property $snapshot 获取一个深克隆的快照状态，稍后你可以赋值value以恢复
 */

/**
 * @type {Map<string, ObjectStore>}
 */
const stores = new Map();
/**
 * @type {Map<string, ObjectStore>}
 */
let dirtyStore;

function _doSaveStore() {
	for(const [k, v] of dirtyStore) {
		localStorage.setItem(k, v.$serialize(v.value));
	}
	dirtyStore.clear();
}
function initPersistStore() {
	dirtyStore = new Map();

	window.addEventListener('storage', (e) => {
		const reactive = stores.get(UC_PERSIST_STORE+":"+e.key);
		if (reactive) reactive.value = reactive.$deserialize(e.newValue);
	});

	if (UC_IMMEDIATE_STORE) return;

	window.addEventListener('beforeunload', _doSaveStore);
	document.addEventListener('visibilitychange', () => {
		if (document.hidden) _doSaveStore();
	});
	setInterval(_doSaveStore, 60000);
}

/**
 * 创建或获取响应式全局状态存储
 *
 * @template T
 * @param {string} key - 存储的唯一标识符 (相同key返回同一实例)
 * @param {T | (() => T)} initializer - 初始值或初始化函数
 * @param {Object} [options] - 配置选项
 * @param {function(T):string} [options.serialize=JSON.stringify] - 序列化方法
 * @param {function(string):T} [options.deserialize=JSON.parse] - 反序列化方法
 * @param {string} [options.namespace=UC_PERSIST_STORE] - 存储命名空间
 * @param {boolean} [options.persist=false] - 是否持久化到localStorage
 * @param {boolean} [options.deep=true] - 深度代理
 * @returns {ObjectStore<T>} 响应式存储对象
 *
 * @example 基础用法
 * const userStore = $store('user', { name: 'Guest' }, { persist: true });
 * userStore.value.name = 'Alice';
 *
 * @note 特性说明
 * - 自动跨标签页同步 (仅限同源页面)
 * - 持久化数据在初始化时自动加载
 * - 修改数据自动触发持久化 (通过vite变量注入配置立即保存或后台保存)
 * - 通过 $dispose() 可手动销毁存储
 */
function $store(key, initializer, options = {}) {
	if (stores.has(key)) return stores.get(key);

	const {
		serialize = JSON.stringify,
		deserialize = JSON.parse,
		namespace = UC_PERSIST_STORE,
		persist = false,
		deep = true
	} = options;

	const scopedKey = `${namespace}:${key}`;
	let rawState;

	// 读取持久化数据
	hasData: {
		if (persist) {
			try {
				const saved = localStorage.getItem(scopedKey);
				if (saved !== null) {
					rawState = deserialize(saved);
					break hasData;
				}
			} catch (e) {
				console.error(`无法读取 ${scopedKey}:`, e);
			}
		}

		// 初始化状态
		rawState = typeof initializer === 'function' ? initializer() : initializer;
	}

	let handleStorage;
	/**
	 * @type {ObjectStore}
	 */
	const state = {
		value: rawState,
		$serialize: serialize,
		$deserialize: deserialize,
		$snapshot: () => deserialize(serialize(state.value)),
		$dispose: () => {
			stores.delete(key);
			window.removeEventListener('storage', handleStorage);
		},
		[$LISTENERS]: new Map()
	};
	const reactive = new Proxy(state, deep ? DeepShallowProxy : ShallowProxy);

	// 持久化监听
	if (persist) {
		if (!dirtyStore) initPersistStore();

		$watch(reactive, () => {
			if (UC_IMMEDIATE_STORE && !dirtyStore.length)
				queueMicrotask(_doSaveStore);
			dirtyStore.set(scopedKey, state);
		}, false);
	}

	stores.set(key, reactive);
	return reactive;
}
//endregion
export {$store};
//region 异步状态
/**
 * @template T
 * @typedef {Reactive<T | null> & Readonly<{
 *   loading: boolean,
 *   error: boolean | any
 * }>} ReactivePromise 异步Promise
 * @property loading 异步操作是否正在进行
 * @property error 异步操作是否失败
 * @property value 异步操作上一次成功时的结果(初始为null)
 */

/**
 * 创建一个异步状态对象，自动处理加载和错误状态
 * @template T
 * @template R
 * @param {(arg: T) => Promise<R>} fetcher - 异步数据获取函数，接收参数并返回 Promise
 * @param {T | Reactive<T>} [value] - 静态或响应式参数
 * @returns {ReactivePromise<R>} 响应式状态对象
 *
 * @example
 * // 基本用法
 * const params = $state(1);
 * const state = $asyncState(async (page) => {
 *   const res = await fetch(`/api?page=${page}`);
 *   return res.json();
 * }, params);
 *
 * // 在组件中使用
 * <div>
 *   {state.loading ? '加载中...' : state.error || state.value}
 * </div>
 *
 * @example
 * // 静态参数用法
 * const userState = $asyncState(async (id) => {
 *   return getUserProfile(id);
 * }, 123);
 *
 * @note
 * 特性说明：
 * 1. 当参数值变化时自动重新获取数据 (参数需为响应式对象)
 * 2. 保持旧数据直到新数据加载完成，避免界面闪烁
 * 3. 自动管理 loading 和 error 状态
 * 4. 返回完全响应式的代理对象，可直接在模板中使用
 */
function $asyncState(fetcher, value) {
	/**
	 * @type {ReactivePromise}
	 */
	const state = {
		value: null,
		[$LISTENERS]: new Map()
	};
	const proxy = new Proxy(state, ShallowProxy);

	const callback = () => {
		state.loading = true;
		state.error = false;
		// 直接触发更新不修改proxy.value
		// 这保证了加载新数据时, 旧数据依然可用, 从而不会给用户展示很多次Loading
		$update(proxy);

		fetcher(unconscious(value)).then(data => {
			state.loading = false;
			proxy.value = data;
		}).catch(error => {
			state.loading = false;
			state.error = error || true;
			$update(proxy);
		});
	};

	if (isReactive(value)) $watch(value, callback);
	// 类似初始化的场景
	else callback();

	return proxy;
}
//endregion
export {$asyncState};
//region 异步加载的组件
/**
 * 异步导入的组件模块
 * @typedef {Object} ImportedComponent
 * @property {Component} default
 */

/**
 * 异步加载组件
 * @param {function(): Promise<ImportedComponent | Component>} loader - 组件加载函数
 * @param {Object} [options] - 配置选项
 * @param {function(): Renderable} [options.loading='加载中'] - 加载时显示的组件
 * @param {function(error: boolean | any): Renderable} [options.error='加载失败'] - 出错时显示的组件
 * @returns {Component}
 */
function $asyncComponent(loader, options = {}) {
	const state = $asyncState(
		async () => {
			const module = await loader();
			return module.default || module;
		}
	);

	return function (props, children) {
		function createInstance() {
			if (state.error) return options.error(state.error) || "加载失败";
			return createComponent(state.value, props, ...children);
		}
		return state.loading ? $computed(() => {
			if (state.loading) return options.loading() || "加载中...";
			return createInstance();
		}) : createInstance();
	}
}
//endregion
export {$asyncComponent};



console.log(
	'%cUnconscious'+
	'%cv'+VERSION+
	'%cby Roj234',

	'color: #fff; background-color: #007BFF; font-size: 18px; padding: 5px 10px;',
	'color: #FF7B00; background-color: #7B00FF; font-size: 18px; padding: 5px 10px;',
	'color: #00FF7B; font-weight: bold; font-size: 18px; padding: 5px 10px;'
);
