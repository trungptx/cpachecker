/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.isTargetState;

import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm.CPAAlgorithmFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMCache;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;

public class BAMTransferRelation extends AbstractBAMTransferRelation<CPAException> {

  protected final Deque<Triple<AbstractState, Precision, Block>> stack = new ArrayDeque<>();

  private final CPAAlgorithmFactory algorithmFactory;
  protected final BAMPCCManager bamPccManager;

  // Callstack-CPA is used for additional recursion handling
  private final CallstackTransferRelation callstackTransfer;

  //Stats
  int maxRecursiveDepth = 0;

  public BAMTransferRelation(
      Configuration pConfig,
      BAMCPA bamCpa,
      ProofChecker wrappedChecker,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    super(bamCpa, pShutdownNotifier);
    algorithmFactory = new CPAAlgorithmFactory(bamCpa, logger, pConfig, pShutdownNotifier);
    callstackTransfer =
        (CallstackTransferRelation)
            (CPAs.retrieveCPAOrFail(bamCpa, CallstackCPA.class, BAMTransferRelation.class))
                .getTransferRelation();
    bamPccManager = new BAMPCCManager(
        wrappedChecker, pConfig, partitioning, wrappedReducer, bamCpa, data);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      final AbstractState pState, final Precision pPrecision)
      throws CPATransferException, InterruptedException {
    Collection<? extends AbstractState> successors =
        super.getAbstractSuccessors(pState, pPrecision);
    if (bamPccManager.isPCCEnabled()) {
      return bamPccManager.attachAdditionalInfoToCallNodes(successors);
    }
    return successors;
  }

  @Override
  protected Collection<? extends AbstractState> getWrappedTransferSuccessor(
      final ARGState pState, final Precision pPrecision, final CFANode node)
      throws CPATransferException, InterruptedException {
    // The Callstack-CPA is not able to handle a recursion of the form f-g-f,
    // because the operation Reduce splits it into f-g and g-f.
    // Thus we check for recursion here and (if we do not handle recursion here)
    // set a flag for the Callstack-CPA, such that it knows about the recursion.
    final boolean foundRecursion = isRecursiveCall(node);
    if (foundRecursion) {
      callstackTransfer.enableRecursiveContext();
    }
    final Collection<? extends AbstractState> result = wrappedTransfer.getAbstractSuccessors(pState, pPrecision);
    if (foundRecursion) {
      callstackTransfer.disableRecursiveContext();
    }
    return result;
  }

  /**
   * overriding super-method, because it is much faster to access the stack-element than searching
   * for the last entry-state.
   */
  @Override
  protected Block getBlockForState(ARGState state) {
    return stack.isEmpty() ? partitioning.getMainBlock() : stack.peek().getThird();
  }

  /**
   * check if
   * - the current node is before a function-block and
   * - the block was entered before (and thus is part of the stack).
   */
  protected boolean isRecursiveCall(final CFANode node) {
    if (!partitioning.isCallNode(node)) {

      // TODO Why filter for functionCallEdge?
      // If only LoopBlocks are used, we can have recursive Loops, too.

      for (CFAEdge e : CFAUtils.leavingEdges(node).filter(CFunctionCallEdge.class)) {
        for (Block block : Iterables.transform(stack, Triple::getThird)) {
          if (block.getCallNodes().contains(e.getSuccessor())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Enters a new block and performs a new analysis by recursively initiating
   * {@link CPAAlgorithm}, or returns a cached result from {@link BAMCache}.
   *
   * <p>Postcondition: sets the {@code currentBlock} variable to the currently
   * processed block.
   *
   * <p>Postcondition: pushes the current recursive level on the {@code stack}.
   *
   * @param initialState Initial state of the analyzed block.
   * @param pPrecision Initial precision associated with the block start.
   * @param node Node corresponding to the block start.
   * @return Set of states associated with the block exit.
   */
  @Override
  protected Collection<AbstractState> doRecursiveAnalysis(
      final AbstractState initialState, final Precision pPrecision, final CFANode node)
      throws CPAException, InterruptedException {

    //Create ReachSet with node as initial element (+ add corresponding Location+CallStackElement)
    //do an CPA analysis to get the complete reachset
    //if lastElement is error State
    // -> return lastElement and break at precision adjustment
    //else
    // -> compute which states refer to return nodes
    // -> return these states as successor
    // -> cache the result

    final Block outerSubtree = getBlockForState((ARGState)initialState);
    assert outerSubtree == (stack.isEmpty() ? partitioning.getMainBlock() : stack.peek().getThird());
    final Block innerSubtree = partitioning.getBlockForCallNode(node);
    bamPccManager.setCurrentBlock(innerSubtree);
    assert innerSubtree.getCallNodes().contains(node);

    logger.log(Level.FINEST, "Reducing state", initialState);
    final AbstractState reducedInitialState =
        wrappedReducer.getVariableReducedState(initialState, innerSubtree, node);
    final Precision reducedInitialPrecision =
        wrappedReducer.getVariableReducedPrecision(pPrecision, innerSubtree);

    final Triple<AbstractState, Precision, Block> currentLevel =
        Triple.of(reducedInitialState, reducedInitialPrecision, innerSubtree);
    stack.push(currentLevel);
    logger.log(
        Level.FINEST,
        "Starting recursive analysis of depth",
        stack.size(),
        " with current Stack:",
        stack);
    maxRecursiveDepth = Math.max(stack.size(), maxRecursiveDepth);

    final Collection<AbstractState> resultStates =
        analyseBlockAndExpand(
            initialState,
            pPrecision,
            innerSubtree,
            outerSubtree,
            reducedInitialState,
            reducedInitialPrecision);

    logger.log(Level.FINEST, "Finished recursive analysis of depth", stack.size());
    final Triple<AbstractState, Precision, Block> lastLevel = stack.pop();
    assert lastLevel.equals(currentLevel);
    bamPccManager.setCurrentBlock(outerSubtree);

    return resultStates;
  }

  /**
   * Analyse block, return expanded exit-states.
   *
   * <p>Breaks if {@code breakAnalysis} was set by the callees.
   *
   * @param entryState State associated with the block entry.
   * @param precision Precision associated with the block entry.
   * @param innerSubtree Inner block.
   * @param outerSubtree Outer block.
   * @param reducedInitialState {@code entryState} after reduction.
   * @param reducedInitialPrecision {@code precision} after reduction.
   * @return Set of states associated with the block exit.
   */
  protected Collection<AbstractState> analyseBlockAndExpand(
      final AbstractState entryState,
      final Precision precision,
      final Block innerSubtree,
      @Nullable final Block outerSubtree,
      final AbstractState reducedInitialState,
      final Precision reducedInitialPrecision)
      throws CPAException, InterruptedException {

    final Pair<Collection<AbstractState>, ReachedSet> reducedResult =
        getReducedResult(entryState, reducedInitialState, reducedInitialPrecision, innerSubtree);

    if (bamPccManager.isPCCEnabled()) {
      bamPccManager.addBlockAnalysisInfo(reducedInitialState);
    }

    return expandResultStates(
        reducedResult.getFirst(),
        reducedResult.getSecond(),
        innerSubtree,
        outerSubtree,
        entryState,
        precision);
  }

  /**
   * Analyse the block starting at the node with {@code initialState}. If there is a result in the
   * cache ({@code data.bamCache}), it is used, otherwise a recursive {@link CPAAlgorithm} is
   * started.
   *
   * @param initialState State associated with the block entry.
   * @param reducedInitialState Reduced {@code initialState}.
   * @param reducedInitialPrecision Reduced precision associated with the block entry.
   * @return Set of reduced pairs of abstract states associated with the exit of the block and the
   *     reached-set they belong to.
   */
  private Pair<Collection<AbstractState>, ReachedSet> getReducedResult(
      final AbstractState initialState,
      final AbstractState reducedInitialState,
      final Precision reducedInitialPrecision,
      final Block innerSubtree)
      throws InterruptedException, CPAException {

    // statesForFurtherAnalysis is always equal to reducedResult,
    // except for one special case (on revisiting recursion).
    final Collection<AbstractState> statesForFurtherAnalysis;

    // Try to get an element from cache.
    // A previously computed element consists of a reached set associated
    // with the recursive call, and
    final Pair<ReachedSet, Collection<AbstractState>> pair =
        data.getCache().get(reducedInitialState, reducedInitialPrecision, innerSubtree);
    final ReachedSet cachedReached = pair.getFirst();
    final Collection<AbstractState> cachedReturnStates = pair.getSecond();

    assert cachedReturnStates == null || cachedReached != null : "there cannot be "
        + "result-states without reached-states";

    final Collection<AbstractState> reducedResult;
    final ReachedSet reached;
    if (isCacheHit(cachedReached, cachedReturnStates)) {
      // cache hit, return element from cache
      logger.log(
          Level.FINEST,
          "Cache hit with finished reached-set with root",
          cachedReached.getFirstState());
      reducedResult = cachedReturnStates;
      statesForFurtherAnalysis = cachedReturnStates;
      reached = cachedReached;

    } else {
      if (cachedReached == null) {
        // we have not even cached a partly computed reach-set,
        // so we must compute the subgraph specification from scratch
        reached =
            data.createAndRegisterNewReachedSet(
                reducedInitialState, reducedInitialPrecision, innerSubtree);
        logger.log(Level.FINEST, "Cache miss: starting recursive CPAAlgorithm with new initial reached-set.");
      } else {
        reached = cachedReached;
        logger.log(Level.FINEST, "Partial cache hit: starting recursive CPAAlgorithm with partial reached-set with root", reached.getFirstState());
      }

      reducedResult = performCompositeAnalysisWithCPAAlgorithm(reached, innerSubtree);

      assert reducedResult != null;

      statesForFurtherAnalysis = filterResultStatesForFurtherAnalysis(reducedResult, cachedReturnStates);
    }

    assert reached != null;

    registerInitalAndExitStates(initialState, statesForFurtherAnalysis, reached);

    ARGState rootOfBlock = null;
    if (bamPccManager.isPCCEnabled()) {
      if (!(reached.getFirstState() instanceof ARGState)) {
        throw new CPATransferException("Cannot build proof, ARG, for BAM analysis.");
      }
      rootOfBlock = BAMARGUtils.copyARG((ARGState) reached.getFirstState());
    }

    // use 'reducedResult' for cache and 'statesForFurtherAnalysis' as return value,
    // both are always equal, except analysis of recursive procedures (@fixpoint-algorithm)
    data.getCache().put(
        reducedInitialState,
        reached.getPrecision(reached.getFirstState()),
        innerSubtree,
        reducedResult,
        rootOfBlock);

    return Pair.of(statesForFurtherAnalysis, reached);
  }

  /**
   * We try to get a smaller set of states for further analysis.
   *
   * @param reducedResult the result states
   * @param cachedReturnStates the cached return states
   * @throws CPAException may be thrown in subclass
   * @throws InterruptedException may be thrown in subclass
   */
  protected Collection<AbstractState> filterResultStatesForFurtherAnalysis(
      final Collection<AbstractState> reducedResult,
      final Collection<AbstractState> cachedReturnStates)
      throws CPAException, InterruptedException {
    return reducedResult; // dummy implementation, overridden in sub-class
  }

  /**
   * Analyse the block with a recursive call to the {@link CPAAlgorithm} on {@code reached}. May set
   * {@code breakAnalysis} to indicate that the recursively forked analysis is wishing to break.
   *
   * @return return states associated with the analysis.
   *     <p>NB: return states will be either {@link
   *     org.sosy_lab.cpachecker.core.interfaces.Targetable}, or associated with the block end.
   */
  private Collection<AbstractState> performCompositeAnalysisWithCPAAlgorithm(
      final ReachedSet reached, final Block innerSubtree)
      throws InterruptedException, CPAException {

    // CPAAlgorithm is not re-entrant due to statistics
    final CPAAlgorithm algorithm = algorithmFactory.newInstance();
    algorithm.run(reached);

    // if the element is an error element
    final Collection<AbstractState> returnStates;
    final AbstractState lastState = reached.getLastState();
    if (isTargetState(lastState)) {
      //found a target state inside a recursive subgraph call
      //this needs to be propagated to outer subgraph (till main is reached)
      returnStates = Collections.singletonList(lastState);

    } else {
      assert !reached.hasWaitingState();
      // get only those states, that are at block-exit.
      // in case of recursion, the block-exit-nodes might also appear in the middle of the block,
      // but the middle states have children, the exit-states have not.
      returnStates = new ArrayList<>();
      for (AbstractState returnState :
          AbstractStates.filterLocations(reached, innerSubtree.getReturnNodes())) {
        if (((ARGState)returnState).getChildren().isEmpty()) {
          returnStates.add(returnState);
        }
      }
    }

    return returnStates;
  }

  public void cleanCaches() {
    data.clear();
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException,
      InterruptedException {
    Collection<? extends AbstractState> out =
        super.strengthen(pElement, pOtherElements, pCfaEdge, pPrecision);
    if (bamPccManager.isPCCEnabled()) {
      return bamPccManager.attachAdditionalInfoToCallNodes(out);
    } else {
      return out;
    }
  }
}
