/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.flowdep;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState.DefinitionPoint;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState.ProgramDefinitionPoint;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependencegraph.UsedIdsCollector;
import org.sosy_lab.cpachecker.util.expressions.IdExpressionCollector;
import org.sosy_lab.cpachecker.util.reachingdef.ReachingDefUtils;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Transfer relation of {@link FlowDependenceCPA}.
 */
class FlowDependenceTransferRelation
    extends SingleEdgeTransferRelation {

  private final TransferRelation delegate;
  private final IdExpressionCollector idCollector;

  private final LogManagerWithoutDuplicates logger;

  FlowDependenceTransferRelation(final TransferRelation pDelegate, final LogManager pLogger) {
    delegate = pDelegate;
    idCollector = new IdExpressionCollector();

    logger = new LogManagerWithoutDuplicates(pLogger);
  }

  private Map<MemoryLocation, Collection<ProgramDefinitionPoint>> normalizeReachingDefinitions(
      ReachingDefState pState) {

    Map<MemoryLocation, Collection<ProgramDefinitionPoint>> normalized = new HashMap<>();

    normalized.putAll(normalize(pState.getLocalReachingDefinitions()));
    normalized.putAll(normalize(pState.getGlobalReachingDefinitions()));
    return normalized;
  }

  private Map<MemoryLocation, Collection<ProgramDefinitionPoint>> normalize(
      Map<MemoryLocation, Set<DefinitionPoint>> pDefs) {

    Map<MemoryLocation, Collection<ProgramDefinitionPoint>> normalized = new HashMap<>();
    for (Map.Entry<MemoryLocation, Set<DefinitionPoint>> e : pDefs.entrySet()) {
      MemoryLocation varName = e.getKey();
      Set<DefinitionPoint> points = e.getValue();

      Collection<ProgramDefinitionPoint> defPoints =
          points
              .stream()
              .filter(x -> x instanceof ProgramDefinitionPoint)
              .map(p -> (ProgramDefinitionPoint) p)
              .collect(Collectors.toList());

      normalized.put(varName, defPoints);
    }
    return normalized;
  }

  /**
   * Returns a new FlowDependenceState for the declaration represented by the given {@link
   * CVariableDeclaration} object. Since the wrapped {@link
   * org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefCPA ReachingDefCPA} tracks new definitions of
   * variables, we only have to consider the use of variables in the initializer that may exist.
   */
  private FlowDependenceState handleDeclarationEdge(
      CDeclarationEdge pCfaEdge,
      CVariableDeclaration pDecl,
      FlowDependenceState pNextFlowState,
      ReachingDefState pReachDefState,
      PointerState pPointerState)
      throws CPATransferException {

    CInitializer maybeInitializer = pDecl.getInitializer();

    if (maybeInitializer != null && maybeInitializer instanceof CInitializerExpression) {
      // If the declaration contains an initializer, create the corresponding flow dependences
      // for its variable uses
      CExpression initializerExp = ((CInitializerExpression) maybeInitializer).getExpression();
      MemoryLocation def = MemoryLocation.valueOf(pDecl.getQualifiedName());
      return handleOperation(
          pCfaEdge,
          Optional.of(def),
          getUsedVars(initializerExp, pPointerState),
          pNextFlowState,
          pReachDefState);

    } else {
      // If the declaration contains no initializer, there are no variable uses and ergo
      // no new flow dependences.
      return pNextFlowState;
    }
  }

  /**
   * Adds the flow dependences based on the given {@link CAstNode} and the {@link ReachingDefState}
   * to the given {@link FlowDependenceState}.
   *
   * <p>If no reaching definition exists for a program variable used in the expression, a flow
   * dependence to the declaration of the variable is added.
   */
  private FlowDependenceState handleOperation(
      CFAEdge pCfaEdge,
      Optional<MemoryLocation> pDef,
      Set<MemoryLocation> pUses,
      FlowDependenceState pNextState,
      ReachingDefState pReachDefState) {

    Map<MemoryLocation, Collection<ProgramDefinitionPoint>> defs =
        normalizeReachingDefinitions(pReachDefState);

    Multimap<MemoryLocation, ProgramDefinitionPoint> dependences = HashMultimap.create();
    for (MemoryLocation memLoc : pUses) {
      Collection<ProgramDefinitionPoint> definitionPoints = defs.get(memLoc);
      if (definitionPoints != null && !definitionPoints.isEmpty()) {
        dependences.putAll(memLoc, definitionPoints);
      } else {
        logger.log(Level.WARNING, "No definition point for use ", memLoc, " at ", pCfaEdge);
      }
    }
    if (!dependences.isEmpty()) {
      pNextState.addDependence(pCfaEdge, pDef, dependences);
    }

    return pNextState;
  }

  private Set<MemoryLocation> getUsedVars(CAstNode pExpression, PointerState pPointerState)
      throws CPATransferException {
    UsesCollector usesCollector = new UsesCollector(pPointerState, logger);
    return pExpression.accept(usesCollector);
  }

  private FlowDependenceState handleReturnStatementEdge(
      CReturnStatementEdge pCfaEdge,
      FlowDependenceState pNextState,
      ReachingDefState pReachDefState,
      PointerState pPointerState)
      throws CPATransferException {
    com.google.common.base.Optional<CAssignment> asAssignment = pCfaEdge.asAssignment();

    if (asAssignment.isPresent()) {
      CAssignment returnAssignment = asAssignment.get();
      CRightHandSide rhs = returnAssignment.getRightHandSide();
      Set<MemoryLocation> defs = getDef(returnAssignment.getLeftHandSide(), pPointerState);

      FlowDependenceState nextState = pNextState;
      for (MemoryLocation d : defs) {
        nextState =
            handleOperation(
                pCfaEdge,
                Optional.of(d),
                getUsedVars(rhs, pPointerState),
                nextState,
                pReachDefState);
      }
      return nextState;

    } else {
      return pNextState;
    }
  }

  private Set<MemoryLocation> getDef(CLeftHandSide pLeftHandSide, PointerState pPointerState)
      throws CPATransferException {
    Set<MemoryLocation> decls;
    if (pLeftHandSide instanceof CPointerExpression) {
      return ReachingDefUtils.possiblePointees(pLeftHandSide, pPointerState);

    } else if (pLeftHandSide instanceof CArraySubscriptExpression) {
      decls = ((CArraySubscriptExpression) pLeftHandSide).getArrayExpression().accept(idCollector);
    } else {
      decls = pLeftHandSide.accept(idCollector);
    }
    return decls;
  }

  protected FlowDependenceState handleAssumption(
      CAssumeEdge cfaEdge,
      CExpression expression,
      FlowDependenceState pNextState,
      ReachingDefState pReachDefState,
      PointerState pPointerState)
      throws CPATransferException {
    return handleOperation(
        cfaEdge,
        Optional.empty(),
        getUsedVars(expression, pPointerState),
        pNextState,
        pReachDefState);
  }

  protected FlowDependenceState handleFunctionCallEdge(
      CFunctionCallEdge pFunctionCallEdge,
      List<CExpression> pArguments,
      FlowDependenceState pNextState,
      ReachingDefState pReachDefState,
      PointerState pPointerState)
      throws CPATransferException {

    FlowDependenceState nextState = pNextState;
    List<CParameterDeclaration> params = pFunctionCallEdge.getSuccessor().getFunctionParameters();
    for (int i = 0; i < pArguments.size(); i++) {
      MemoryLocation def;
      if (i < params.size()) {
        def = MemoryLocation.valueOf(params.get(i).getQualifiedName());
      } else {
        assert pFunctionCallEdge.getSuccessor().getFunctionDefinition().getType().takesVarArgs();
        // TODO support var args
        break;
      }
      CExpression argument = pArguments.get(i);
      nextState =
          handleOperation(
              pFunctionCallEdge,
              Optional.of(def),
              getUsedVars(argument, pPointerState),
              nextState,
              pReachDefState);
    }
    return nextState;
  }

  protected FlowDependenceState handleStatementEdge(
      CStatementEdge pCfaEdge,
      CStatement pStatement,
      FlowDependenceState pNextState,
      ReachingDefState pReachDefState,
      PointerState pPointerState)
      throws CPATransferException {

    FlowDependenceState nextState = pNextState;
    Set<MemoryLocation> possibleDefs;
    if (pStatement instanceof CAssignment) {
      possibleDefs = getDef(((CAssignment) pStatement).getLeftHandSide(), pPointerState);

      if (possibleDefs != null) {
        for (MemoryLocation def : possibleDefs) {
          nextState =
              handleOperation(
                  pCfaEdge,
                  Optional.ofNullable(def),
                  getUsedVars(pStatement, pPointerState),
                  nextState,
                  pReachDefState);
        }
      } else {
        nextState =
            handleOperation(
                pCfaEdge,
                Optional.empty(),
                getUsedVars(pStatement, pPointerState),
                nextState,
                pReachDefState);
      }
    }

    return nextState;
  }

  @Override
  public Collection<FlowDependenceState> getAbstractSuccessorsForEdge(
      final AbstractState pState, final Precision pPrecision, final CFAEdge pCfaEdge)
      throws CPATransferException {

    assert pState instanceof FlowDependenceState
        : "Expected state of type " + FlowDependenceState.class.getSimpleName();

    FlowDependenceState oldState = (FlowDependenceState) pState;
    CompositeState oldComposite = oldState.getReachDefState();
    Optional<CompositeState> nextComposite =
        computeReachDefState(oldComposite, pPrecision, pCfaEdge);

    if (nextComposite.isPresent()) {
      CompositeState newReachDefState = nextComposite.get();
      Pair<ReachingDefState, PointerState> oldReachDefAndPointerState = oldState.unwrap();
      ReachingDefState oldReachDefState = oldReachDefAndPointerState.getFirst();
      PointerState oldPointerState = oldReachDefAndPointerState.getSecond();

      FlowDependenceState nextState = new FlowDependenceState(newReachDefState);
      switch (pCfaEdge.getEdgeType()) {
        case DeclarationEdge:
          CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
          if (declEdge.getDeclaration() instanceof CVariableDeclaration) {
            CVariableDeclaration declaration = (CVariableDeclaration) declEdge.getDeclaration();
            nextState =
                handleDeclarationEdge(
                    declEdge, declaration, nextState, oldReachDefState, oldPointerState);
          } // else {
            // Function declarations don't introduce any flow dependencies
            // }
          break;

        case StatementEdge:
          CStatementEdge stmtEdge = (CStatementEdge) pCfaEdge;
          nextState =
              handleStatementEdge(
                  stmtEdge, stmtEdge.getStatement(), nextState, oldReachDefState, oldPointerState);
          break;

        case AssumeEdge:
          CAssumeEdge assumeEdge = (CAssumeEdge) pCfaEdge;
          nextState =
              handleAssumption(
                  assumeEdge,
                  assumeEdge.getExpression(),
                  nextState,
                  oldReachDefState,
                  oldPointerState);
          break;

        case ReturnStatementEdge:
          CReturnStatementEdge returnStatementEdge = (CReturnStatementEdge) pCfaEdge;
          nextState =
              handleReturnStatementEdge(
                  returnStatementEdge, nextState, oldReachDefState, oldPointerState);
          break;

        case FunctionCallEdge:
          CFunctionCallEdge callEdge = (CFunctionCallEdge) pCfaEdge;
          nextState =
              handleFunctionCallEdge(
                  callEdge, callEdge.getArguments(), nextState, oldReachDefState, oldPointerState);
          break;

        case FunctionReturnEdge:
          CFunctionReturnEdge returnEdge = (CFunctionReturnEdge) pCfaEdge;
          nextState =
              handleFunctionReturnEdge(returnEdge, nextState, oldReachDefState, oldPointerState);
          break;

        default:
          break;
      }

      assert nextState != null;
      return ImmutableSet.of(nextState);

    } else {
      return Collections.emptySet();
    }
  }

  private FlowDependenceState handleFunctionReturnEdge(
      final CFunctionReturnEdge pReturnEdge,
      final FlowDependenceState pNewState,
      final ReachingDefState pReachDefState,
      final PointerState pPointerState)
      throws CPATransferException {

    FlowDependenceState nextState = pNewState;
    CFunctionSummaryEdge summaryEdge = pReturnEdge.getSummaryEdge();
    CFunctionCallExpression functionCall = summaryEdge.getExpression().getFunctionCallExpression();

    List<CExpression> outFunctionParams = functionCall.getParameterExpressions();
    List<CParameterDeclaration> inFunctionParams = functionCall.getDeclaration().getParameters();

    // TODO support varargs
    for (int i = 0; i < inFunctionParams.size(); i++) {
      CParameterDeclaration inParam = inFunctionParams.get(i);
      CType parameterType = inParam.getType();

      if (parameterType instanceof CArrayType) {
        CExpression outParam = outFunctionParams.get(i);
        Set<MemoryLocation> possibleDefs;
        if (outParam instanceof CLeftHandSide) {
          possibleDefs = getDef((CLeftHandSide) outParam, pPointerState);
        } else {
          throw new AssertionError("Unhandled: " + outParam);
        }

        if (possibleDefs != null) {
          for (MemoryLocation def : possibleDefs) {
            nextState =
                handleOperation(
                    pReturnEdge,
                    Optional.ofNullable(def),
                    ImmutableSet.of(MemoryLocation.valueOf(inParam.getQualifiedName())),
                    nextState,
                    pReachDefState);
          }
        } else {
          nextState =
              handleOperation(
                  pReturnEdge,
                  Optional.empty(),
                  ImmutableSet.of(MemoryLocation.valueOf(inParam.getQualifiedName())),
                  nextState,
                  pReachDefState);
        }
      }
    }

    com.google.common.base.Optional<CVariableDeclaration> maybeReturnVar =
        summaryEdge.getFunctionEntry().getReturnVariable();
    if (maybeReturnVar.isPresent()) {
      Set<MemoryLocation> possibleDefs = null;
      CFunctionCall call = summaryEdge.getExpression();
      if (call instanceof CFunctionCallAssignmentStatement) {
        possibleDefs =
            getDef(((CFunctionCallAssignmentStatement) call).getLeftHandSide(), pPointerState);
      }
      if (possibleDefs != null) {
        for (MemoryLocation def : possibleDefs) {
          nextState =
              handleOperation(
                  pReturnEdge,
                  Optional.ofNullable(def),
                  ImmutableSet.of(MemoryLocation.valueOf(maybeReturnVar.get().getQualifiedName())),
                  nextState,
                  pReachDefState);
        }
      } else {
        nextState =
            handleOperation(
                pReturnEdge,
                Optional.empty(),
                ImmutableSet.of(MemoryLocation.valueOf(maybeReturnVar.get().getQualifiedName())),
                nextState,
                pReachDefState);
      }
    }
    return nextState;
  }

  private Optional<CompositeState> computeReachDefState(
      CompositeState pOldState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException {

    Collection<? extends AbstractState> computedReachDefStates;
    try {
      computedReachDefStates =
          delegate.getAbstractSuccessorsForEdge(pOldState, pPrecision, pCfaEdge);

    } catch (InterruptedException pE) {
      throw new CPATransferException("Exception in reaching definitions transfer", pE);
    }

    if (computedReachDefStates.isEmpty()) {
      return Optional.empty();
    } else {
      CompositeState composite = (CompositeState) Iterables.getOnlyElement(computedReachDefStates);
      return Optional.of(composite);
    }
  }

  /**
   * Visitor that collects the variables used in a {@link CAstNode}. Variables are represented by
   * their declaration.
   */
  private static class UsesCollector
      implements CAstNodeVisitor<Set<MemoryLocation>, CPATransferException> {

    private final UsedIdsCollector idCollector = new UsedIdsCollector();

    private final PointerState pointerState;
    private final LogManagerWithoutDuplicates logger;

    public UsesCollector(
        final PointerState pPointerState, final LogManagerWithoutDuplicates pLogger) {
      pointerState = pPointerState;
      logger = pLogger;
    }

    @Override
    public Set<MemoryLocation> visit(CExpressionStatement pStmt) throws CPATransferException {
      return pStmt.getExpression().accept(new UsedIdsCollector());
    }

    @Override
    public Set<MemoryLocation> visit(CExpressionAssignmentStatement pStmt)
        throws CPATransferException {
      Set<MemoryLocation> used = new HashSet<>();
      used.addAll(pStmt.getRightHandSide().accept(this));
      used.addAll(handleLeftHandSide(pStmt.getLeftHandSide()));

      return used;
    }

    @Override
    public Set<MemoryLocation> visit(CFunctionCallAssignmentStatement pStmt)
        throws CPATransferException {
      Set<MemoryLocation> used = new HashSet<>();
      used.addAll(pStmt.getRightHandSide().accept(this));
      used.addAll(handleLeftHandSide(pStmt.getLeftHandSide()));

      return used;
    }

    private Set<MemoryLocation> handleLeftHandSide(final CLeftHandSide pLhs)
        throws CPATransferException {
      if (pLhs instanceof CPointerExpression) {
        return ((CPointerExpression) pLhs).getOperand().accept(this);
      } else if (pLhs instanceof CArraySubscriptExpression) {
        return ((CArraySubscriptExpression) pLhs).getSubscriptExpression().accept(this);
      } else {
        return Collections.emptySet();
      }
    }

    @Override
    public Set<MemoryLocation> visit(CFunctionCallStatement pStmt) throws CPATransferException {
      Set<MemoryLocation> paramDecls = new HashSet<>();
     for (CExpression p : pStmt.getFunctionCallExpression().getParameterExpressions()) {
       paramDecls.addAll(p.accept(this));
     }
     return paramDecls;
    }

    @Override
    public Set<MemoryLocation> visit(CArrayDesignator pArrayDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CArrayRangeDesignator pArrayRangeDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CFieldDesignator pFieldDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CArraySubscriptExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CFieldReference pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CIdExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CPointerExpression pExp) throws CPATransferException {
      Set<MemoryLocation> uses = new HashSet<>(pExp.accept(idCollector));
      Set<MemoryLocation> pointees = ReachingDefUtils.possiblePointees(pExp, pointerState);
      if (pointees == null) {
        logger.logOnce(Level.WARNING, "Unhandled pointer dereference. Analysis may be unsound.");
      } else {
        uses.addAll(pointees);
      }
      return uses;
    }

    @Override
    public Set<MemoryLocation> visit(CComplexCastExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CInitializerExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CInitializerList pInitializerList)
        throws CPATransferException {
      Set<MemoryLocation> uses = new HashSet<>();
      for (CInitializer i : pInitializerList.getInitializers()) {
        uses.addAll(i.accept(this));
      }
      return uses;
    }

    @Override
    public Set<MemoryLocation> visit(CDesignatedInitializer pExp) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CFunctionCallExpression pExp) throws CPATransferException {
      Set<MemoryLocation> useds = new HashSet<>();
      for (CExpression p : pExp.getParameterExpressions()) {
        useds.addAll(p.accept(idCollector));
      }
      return useds;
    }

    @Override
    public Set<MemoryLocation> visit(CBinaryExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CCastExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CCharLiteralExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CFloatLiteralExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CIntegerLiteralExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CStringLiteralExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CTypeIdExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CUnaryExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CImaginaryLiteralExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CAddressOfLabelExpression pExp) throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<MemoryLocation> visit(CFunctionDeclaration pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CComplexTypeDeclaration pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CTypeDefDeclaration pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CVariableDeclaration pDecl) throws CPATransferException {
      CInitializer init = pDecl.getInitializer();
      if (init != null) {
        return init.accept(this);
      } else {
        return Collections.emptySet();
      }
    }

    @Override
    public Set<MemoryLocation> visit(CParameterDeclaration pDecl) throws CPATransferException {
      return pDecl.asVariableDeclaration().accept(this);
    }

    @Override
    public Set<MemoryLocation> visit(CEnumerator pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<MemoryLocation> visit(CReturnStatement pNode) throws CPATransferException {
      com.google.common.base.Optional<CExpression> ret = pNode.getReturnValue();

      if (ret.isPresent()) {
        return ret.get().accept(idCollector);
      } else {
        return Collections.emptySet();
      }
    }
  }
}
