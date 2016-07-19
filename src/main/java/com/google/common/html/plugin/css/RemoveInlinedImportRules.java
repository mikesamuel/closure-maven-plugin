package com.google.common.html.plugin.css;

import org.apache.maven.plugin.logging.Log;

import com.google.common.css.CustomPass;
import com.google.common.css.compiler.ast.CssImportRuleNode;
import com.google.common.css.compiler.ast.DefaultTreeVisitor;
import com.google.common.css.compiler.ast.ErrorManager;
import com.google.common.css.compiler.ast.MutatingVisitController;

final class RemoveInlinedImportRules implements CustomPass {
  final Log log;

  RemoveInlinedImportRules(Log log) {
    this.log = log;
  }

  public void run(
      final MutatingVisitController visitController,
      ErrorManager errorManager) {
    visitController.startVisit(new DefaultTreeVisitor() {
      @Override
      public void leaveImportRule(CssImportRuleNode node) {
        if (CssImportGraph.importForNode(log, node).isPresent()) {
          visitController.removeCurrentNode();
        }
      }
    });
  }

  public Time when() {
    return Time.SIMPLIFIED_AST;
  }
}
