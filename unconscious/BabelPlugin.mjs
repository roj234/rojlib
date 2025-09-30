"use strict";

import {normalizePath} from "vite";

import t from "@babel/types";
import {generate as babelGenerate} from '@babel/generator';
import babelModuleImports from "@babel/helper-module-imports";
import babelAnnotateAsPure from "@babel/helper-annotate-as-pure";
import {ID_CLASSLIST, ID_EVENTHANDLER, ID_NAMESPACE, ID_STYLELIST} from "./constant.js";

const getContext = (pass, name) => pass.get('unconscious/babel-jsx/'+name);
const setContext = (pass, name, v) => pass.set('unconscious/babel-jsx/'+name, v);

/**
 *
 * @param {string} s
 * @return {string}
 */
function intellijCompat(s) {
  return s === "className" ? "class" : s.toLowerCase();
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
      args[i] = t.callExpression(getContext(file, 'id/computed')(), [
        t.arrowFunctionExpression([], x)
      ]);
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
          lazyImport("id/eldeco/styles", "_stylesBehaviour");
          lazyImport("id/eldeco/classes", "_classesBehaviour");
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

          const development = state.opts.envName === "development";
          if (development) {
            // 追加：【元素是被谁创建的？】信息，包括文件和行号
            path.traverse(addDebugInfoVisitor, state);
          }
        },

        exit(path, state) {
          const development = state.opts.envName === "development";
          if (!development || 1 !== getContext(state, "isComponent")) return;

          const ast = t.ifStatement(t.identifier("import.meta.hot"), t.expressionStatement(
              t.callExpression(t.identifier("import.meta.hot.accept"), [
                  t.arrowFunctionExpression([t.identifier("newModule")],
                      t.callExpression(t.identifier("__HMR.updateModule"), [
                          getFileName(path, state),
                          t.identifier("newModule")
                      ]))
              ])
          ));
          path.pushContainer('body', ast);
        }
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
          const NAMESPACE_SHORTHEAD = {
            svg: "http://www.w3.org/2000/svg"
          };
          let tagName = tag.name, namespace = null;
          if (tagName.name) {
            node.pushContainer("attributes", t.jsxAttribute(
              t.jsxIdentifier(ID_NAMESPACE),
              t.stringLiteral(NAMESPACE_SHORTHEAD[tag.namespace.name] || tag.namespace.name)
            ));

            tagName = tagName.name;
          } else {
            if (isFirstCharUpperCase(tagName)) {
              // 创建组件
              methodName = "component";
              ast = t.identifier(tagName);
            }
          }

          const decorators = [];
          const args = [ast ?? t.stringLiteral(tagName)];

          const props = buildAttributes(file, node.get("attributes"), decorators);
          if (props != null) args.push(props);

          const children = t.react.buildChildren(path.node);
          if (children.length) {
            if (props == null) {
              args.push(t.nullLiteral());
            }
            args.push.apply(args, children);
          }

          transformJsxChildrenArguments(args, file, 2);

          let callExpr = call(file, methodName, args);
          for (const decorator of decorators) {
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
      ArrowFunctionExpression(path, state) {
        unwatchOnDispose(path, state);
      },
      FunctionExpression(path, state) {
        unwatchOnDispose(path, state);
      },
      FunctionDeclaration(path, state) {
        unwatchOnDispose(path, state);

        const development = state.opts.envName === "development";
        if (!development) return;

        const { node } = path;
        const name = node.id.name;
        if (!isFirstCharUpperCase(name) || name.length === 1) return;

        setContext(state, "isComponent", (getContext(state, "isComponent") ?? 0) + 1);

        if (node.params.length > 2) {
          throw path.buildCodeFrameError('组件函数声明至多具有两个参数');
        }
        if (node.params.length < 2) {
          node.body.body.unshift(t.callExpression(t.identifier("__HMR.checkArgument"), [
            t.identifier("arguments"),
            t.numericLiteral(node.params.length),
            getFileName(path, state)
          ]));
        }

        let stateRepoName = path.scope.generateUidIdentifier("_stateRepo");

        node.body.body.unshift(t.variableDeclaration("const", [
          t.variableDeclarator(stateRepoName,
              t.callExpression(t.identifier("__HMR.createStateRepo"), [
                t.identifier("arguments"),
                getFileName(path, state)
              ]))
        ]));

        let counter = new Map();
        const rootPath = path;
        path.traverse({
          ReturnStatement(path) {
            // Only transform returns in the direct component function scope, skip nested functions/closures
            if (path.scope !== rootPath.scope) return;

            if (!path.get('argument').node) {
              throw path.buildCodeFrameError('组件必须返回"值"');
            }

            const originalArg = path.node.argument;
            path.node.argument = t.callExpression(
                t.identifier("__HMR.register"),
                [
                  getFileName(rootPath, state),
                  stateRepoName,
                  originalArg
                ]
            );
          },

          CallExpression(path) {
            if (path.scope !== rootPath.scope) return;

            const { callee, arguments: args } = path.node;

            // TODO import精确匹配防同名
            if (!t.isIdentifier(callee) || callee.name !== 'preserveState') return;

            if (args.length !== 1 && args.length !== 2) {
              throw path.buildCodeFrameError('preserveState只允许提供1或2个参数');
            }

            if (args.length === 1) {
              const code = babelGenerate(args[0]).code;
              const index = counter.get(code) || 0;
              counter.set(code, index+1);
              args.push(t.stringLiteral(code+"_"+index));
            }

            args.push(stateRepoName);
          }
        });
      }
    }
  };
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
function buildAttributes(file, attribs, decorators) {
  const props = [];
  for (const attr of attribs) {
    accumulateAttribute(file, props, attr.node, decorators);
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

function accumulateAttribute(pass, array, attribute, decorators) {
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
    decorators.push(value === TRUE ? [key] : [key, value]);
    return;
  }

  // 处理name

  if (t.isJSXNamespacedName(key)) {
    let namespace = key.namespace.name;
    if (namespace === "class") namespace = ID_CLASSLIST;
    else if (namespace === "style") namespace = ID_STYLELIST;
    else namespace += ":";
    // abc:def = ...
    key = t.stringLiteral(namespace+key.name.name);
  } else {
    // FIXME add robust!
    // Preserve original case for ID_NAMESPACE and other special identifiers
    if (key.name !== ID_NAMESPACE && key.name !== ID_CLASSLIST && key.name !== ID_STYLELIST && key.name !== ID_EVENTHANDLER) {
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

    if (key.name.startsWith("on")) key.name = ID_EVENTHANDLER+key.name.substring(2);
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
const addDebugInfoVisitor = {
  JSXOpeningElement(path, state) {
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
  }
};

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

function getFileName(path, state) {
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