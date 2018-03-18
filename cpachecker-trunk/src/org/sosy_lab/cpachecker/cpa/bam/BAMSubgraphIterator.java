/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.bam;

import static com.google.common.collect.FluentIterable.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;

public class BAMSubgraphIterator {

  private final ARGState targetState;
  private final BAMMultipleCEXSubgraphComputer subgraphComputer;
  private final BAMDataManager data;

  //Internal state of iterator:
  //First state of previously constructed path
  private BackwardARGState firstState;
  //Iterators for branching points
  private Map<BackwardARGState, Iterator<ARGState>> toCallerStatesIterator = new HashMap<>();

  BAMSubgraphIterator(
      ARGState pTargetState, BAMMultipleCEXSubgraphComputer sComputer, BAMDataManager pData) {
    targetState = pTargetState;
    subgraphComputer = sComputer;
    data = pData;
    firstState = null;
  }

  //Actually it is possible to implement an optimization,
  //which allows to search forks not from the first state, but from a some middle state
  private ARGPath computeNextPath(BackwardARGState lastAffectedState, Set<List<Integer>> pRefinedStates) {
    assert lastAffectedState != null;

    ARGState nextParent = null;
    BackwardARGState childOfReducedState = null;
    ARGPath newPath = null;

    List<BackwardARGState> potentialBranchingStates = findBranchingStatesAfter(lastAffectedState);
    if (potentialBranchingStates.isEmpty()) {
      return null;
    }
    Iterator<BackwardARGState> forkIterator = potentialBranchingStates.iterator();

    do {
      //Determine next branching point
      nextParent = null;
      while (nextParent == null && forkIterator.hasNext()) {
        //This is a backward state, which displays the following state after reduced ARG state, which we want to found
        childOfReducedState = forkIterator.next();

        nextParent = findNextExpandedState(childOfReducedState);
      }

      if (nextParent == null) {
        return null;
      }

      //Because of cached paths, we cannot change the part of it
      BackwardARGState rootOfTheClonedPath = cloneTheRestOfPath(childOfReducedState);
      BackwardARGState nextBranchingParentOnPath = new BackwardARGState(nextParent);
      rootOfTheClonedPath.addParent(nextBranchingParentOnPath);
      //Restore the new path from branching point
      newPath = subgraphComputer.restorePathFrom(nextBranchingParentOnPath, pRefinedStates);

    } while (newPath != null);

    return newPath;
  }

  private BackwardARGState cloneTheRestOfPath(BackwardARGState pChildOfForkState) {
    BackwardARGState stateOnOriginPath = pChildOfForkState;
    BackwardARGState stateOnClonedPath = stateOnOriginPath.copy(), tmpStateOnPath;
    BackwardARGState root = stateOnClonedPath;

    while (!stateOnOriginPath.getChildren().isEmpty()) {
      assert stateOnOriginPath.getChildren().size() == 1;
      stateOnOriginPath = getNextStateOnPath(stateOnOriginPath);
      tmpStateOnPath = stateOnOriginPath.copy();
      tmpStateOnPath.addParent(stateOnClonedPath);
      stateOnClonedPath = tmpStateOnPath;
    }
    return root;
  }

  /** Finds the parentState (in ARG), which corresponds to the child (in the path)
   *
   * @param forkChildInPath child, which has more than one parents, which are not yet explored
   * @return found parent state
   */

  private ARGState findNextExpandedState(BackwardARGState forkChildInPath) {


    Iterator<ARGState> iterator;
    //It is important to put a backward state in map, because we can find the same real state during exploration
    //but for it a new backward state will be created
    if (toCallerStatesIterator.containsKey(forkChildInPath)) {
      //Means we have already handled this state, just get the next one
      iterator = toCallerStatesIterator.get(forkChildInPath);
    } else {
      ARGState forkChildInARG = forkChildInPath.getARGState();
      assert forkChildInARG.getParents().size() == 1;
      ARGState reducedStateInARG = forkChildInARG.getParents().iterator().next();

      iterator =
          from(data.getNonReducedInitialStates(reducedStateInARG))
              .skip(1) // skip already traversed parent of forkState
              .transform(s -> (ARGState) s)
              .iterator();

      //We get this fork the second time (the first one was from path computer)
      //Found the caller, we have explored the first time
      toCallerStatesIterator.put(forkChildInPath, iterator);
    }

    if (iterator.hasNext()) {
      return iterator.next();
    } else {
      //We do not find next branching parent for the given state
      return null;
    }
  }

  /**
   * Due to special structure of ARGPath,
   * the real fork state (reduced entry) is not included into it.
   * We need to get it.
   *
   * @param parent a state after that we need to found a fork
   * @return a state of the nearest fork
   */
  private List<BackwardARGState> findBranchingStatesAfter(BackwardARGState parent) {

    List<BackwardARGState> potentialForkStates = new ArrayList<>();
    Map<ARGState, BackwardARGState> mapToPath = new HashMap<>();
    BackwardARGState currentStateOnPath = parent;

    while (currentStateOnPath.getChildren().size() > 0) {

      assert currentStateOnPath.getChildren().size() == 1;
      currentStateOnPath = getNextStateOnPath(currentStateOnPath);
      ARGState currentStateInARG = currentStateOnPath.getARGState();

      //No matter which parent to take - interesting one is single anyway
      ARGState parentInARG = currentStateInARG.getParents().iterator().next();

      // Check if it is an exit state, we are waiting
      // Recursion is not supported here!

      if (data.getNonReducedInitialStates(parentInARG).size() > 1) {
        assert parentInARG.getParents().size() == 0;

        //Now we should check, that there is no corresponding exit state in the path
        //only in this case this is a real fork

        //This is expanded state on the path at function call
        ARGState expandedEntryState = getPreviousStateOnPath(currentStateOnPath).getARGState();

        //Save child and if we meet it, we remove the parent as not a fork
        mapToPath.put(expandedEntryState, currentStateOnPath);
        potentialForkStates.add(currentStateOnPath);
      }

      //parentInARG may be an expanded exit state, which is not stored in the path
      //check it using parent of parent
      from(parentInARG.getParents())
        .filter(mapToPath::containsKey)
        .transform(mapToPath::get)
        .forEach(potentialForkStates::remove);
    }

    return potentialForkStates;
  }

  public ARGPath nextPath(Set<List<Integer>> pRefinedStatesIds) {
    ARGPath path;
    if (firstState == null) {
      //The first time, we have no path to iterate
      path = subgraphComputer.restorePathFrom(new BackwardARGState(targetState), pRefinedStatesIds);
    } else {
      path = computeNextPath(firstState, pRefinedStatesIds);
    }
    if (path != null) {
      //currentPath may become null if it goes through repeated (refined) states
      firstState = (BackwardARGState) path.getFirstState();
    }

    return path;
  }

  /* Functions only to simplify the understanding:
   */

  private BackwardARGState getNextStateOnPath(BackwardARGState state) {
    return (BackwardARGState) state.getChildren().iterator().next();
  }

  private BackwardARGState getPreviousStateOnPath(BackwardARGState state) {
    return (BackwardARGState) state.getParents().iterator().next();
  }
}
