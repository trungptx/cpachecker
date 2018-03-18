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
package org.sosy_lab.cpachecker.cpa.bdd;

import com.google.common.collect.Iterables;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerState;
import org.sosy_lab.cpachecker.cpa.pointer2.util.ExplicitLocationSet;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.variableclassification.Partition;

public class BDDPointerCompressExpressionVisitor extends BDDCompressExpressionVisitor {

  private final PointerState pointerInfo;

  protected BDDPointerCompressExpressionVisitor(
      PredicateManager pPredMgr,
      VariableTrackingPrecision pPrecision,
      int pSize,
      CFANode pLocation,
      BitvectorManager pBVmgr,
      Partition pPartition,
      PointerState pPointerInfo) {
    super(pPredMgr, pPrecision, pSize, pLocation, pBVmgr, pPartition);
    pointerInfo = pPointerInfo;
  }

  @Override
  public Region[] visit(CPointerExpression e) {
    ExplicitLocationSet explicitSet = null;
    try {
      explicitSet = BDDTransferRelation.getLocationsForLhs(pointerInfo, e);
    } catch (UnrecognizedCCodeException exception) {
      throw new AssertionError(exception);
    }

    if (explicitSet != null && explicitSet.getSize() == 1) {
      MemoryLocation memLoc = Iterables.getOnlyElement(explicitSet);
      return predMgr.createPredicate(
          memLoc.getAsSimpleString(), e.getExpressionType(), location, size, precision);
    }
    return visitDefault(e);
  }
}
