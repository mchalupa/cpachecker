/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.livevar;

import com.google.common.base.Equivalence.Wrapper;

import org.sosy_lab.cpachecker.cfa.ast.AArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.ABinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.ACastExpression;
import org.sosy_lab.cpachecker.cfa.ast.ACharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.AFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.AUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JArrayCreationExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JArrayInitializer;
import org.sosy_lab.cpachecker.cfa.ast.java.JArrayLengthExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBooleanLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JEnumConstantExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JNullLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JRunTimeTypeEqualsType;
import org.sosy_lab.cpachecker.cfa.ast.java.JThisExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JVariableRunTimeType;
import org.sosy_lab.cpachecker.util.LiveVariables;

import java.util.Map;
import java.util.BitSet;

/**
 * This visitor collects all ASimpleDeclarations from a given expression. This
 * is independent of the programming language of the evaluated expression.
 */
class DeclarationCollectingVisitor extends AExpressionVisitor<BitSet, RuntimeException> {

  private final int allVarsSize;
  private final Map<Wrapper<? extends ASimpleDeclaration>, Integer> listPos;

  public DeclarationCollectingVisitor(
      Map<Wrapper<? extends ASimpleDeclaration>, Integer> pListPos) {
    allVarsSize = pListPos.size();
    listPos = pListPos;
  }

  @Override
  public BitSet visit(CTypeIdExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(CImaginaryLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(CFieldReference exp) {
    return exp.getFieldOwner().accept(this);
  }

  @Override
  public BitSet visit(CPointerExpression exp) {
    return exp.getOperand().accept(this);
  }

  @Override
  public BitSet visit(CComplexCastExpression exp) {
    return exp.getOperand().accept(this);
  }

  @Override
  public BitSet visit(CAddressOfLabelExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(JBooleanLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(JArrayCreationExpression exp) {
    if (exp.getInitializer() != null) {
      return exp.getInitializer().accept(this);
    } else {
      return new BitSet(allVarsSize);
    }
  }

  @Override
  public BitSet visit(JArrayInitializer exp) {
    BitSet out = new BitSet(allVarsSize);
    for (JExpression innerExp : exp.getInitializerExpressions()) {
      // Do not remove explicit type inference, otherwise build fails with IntelliJ
      out.or(innerExp.accept(this));
    }
    return out;
  }

  @Override
  public BitSet visit(JArrayLengthExpression exp) {
    return exp.getQualifier().accept(this);
  }

  @Override
  public BitSet visit(JVariableRunTimeType exp) {
    return exp.getReferencedVariable().accept(this);
  }

  @Override
  public BitSet visit(JRunTimeTypeEqualsType exp) {
    return exp.getRunTimeTypeExpression().accept(this);
  }

  @Override
  public BitSet visit(JNullLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(JEnumConstantExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(JThisExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(AArraySubscriptExpression exp) {
    BitSet out = accept0(exp.getArrayExpression());
    out.or(accept0(exp.getSubscriptExpression()));
    return out;
  }

  @Override
  public BitSet visit(AIdExpression exp) {
    BitSet out = new BitSet(allVarsSize);
    int pos = listPos.get(
        LiveVariables.LIVE_DECL_EQUIVALENCE.wrap(exp.getDeclaration()));
    out.set(pos);
    return out;
  }

  @Override
  public BitSet visit(ABinaryExpression exp) {
    BitSet out = accept0(exp.getOperand1());
    out.or(accept0(exp.getOperand2()));
    return out;
  }

  @Override
  public BitSet visit(ACastExpression exp) {
    return accept0(exp.getOperand());
  }

  @Override
  public BitSet visit(ACharLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(AFloatLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(AIntegerLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(AStringLiteralExpression exp) {
    return new BitSet(allVarsSize);
  }

  @Override
  public BitSet visit(AUnaryExpression exp) {
    return accept0(exp.getOperand());
  }

  private BitSet accept0 (AExpression exp) {
    return exp.accept_(this);
  }
}
