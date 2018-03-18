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
package org.sosy_lab.cpachecker.cpa.assumptions.storage;

import com.google.common.base.Function;
import java.util.List;
import java.util.Optional;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class AssumptionStoragePrecisionAdjustment implements PrecisionAdjustment {

  private final AssumptionStorageTransferRelation transferRelation;

  public AssumptionStoragePrecisionAdjustment(AssumptionStorageTransferRelation pTransferRelation) {
    transferRelation = pTransferRelation;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(AbstractState pState, Precision pPrecision,
      UnmodifiableReachedSet pStates, Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState) throws CPAException, InterruptedException {
    return StaticPrecisionAdjustment.getInstance().prec(pState, pPrecision, pStates, pStateProjection, pFullState);
  }

  @Override
  public Optional<? extends AbstractState> strengthen(AbstractState pState, Precision pPrecision,
      List<AbstractState> pOtherStates) throws CPAException, InterruptedException {
    AssumptionStorageState state = (AssumptionStorageState) pState;
    CFAEdge edge = getEdge(pOtherStates);
    return Optional.of(transferRelation.strengthen(state.reset(), pOtherStates, edge));
  }

  private CFAEdge getEdge(List<AbstractState> pStates) {
    Optional<AbstractState> locationState = pStates.stream().filter(LocationState.class::isInstance).findFirst();
    final CFANode successor;
    if (locationState.isPresent()) {
      LocationState ls = (LocationState) locationState.get();
      successor = ls.getLocationNode();
      if (successor.getNumEnteringEdges() == 1) {
        return successor.getEnteringEdge(0);
      }
    } else {
      successor = new CFANode("__CPAchecker_dummy");
    }
    CFANode predecessor = successor;
    return new CFAEdge() {

      private static final long serialVersionUID = 1L;

      @Override
      public CFANode getSuccessor() {
        return successor;
      }

      @Override
      public String getRawStatement() {
        return "";
      }

      @Override
      public com.google.common.base.Optional<? extends AAstNode> getRawAST() {
        return com.google.common.base.Optional.absent();
      }

      @Override
      public CFANode getPredecessor() {
        return predecessor;
      }

      @Override
      public int getLineNumber() {
        return getFileLocation().getStartingLineNumber();
      }

      @Override
      public FileLocation getFileLocation() {
        // TODO Auto-generated method stub
        return FileLocation.DUMMY;
      }

      @Override
      public CFAEdgeType getEdgeType() {
        return CFAEdgeType.BlankEdge;
      }

      @Override
      public String getDescription() {
        return "";
      }

      @Override
      public String getCode() {
        return "";
      }
    };
  }

}
