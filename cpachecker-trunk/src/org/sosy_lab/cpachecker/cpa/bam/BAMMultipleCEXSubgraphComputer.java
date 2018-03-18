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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;

/**
 * The subgraph computer is used to restore paths not to target states, but to any other.
 * The difficulty is to determine the outer block.
 *
 * One more feature of the computer is skipping such paths, which contains special (repeated) states.
 * The feature is extremely important for refinement optimization: we do not refine and even not compute the similar paths
 */

public class BAMMultipleCEXSubgraphComputer extends BAMSubgraphComputer{

  private Set<ArrayDeque<Integer>> remainingStates = new HashSet<>();
  private final Function<ARGState, Integer> getStateId;

  BAMMultipleCEXSubgraphComputer(BAMCPA bamCPA, @Nonnull Function<ARGState, Integer> idExtractor) {
    super(bamCPA);
    getStateId = idExtractor;
  }


  private ARGState findPath(BackwardARGState newTreeTarget, Set<List<Integer>> pProcessedStates) throws InterruptedException, MissingBlockException {

    Map<ARGState, BackwardARGState> elementsMap = new HashMap<>();
    final NavigableSet<ARGState> openElements = new TreeSet<>(); // for sorted IDs in ARGstates
    ARGState root = null;
    boolean inCallstackFunction = false;

    //Deep clone to be patient about modification
    remainingStates.clear();
    for (List<Integer> newList : pProcessedStates) {
      remainingStates.add(new ArrayDeque<>(newList));
    }

    ARGState target = newTreeTarget.getARGState();
    elementsMap.put(target, newTreeTarget);
    ARGState currentState = target;

    openElements.addAll(target.getParents());
    while (!openElements.isEmpty()) {
      currentState = openElements.pollLast();
      BackwardARGState newCurrentElement = new BackwardARGState(currentState);
      elementsMap.put(currentState, newCurrentElement);

      final Set<BackwardARGState> childrenInSubgraph = new TreeSet<>();
      for (final ARGState child : currentState.getChildren()) {
        // if a child is not in the subgraph, it does not lead to the target, so ignore it.
        // Because of the ordering, all important children should be finished already.
        if (elementsMap.containsKey(child)) {
          childrenInSubgraph.add(elementsMap.get(child));
        }
      }

      if (childrenInSubgraph.isEmpty()) {
        continue;
      }

      inCallstackFunction = false;
      if (currentState.getParents().isEmpty()) {
        // Find correct expanded state
        Collection<AbstractState> expandedStates = data.getNonReducedInitialStates(currentState);

        if (expandedStates.isEmpty()) {
          // children are a normal successors -> create an connection from parent to children
          for (final BackwardARGState newChild : childrenInSubgraph) {
            newChild.addParent(newCurrentElement);
            if (checkRepeatitionOfState(newChild)) {
              return DUMMY_STATE_FOR_REPEATED_STATE;
            }
          }

          //The first state
          root = newCurrentElement;
          break;
        }

        //Try to find path.
        //Exchange the reduced state by the expanded one
        currentState = (ARGState) expandedStates.iterator().next();
        newCurrentElement = new BackwardARGState(currentState);
        elementsMap.put(currentState, newCurrentElement);
        inCallstackFunction = true;
      }

      // add parent for further processing
      openElements.addAll(currentState.getParents());

      if (data.hasInitialState(currentState) && !inCallstackFunction) {

        // If child-state is an expanded state, the child is at the exit-location of a block.
        // In this case, we enter the block (backwards).
        // We must use a cached reachedSet to process further, because the block has its own reachedSet.
        // The returned 'innerTreeRoot' is the rootNode of the subtree, created from the cached reachedSet.
        // The current subtree (successors of child) is appended beyond the innerTree, to get a complete subgraph.
        computeCounterexampleSubgraphForBlock(newCurrentElement, childrenInSubgraph);
        assert childrenInSubgraph.size() == 1;
        BackwardARGState tmpState = childrenInSubgraph.iterator().next();
        //Check repetition of constructed states
        while (tmpState != newCurrentElement) {
          if (checkRepeatitionOfState(tmpState.getARGState())) {
            return DUMMY_STATE_FOR_REPEATED_STATE;
          }
          Collection<ARGState> parents = tmpState.getParents();
          assert parents.size() == 1;
          tmpState = (BackwardARGState) parents.iterator().next();
        }

      } else {
        // children are normal successors -> create an connection from parent to children
        for (final BackwardARGState newChild : childrenInSubgraph) {
          newChild.addParent(newCurrentElement);
          if (checkRepeatitionOfState(newChild)) {
            return DUMMY_STATE_FOR_REPEATED_STATE;
          }
        }

        if (currentState.getParents().isEmpty()) {
          //The first state
          root = newCurrentElement;
          break;
        }
      }
      if (currentState.isDestroyed()) {
        return null;
      }
    }
    assert root != null;
    return root;
  }

  private boolean checkRepeatitionOfState(ARGState currentElement) {
    if (currentElement != null && getStateId != null) {
      Integer currentId = getStateId.apply(currentElement);
      for (ArrayDeque<Integer> rest : remainingStates) {
        if (rest.getLast().equals(currentId)) {
          rest.removeLast();
          if (rest.isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  ARGPath restorePathFrom(BackwardARGState pLastElement, Set<List<Integer>> pRefinedStates) {
    //Note pLastElement may not be the last indeed
    //The path may be recomputed from the middle

    assert (pLastElement != null && !pLastElement.isDestroyed());

    try {
      ARGState rootOfSubgraph = findPath(pLastElement, pRefinedStates);
      assert (rootOfSubgraph != null);
      if (rootOfSubgraph == BAMMultipleCEXSubgraphComputer.DUMMY_STATE_FOR_REPEATED_STATE) {
        return null;
      }
      ARGPath result = ARGUtils.getRandomPath(rootOfSubgraph);
      if (result != null && checkThePathHasRepeatedStates(result, pRefinedStates)) {
        return null;
      }
      return result;
    } catch (MissingBlockException e) {
      return null;
    } catch (InterruptedException e) {
      return null;
    }
  }

  public ARGPath computePath(ARGState pLastElement) {
    return restorePathFrom(new BackwardARGState(pLastElement), Collections.emptySet());
  }

  private boolean checkThePathHasRepeatedStates(ARGPath path, Set<List<Integer>> pRefinedStates) {
    List<Integer> ids =
        from(path.asStatesList())
        .transform(getStateId)
        .toList();

    return from(pRefinedStates)
        .anyMatch(ids::containsAll);
  }

  /** This states is used for UsageStatisticsRefinement:
   *  If after some refinement iterations the path goes through already processed states,
   *  this marked state is returned.
   */
  public final static BackwardARGState DUMMY_STATE_FOR_REPEATED_STATE = new BackwardARGState(new ARGState(null, null));
  /**
   * This is a ARGState, that counts backwards, used to build the Pseudo-ARG for CEX-retrieval.
   * As the Pseudo-ARG is build backwards starting at its end-state, we count the ID backwards.
   */

  public BAMSubgraphIterator iterator(ARGState target) {
    return new BAMSubgraphIterator(target, this, data);
  }
}
