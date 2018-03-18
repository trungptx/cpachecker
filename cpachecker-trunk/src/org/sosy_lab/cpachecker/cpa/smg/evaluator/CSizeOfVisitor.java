/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.evaluator;

import java.util.List;
import java.util.Optional;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel.BaseSizeofVisitor;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressAndState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGAddress;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGExplicitValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class CSizeOfVisitor extends BaseSizeofVisitor {
  private final CFAEdge edge;
  private final SMGState state;
  private final Optional<CExpression> expression;
  protected final SMGExpressionEvaluator eval;

  public CSizeOfVisitor(SMGExpressionEvaluator pSmgExpressionEvaluator, CFAEdge pEdge,
      SMGState pState, Optional<CExpression> pExpression) {
    super(pSmgExpressionEvaluator.machineModel);
    edge = pEdge;
    state = pState;
    expression = pExpression;
    eval = pSmgExpressionEvaluator;
  }

  @Override
  public Integer visit(CArrayType pArrayType) throws IllegalArgumentException {

    CExpression arrayLength = pArrayType.getLength();

    int sizeOfType = pArrayType.getType().accept(this);

    /* If the array type has a constant size, we can simply
     * get the length of the array, but if the size
     * of the array type is variable, we have to try and calculate
     * the current size.
     */
    int length;

    if(arrayLength == null) {
      // treat size of unknown array length type as ptr
      return super.visit(pArrayType);
    } else if (arrayLength instanceof CIntegerLiteralExpression) {
      length = ((CIntegerLiteralExpression) arrayLength).getValue().intValue();
    } else if (edge instanceof CDeclarationEdge) {

      /* If we currently declare the array of this type,
       * we simply need to calculate the current length of the array
       * from the given expression in the type.
       */
      SMGExplicitValue lengthAsExplicitValue;

      try {
        lengthAsExplicitValue = eval.evaluateExplicitValueV2(state, edge, arrayLength);
      } catch (CPATransferException e) {
        throw new IllegalArgumentException(
            "Exception when calculating array length of " + pArrayType.toASTString("") + ".", e);
      }

      if (lengthAsExplicitValue.isUnknown()) {
        length = handleUnkownArrayLengthValue(pArrayType);
      } else {
        length = lengthAsExplicitValue.getAsInt();
      }

    } else {

      /*
       * If we are not at the declaration of the variable array type, we try to get the
       * smg object that represents the array, and calculate the current array size that way.
       */

      if (expression.filter(CLeftHandSide.class::isInstance).isPresent()) {

        LValueAssignmentVisitor visitor = eval.getLValueAssignmentVisitor(edge, state);

        List<SMGAddressAndState> addressOfFieldAndState;
        try {
          addressOfFieldAndState = expression.get().accept(visitor);
        } catch (CPATransferException e) {
          return handleUnkownArrayLengthValue(pArrayType);
        }

        assert addressOfFieldAndState.size() > 0;

        SMGAddress addressOfField = addressOfFieldAndState.get(0).getObject();

        if (addressOfField.isUnknown()) {
          return handleUnkownArrayLengthValue(pArrayType);
        }

        SMGObject arrayObject = addressOfField.getObject();
        int offset = addressOfField.getOffset().getAsInt();
        return arrayObject.getSize() - offset;
      } else {
        throw new IllegalArgumentException(
            "Unable to calculate the size of the array type " + pArrayType.toASTString("") + ".");
      }
    }

    return length * sizeOfType;
  }

  protected int handleUnkownArrayLengthValue(CArrayType pArrayType) {
    throw new IllegalArgumentException(
        "Can't calculate array length of type " + pArrayType.toASTString("") + ".");
  }
}