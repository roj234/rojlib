"use strict";

import {
  _uc_mixinPluginNames as ucBabelPluginNames,
  _uc_mixinPlugins as ucBabelPlugins,
  _uc_tokenIsKeyword as tokenIsKeyword,
  _uc_tokenLabelName as tokenLabelName,
} from '@babel/parser';

export const MIXIN_ID = "unconsciousMixin";

ucBabelPlugins[MIXIN_ID] = superClass =>
class UnconsciousJSXParserMixin extends superClass {
  jsxParseIdentifier() {
    const node = this.startNode();

    // @
    if(this.match(26)) {
      node._uc_isDecorator = true;
      this.next();
    }

    // onclick.left.passive
    if(this.match(141)){
      node.name = this.state.value;
      node._uc_names = [];

      this.next();
      while (this.match(16)) {
        this.next();

        if (this.match(141)) {
          node._uc_names.push(this.state.value);
          this.next();
        }

        // filter(...)
        if(this.match(10)) {
          this.next();
          node._uc_names.push(this.parseCallExpressionArguments(11));
        }
      }
    }
    else if(tokenIsKeyword(this.state.type)){node.name=tokenLabelName(this.state.type);
    this.next();}
    else {this.unexpected();}

    return this.finishNode(node,"JSXIdentifier");
  }
};
ucBabelPluginNames.push(MIXIN_ID);
