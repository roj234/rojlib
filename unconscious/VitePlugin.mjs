import {createFilter, normalizePath} from 'vite';
import fs from 'fs';
import path from 'path';
import {MIXIN_ID} from "./MyJSXParser.mjs";

import {parse as babelParse} from '@babel/parser';
import {generate as babelGenerate} from '@babel/generator';
import codeFrame from "@babel/code-frame";
import traverse from "@babel/traverse";

import HotModuleReplace from "./transformer/HotModuleReplace.js";
import RemoveSpecialImport from "./transformer/RemoveSpecialImport.js";
import UCTransformer from './transformer/Unconscious.js';
import SideEffectAnalyze from "./transformer/SideEffectAnalyze.js";

const DEFAULT_FILTER = /\.[jt]sx?$/;
const scriptPath = import.meta.dirname;


function findPackageJson(startDir) {
  let currentDir = path.resolve(startDir || process.cwd());
  const rootDir = path.parse(currentDir).root;

  while (1) {
    const packageJsonPath = path.join(currentDir, 'package.json');

    if (fs.existsSync(packageJsonPath)) {
      try {
        const packageJsonContent = fs.readFileSync(packageJsonPath, 'utf8');
        const packageData = JSON.parse(packageJsonContent);
        return {
          path: packageJsonPath,
          data: packageData
        };
      } catch (error) {
        throw new Error(`Found package.json at ${packageJsonPath} but failed to parse: ${error.message}`);
      }
    }

    if (currentDir === rootDir) break
    currentDir = path.dirname(currentDir);
  }

  return null;
}

/**
 *
 * @param {import('vite').FilterPattern} options.include=undefined
 * @param {import('vite').FilterPattern} options.exclude=undefined
 * @param {boolean} options.micro=false
 * @param {Object} options
 * @property {"unconscious"|"unconscious@micro"} options.modulePath
 * @return {import('vite').Plugin}
 */
export default (options = {}) => {
  const {
    include = DEFAULT_FILTER,
    exclude,
    plugins = [],
    micro = false
  } = options;

  let theLibrary;
  if (micro) {
    options.modulePath = "unconscious@micro";
    theLibrary = {'unconscious@micro': scriptPath+'/runtime_micro.js'};
  } else {
    theLibrary = {'unconscious': scriptPath+'/runtime.js'};
  }

  const config = {
    parseOptions: {},
    generateOptions: {},
    ...options,
    cwd: process.cwd(),
    plugins: [
      [SideEffectAnalyze],
      [UCTransformer, options],
      [RemoveSpecialImport],
      [HotModuleReplace],
      ...plugins].map(t => {
      if (Array.isArray(t)) return t[0](null, t[1]);
      return typeof t === "function" ? t(null, {}) : t;
    }),
    removeSpecialImport: {
      'unconscious': ['$watchWithCleanup']
    }
  };

  const customFilter = createFilter(include, exclude);
  return {
    name: "unconscious",
    enforce: 'pre',
    handleHotUpdate({ file, timestamp, modules, server }) {
      server.ws.send({
        type: 'custom',
        event: 'module-graph',
        data: {
          id: file,
          timestamp: timestamp,
          updated: modules.map(m => {
            return {
              id: m.id,
              parent: Array.from(m.importers).map(importer => importer.id),
              child: Array.from(m.importedModules).map(importer => importer.id)
            }
          })
        }
      });
    },
    config(userConfig, {mode}) {
      config.envName = mode;

      return {
        resolve: {
          alias: {
            'unconscious@shared': scriptPath+'/runtime_shared.js',
            'unconscious/ext': scriptPath+'/ext',
            ...theLibrary,
          }
        },

        define: {
          UC_PERSIST_STORE: userConfig?.define?.UC_PERSIST_STORE ?? JSON.stringify(findPackageJson()?.data.name ?? 'default'),
          UC_IMMEDIATE_STORE: userConfig?.define?.UC_IMMEDIATE_STORE ?? false,
          UC_REACTIVE_FRAGMENT: userConfig?.define?.UC_REACTIVE_FRAGMENT ?? false
        },

        // 依赖转换阶段
        optimizeDeps: {
          esbuildOptions: { plugins: [{
              name: "unconscious_prebuild",
              setup(build) {
                const namespace = "";

                build.onLoad({ filter: /.*/, namespace }, async (args) => {
                  if (args.path.startsWith(normalizePath(scriptPath))) {
                    if (!normalizePath(args.path.substring(scriptPath.length+1)).includes("/")) return;
                  }
                  else if (normalizePath(args.path).includes("/node_modules/")) return;
                  if (!customFilter(args.path)) return;

                  const code = await fs.promises.readFile(args.path, "utf8");
                  return {
                    contents: transform(code, { filename: args.path, ...config }).code
                  };
                });
              }
          }] },

          rollupOptions: { plugins: [{
            name: "unconscious_prebuild",
            async load(path) {
              if (path.startsWith(scriptPath)) {
                if (!normalizePath(path.substring(scriptPath.length+1)).includes("/")) return;
              }
              else if (scriptPath.includes("/node_modules/")) return;
              if (!customFilter(path)) return;

              const code = await fs.promises.readFile(path, "utf8");
              return transform(code, { filename: path, ...config }, false);
          }
        }] } }
      };
    },
    // 构建阶段
    transform(code, filename, transformOptions) {
      if (filename.startsWith(normalizePath(scriptPath))) {
        if (!filename.substring(scriptPath.length+1).includes("/")) return;
      }
      if (!customFilter(filename)) return;

      return transform(code, { filename, ...config, inputSourceMap: transformOptions?.inMap }, true);
    }
  };
};

// AST Traverser

class PluginPass {
  constructor(file) {
    this._map = new Map();
    this.file = file;
    this.opts = file.opts;
    this.filename = file.opts.filename;
  }

  set(key, val) {
    this._map.set(key, val);
  }
  get(key) {
    return this._map.get(key);
  }

  buildCodeFrameError(node, msg, _Error) {
    return this.file.buildCodeFrameError(node, msg, _Error);
  }
}

class File {
  constructor(code, ast, options) {
    this.code = code;
    this.ast = ast;
    this.opts = options;
    this.metadata = {};
    this.path = traverse.NodePath.get({
      parentPath: null,
      parent: ast,
      container: ast,
      key: "program",
      hub: {
        buildError: this.buildCodeFrameError.bind(this),
        file: this
      }
    }).setContext();
    this.scope = this.path.scope;
  }

  buildCodeFrameError(node, msg, _Error = SyntaxError) {
    let loc = node == null ? void 0 : node.loc;
    if (loc) {
      const {
        highlightCode = true
      } = this.opts;
      msg += "\n" + codeFrame.codeFrameColumns(this.code, {
        start: {
          line: loc.start.line,
          column: loc.start.column + 1
        },
        end: loc.end && loc.start.line === loc.end.line ? {
          line: loc.end.line,
          column: loc.end.column + 1
        } : undefined
      }, {
        highlightCode
      });
    }
    return new _Error(msg);
  }
}

/**
 * 转换JavaScript
 */
function transform(code, options, needSourceMap) {
  const mixins = ["jsx", MIXIN_ID];
  if (options.filename.match(/.tsx?$/)) {
    mixins.push("typescript");
  }

  const ast = babelParse(code, {
    sourceFileName: options.filename,
    plugins: mixins,
    inputSourceMap: options.inputSourceMap,
    ...options.parseOptions,
    sourceType: "module",
  });

  const file = new File(code, ast, options);

  /**
   * @type {PluginPass[]}
   */
  const passes = [];
  const visitors = [];

  for (const plugin of options.plugins) {
    if (plugin.developmentOnly && options.envName !== "development") continue;

    const pass = new PluginPass(file, plugin.key);
    passes.push(pass);
    plugin.pre?.call(pass, file);
    visitors.push(plugin.visitor);
  }

  const visitor = traverse.visitors.merge(visitors, passes, options.wrapPluginVisitorMethod);

  traverse.default(file.ast, visitor, file.scope);

  return babelGenerate(file.ast, {
    comments: true,
    compact: 'auto',
    sourceMaps: needSourceMap,
    inputSourceMap: ast.inputSourceMap,

    ...options.generateOptions,

    filename: options.filename,
    sourceFileName: options.filename.substring(options.filename.lastIndexOf('/')+1),
    //sourceRoot: process.cwd(),
  }, code);
}