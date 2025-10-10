"use strict";

import {normalizePath} from "vite";

import t from "@babel/types";
import {generate as babelGenerate} from '@babel/generator';
import babelModuleImports from "@babel/helper-module-imports";
import babelAnnotateAsPure from "@babel/helper-annotate-as-pure";
import {ID_CLASSLIST, ID_DANGEROUSLY_SET_INNERHTML, ID_EVENTHANDLER, ID_NAMESPACE, ID_STYLELIST} from "../constant.js";

const getContext = (pass, name) => pass.get('unconscious/babel-jsx/'+name);
const setContext = (pass, name, v) => pass.set('unconscious/babel-jsx/'+name, v);

/**
 *
 * @param {string} s
 * @return {string}
 */
function intellijCompat(s) {
	if (s === "dangerouslySetInnerHTML") return ID_DANGEROUSLY_SET_INNERHTML;
	if (s.startsWith("on")) return s.toLowerCase();
	return s === "className" ? "class" : s/*.toLowerCase()*/;
}

/**
 *
 * @param {string} char
 * @return {boolean}
 */
function isFirstCharUpperCase(char) {
	const charCode = char.charCodeAt(0);
	return charCode >= 65 && charCode <= 90;
}

/**
 *
 * @param {t.Expression[]} args
 * @param {import('VitePlugin.mjs').File} file
 * @param {number} i
 */
function transformJsxChildrenArguments(args, file, i) {
	for (; i < args.length; i++) {
		const x = args[i];
		if (t.isJSXSpreadChild(x)) {
			args[i] = t.spreadElement(x.expression);
		}
		if (t.isConditionalExpression(x)) {
			if (x.test?.callee?.name !== "AS_IS") {
				args[i] = t.callExpression(getContext(file, 'id/computed')(), [
					t.arrowFunctionExpression([], x)
				]);
			} else {
				x.test = x.test.arguments[0];
			}
		}
	}
}

function createPlugin(_, options) {
	// 【expr ?? default】 in ES2015
	const { modulePath = "unconscious" } = options;

	return {
		name: "unconscious",
		visitor: {
			Program: {
				enter(path, state) {
					// 按需导入
					lazyImport("id/one", "createElement");
					lazyImport("id/many", "createFragment");
					lazyImport("id/component", "createComponent");
					lazyImport("id/computed", "$computed");
					lazyImport("id/deco/left", "_left");
					lazyImport("id/deco/middle", "_middle");
					lazyImport("id/deco/right", "_right");
					lazyImport("id/deco/prevent", "_prevent");
					lazyImport("id/deco/stop", "_stop");
					lazyImport("id/deco/children", "_children");
					lazyImport("id/deco/delegate", "_delegate");
					lazyImport("id/eldeco/style", "_stylesBehaviour");
					lazyImport("id/eldeco/class", "_classesBehaviour");
					lazyImport("id/eldeco/className", "_classesBehaviour");
					lazyImport("id/watch", "$watch");
					lazyImport("id/disposable", "$disposable");
					function lazyImport(id, functionName) {
						setContext(state, id, () => {
							const ast = babelModuleImports.addNamed(path, functionName, modulePath, {
								importedInterop: "uncompiled",
								importPosition: "after"
							});

							setContext(state, id, () => t.cloneNode(ast));
							return ast;
						});
					}
				},

				exit(path, state) {
					const allowHMR = path.hub.file.metadata.allowHMR;
					if (!allowHMR) return;

					const components = getContext(state, "knownComponents")?.map(t.stringLiteral);
					if (!components) return;

					const ast = t.ifStatement(t.identifier("import.meta.hot"), t.expressionStatement(
						t.callExpression(t.identifier("import.meta.hot.accept"), [
							t.arrowFunctionExpression([t.identifier("newModule")],
								t.callExpression(t.identifier("__HMR.updateComponent"), [
									getFileName(path, state),
									t.identifier("newModule"),
									t.arrayExpression(components)
								]))
						])
					));
					path.pushContainer('body', ast);
				}
			},

			/**
			 * 添加调试信息
			 */
			JSXOpeningElement(path, state) {
				if (state.opts.envName !== "development") return;

				const attributes = [];
				if (isThisAllowed(path.scope)) {
					attributes.push(t.jsxAttribute(
						t.jsxIdentifier("__self"),
						t.jsxExpressionContainer(t.thisExpression())
					));
				}

				attributes.push(t.jsxAttribute(
					t.jsxIdentifier("__source"),
					t.jsxExpressionContainer(makeDebugInfo(path, state))
				));

				path.pushContainer("attributes", attributes);
			},

			JSXFragment: {
				exit(path, file) {
					// createFragment(...children)
					const args = t.react.buildChildren(path.node);
					transformJsxChildrenArguments(args, file, 0);
					const callExpr = call(file, "many", args);
					path.replaceWith(t.inherits(callExpr, path.node));
				}
			},
			JSXElement: {
				exit(path, file) {
					const node = path.get("openingElement");

					let methodName = "one", ast;

					const tag = node.node.name;
					const NAMESPACE_SHORTHAND = {
						svg: "http://www.w3.org/2000/svg"
					};
					const DEFAULT_NAMESPACE = {
						svg: "svg",
						path: "svg",
						g: "svg"
					};

					let tagName = tag.name, namespace;

					if (tagName.name) {
						namespace = tag.namespace.name;

						tagName = tagName.name;
					} else {
						namespace = DEFAULT_NAMESPACE[tagName];

						if (isFirstCharUpperCase(tagName)) {
							// 创建组件
							methodName = "component";
							ast = t.identifier(tagName);
						}
					}

					if (namespace) {
						node.pushContainer("attributes", t.jsxAttribute(
							t.jsxIdentifier(ID_NAMESPACE),
							t.stringLiteral(NAMESPACE_SHORTHAND[namespace] || namespace)
						));
					}

					const args = [ast ?? t.stringLiteral(tagName)];
					const ctx = {
						isHTMLElement: methodName !== "component",
						decorators: []
					}

					const props = buildAttributes(file, node.get("attributes"), ctx);
					if (props != null) args.push(props);

					const children = t.react.buildChildren(path.node);
					if (children.length) {
						if (props == null) {
							args.push(t.nullLiteral());
						}
						args.push(...children);
					}

					transformJsxChildrenArguments(args, file, 2);

					let callExpr = call(file, methodName, args);
					// FIXME 返回第一个，还是返回最后一个？
					if (ctx.ref) {
						callExpr = t.assignmentExpression("=", ctx.ref, callExpr);
					}

					for (const decorator of ctx.decorators) {
						const name = decorator.shift().name;
						let expr;
						if (getContext(file, 'id/eldeco/'+name) != null) {
							expr = getContext(file, 'id/eldeco/'+name)();
						} else {
							expr = t.identifier(name);
						}
						callExpr = t.callExpression(expr, [callExpr, ...decorator]);
					}

					path.replaceWith(t.inherits(callExpr, path.node));
				}
			},

			// 处理函数声明和箭头函数表达式
			FunctionExpression: unwatchOnDispose,
			ArrowFunctionExpression: unwatchOnDispose,
			FunctionDeclaration: unwatchOnDispose,

			ExportDefaultDeclaration(path, state) {
				const development = state.opts.envName === "development";
				if (!development) return;

				const decl = path.node.declaration;
				if (t.isFunctionDeclaration(decl)) {
					componentHMR('default', path.get('declaration'), state);
				}
			},
			ExportNamedDeclaration(path, state) {
				const development = state.opts.envName === "development";
				if (!development) return;

				const decl = path.node.declaration;
				if (t.isFunctionDeclaration(decl)) {
					const componentId = decl.id.name;
					componentHMR(componentId, path.get('declaration'), state);
				}

				if (t.isVariableDeclaration(decl)) {
					let i = 0;
					for (const declarator of decl.declarations) {
						if (declarator.init) {
							const componentId = declarator.id.name;

							if (t.isArrowFunctionExpression(decl)) {
								componentHMR(componentId, path.get('declaration.declarations.'+i+'.init'), state);
							}
							if (t.isFunctionDeclaration(decl)) {
								componentHMR(componentId, path.get('declaration.declarations.'+i+'.init'), state);
							}
						}

						i++;
					}
				}
			},
		}
	};
}

function componentHMR(componentId, path, state) {
	const {node} = path;

	const name = componentId === "default" ? node.id.name : componentId;
	if (!isFirstCharUpperCase(name) || name.length === 1) return;

	let components = getContext(state, "knownComponents");
	if (components == null) {
		components = [];
		setContext(state, "knownComponents", components);
	}
	components.push(componentId);

	if (node.params.length > 2) {
		throw path.buildCodeFrameError('组件函数声明至多具有两个参数');
	}

	let stateRepoName = path.scope.generateUidIdentifier("stateRepo");

	node.body.body.unshift(t.variableDeclaration("const", [
		t.variableDeclarator(stateRepoName,
			t.callExpression(t.identifier("__HMR.getPreviousState"), [
				t.identifier("arguments"),
				t.numericLiteral(node.params.length),
				getFileName(path, state),
				t.stringLiteral(componentId)
			]))
	]));

	let counter = new Map();
	const rootPath = path;
	path.traverse({
		Scope(path) {
			if (path.scope !== rootPath.scope) {
				path.skip();
			}
		},

		ReturnStatement(path) {
			if (!path.get('argument').node) {
				throw path.buildCodeFrameError('组件必须返回"值"');
			}

			const originalArg = path.node.argument;
			path.node.argument = t.callExpression(
				t.identifier("__HMR.wrapComponent"),
				[
					getFileName(rootPath, state),
					t.stringLiteral(componentId),
					stateRepoName,
					originalArg
				]
			);
		},

		CallExpression(path) {
			const {callee, arguments: args} = path.node;

			if (!t.isIdentifier(callee) || callee.name !== 'preserveState') return;
			const binding = path.scope.getBinding("preserveState");
			if (binding.kind !== "module" || binding.path.parent.source.value !== "unconscious") return;

			if (args.length !== 1 && args.length !== 2) {
				throw path.buildCodeFrameError('preserveState只允许提供1或2个参数');
			}

			if (args.length === 1) {
				const code = babelGenerate(args[0]).code;
				const index = counter.get(code) || 0;
				counter.set(code, index + 1);
				args.push(t.stringLiteral(code+"_"+index));
			}

			args.push(stateRepoName);
		}
	});
}

/**
 *
 * @param path
 * @param {import('VitePlugin.mjs').PluginPass} pass
 */
function unwatchOnDispose(path, pass) {
	const body = path.get('body');
	if (!body.isBlockStatement()) return;

	const cleanupCalls = [];
	const dependenciesMap = new Map();
	let returnStatement = null;

	// 第一步：查找所有 $watchWithCleanup 调用
	body.traverse({
		CallExpression(callPath) {
			const callee = callPath.get('callee');
			if (!callee.isIdentifier({ name: '$watchWithCleanup' })) return;

			const binding = callPath.scope.getBinding("$watchWithCleanup");
			if (binding.kind !== "module" || binding.path.parent.source.value !== "unconscious") return;

			// 提取参数
			const [listArg, callbackArg] = callPath.node.arguments;

			// 生成唯一回调名称
			const callbackId = path.scope.generateUidIdentifier("callback");

			// 替换原调用为 $watch
			callPath.replaceWith(
				t.callExpression(
					getContext(pass, 'id/watch')(),
					[listArg, callbackId]
				)
			);

			// 收集依赖项和回调声明
			cleanupCalls.push({
				list: listArg,
				callbackId,
				callback: callbackArg
			});

			dependenciesMap.set(callbackId.name, {
				list: listArg,
				callbackId
			});

			callPath.skip();
		},

		ReturnStatement(retPath) {
			if (!returnStatement) {
				returnStatement = retPath;
			}
		}
	});

	if (cleanupCalls.length === 0) return;

	// 第二步：生成回调函数声明
	const variableDeclarations = cleanupCalls.map(({ callbackId, callback }) => {
		return t.variableDeclarator(
			callbackId,
			callback
		);
	});

	body.unshiftContainer(
		'body',
		t.variableDeclaration('const', variableDeclarations)
	);

	// 第三步：处理返回语句
	if (returnStatement) {
		const returnArg = returnStatement.get('argument');
		const returnValueId = path.scope.generateUidIdentifier("returnValue");

		// 替换原始返回值为变量
		returnStatement.insertBefore(
			t.variableDeclaration('const', [
				t.variableDeclarator(
					returnValueId,
					returnArg.node
				)
			])
		);

		// 生成 $disposable 调用参数
		const dependencies = Array.from(dependenciesMap.values()).flatMap(v => [
			v.list,
			v.callbackId
		]);

		returnStatement.insertBefore(
			t.expressionStatement(
				t.callExpression(
					getContext(pass, 'id/disposable')(),
					[
						returnValueId,
						t.arrayExpression(dependencies)
					]
				)
			)
		);

		// 替换原始返回值为变量
		returnArg.replaceWith(returnValueId);
	}
}

//region createElement语句生成
function buildAttributes(file, attribs, ctx) {
	const props = [];
	for (const attr of attribs) {
		accumulateAttribute(file, props, attr.node, ctx);
	}

	return props.length === 1
	&& t.isSpreadElement(props[0])
	&& !t.isObjectExpression(props[0].argument)

		? props[0].argument
		: props.length > 0
			? t.objectExpression(props)
			: null;
}

const TRUE = t.booleanLiteral(true);

/**
 *
 * @param pass
 * @param array
 * @param attribute
 * @param ctx
 */
function accumulateAttribute(pass, array, attribute, ctx) {
	function hasProto(node) {
		return node.properties.some(value => t.isObjectProperty(value, {
			computed: false,
			shorthand: false
		}) && (t.isIdentifier(value.key, {
			name: "__proto__"
		}) || t.isStringLiteral(value.key, {
			value: "__proto__"
		})));
	}

	if (t.isJSXSpreadAttribute(attribute)) {
		const arg = attribute.argument;
		if (t.isObjectExpression(arg) && !hasProto(arg)) {
			// 优化 {...{a:b}} 不过真的有必要吗？？写出这种代码的程序员应该自裁
			array.push(...arg.properties);
		} else {
			array.push(t.spreadElement(arg));
		}
		return;
	}

	// 处理value
	let value = attribute.value;
	if (value) {
		if (t.isJSXExpressionContainer(value)) {
			value = value.expression;
		} else {
			// stringLiteral
			value.value = value.value.replace(/\r\n\s+/g, " ");
			delete value.extra?.raw;
		}
	} else {
		value = TRUE;
	}

	let key = attribute.name;
	if (key._uc_isDecorator) {
		ctx.decorators.push(value === TRUE ? [key] : [key, value]);
		return;
	}

	// 处理name

	if (t.isJSXNamespacedName(key)) {
		let namespace = key.namespace.name;
		if (key.name.name === "reactive") {
			ctx.decorators.push([key.namespace, value]);
			return;
		}

		if (namespace === "class") namespace = ID_CLASSLIST;
		else if (namespace === "style") namespace = ID_STYLELIST;
		else namespace += ":";
		// abc:def = ...
		key = t.stringLiteral(namespace+key.name.name);
	} else {
		if (key.name === "ref") {
			ctx.ref = value;
			return;
		}

		// FIXME add robust!
		// Preserve original case for ID_NAMESPACE and other special identifiers
		if (ctx.isHTMLElement && key.name !== ID_NAMESPACE && key.name !== ID_CLASSLIST && key.name !== ID_STYLELIST && key.name !== ID_EVENTHANDLER) {
			key.name = intellijCompat(key.name);
		}

		if (key._uc_names) {
			// onclick.left
			const arr = key._uc_names;

			function isEventProperty(name) {
				return name === "once" || name === "passive" || name === "capture";
			}

			const eventProperties = [];

			for (let i = 0; i < arr.length; i++) {
				const decorator = arr[i];
				if (isEventProperty(decorator)) {
					eventProperties.push(t.objectProperty(
						t.identifier(decorator),
						t.booleanLiteral(true)
					));
				} else {
					let expr;
					if (getContext(pass, 'id/deco/'+decorator) != null) {
						expr = getContext(pass, 'id/deco/'+decorator)();
					} else {
						expr = t.identifier(decorator);
					}

					const otherArg = Array.isArray(arr[i+1]) ? arr[++i] : [];
					value = t.callExpression(expr, [value, ...otherArg]);
				}
			}

			if (eventProperties.length) {
				value = t.arrayExpression([value, t.objectExpression(eventProperties)]);
			}

			key = t.identifier(key.name);
		}

		if (ctx.isHTMLElement && key.name.startsWith("on")) key.name = ID_EVENTHANDLER+key.name.substring(2);
		if (key.name.includes("-")) {
			// aria-xxx
			key = t.stringLiteral(key.name);
		} else {
			key.type = "Identifier";
		}
	}

	array.push(t.inherits(t.objectProperty(key, value), attribute));
}

function call(pass, name, args, pure = true) {
	const node = t.callExpression(getContext(pass, 'id/'+name)(), args);
	babelAnnotateAsPure.default(node);
	return node;
}
//endregion
//region 调试信息生成

function isDerivedClass(classPath) {
	return classPath.node.superClass !== null;
}
function isThisAllowed(scope) {
	do {
		const { path } = scope;
		if (path.isFunctionParent() && !path.isArrowFunctionExpression()) {
			if (!path.isMethod()) {
				return true;
			}
			if (path.node.kind !== "constructor") {
				return true;
			}
			return !isDerivedClass(path.parentPath.parentPath);
		}
		if (path.isTSModuleBlock()) {
			return false;
		}
	} while (scope = scope.parent);
	return true;
}

export function getFileName(path, state) {
	if (!state.fileNameIdentifier) {
		//const filename = state.filename ?? "";
		const { filename = "" } = state;

		const fileNameIdentifier = path.scope.generateUidIdentifier("_moduleId");
		path.scope.getProgramParent().push({
			id: fileNameIdentifier,
			init: t.stringLiteral(normalizePath(filename))
		});
		state.fileNameIdentifier = fileNameIdentifier;
	}

	//AST.cloneNode(fileNameIdentifier);
	return state.fileNameIdentifier;
}

function makeDebugInfo(path, state) {
	const location = path.node.loc;
	if (!location) return path.scope.buildUndefinedNode();

	return makeTrace(getFileName(path, state), location.start.line, location.start.column);
}

function makeTrace(fileName, lineNumber, column0Based) {
	const fileLineLiteral = lineNumber != null ? t.numericLiteral(lineNumber) : t.nullLiteral();
	const fileColumnLiteral = column0Based != null ? t.numericLiteral(column0Based + 1) : t.nullLiteral();
	return t.objectExpression([
		t.objectProperty(t.identifier("file"), fileName),
		t.objectProperty(t.identifier("line"), fileLineLiteral),
		t.objectProperty(t.identifier("column"), fileColumnLiteral)
	]);
}
//endregion

export default createPlugin;