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
package org.sosy_lab.cpachecker.core.algorithm.bmc;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;

public class FrameSet implements AutoCloseable {

  private final Solver solver;

  private final Set<ProverOptions> proverOptions;

  private final List<Set<CandidateInvariant>> frames = new ArrayList<>();

  private final List<ProverEnvironmentWithFallback> frameProvers = new ArrayList<>();

  private final Set<Integer> emptyFrames = new HashSet<>(2);

  private final Map<CandidateInvariant, Integer> rootCandidateInvariantFrontierIndices =
      new HashMap<>();

  public FrameSet(Solver pSolver, Set<ProverOptions> pProverOptions) {
    solver = pSolver;
    proverOptions =
        pProverOptions.isEmpty() ? Collections.emptySet() : Sets.immutableEnumSet(pProverOptions);
    newFrame();
  }

  private void newFrame() {
    frames.add(new LinkedHashSet<>());

    @SuppressWarnings("resource")
    ProverEnvironmentWithFallback prover =
        new ProverEnvironmentWithFallback(
            solver, proverOptions.stream().toArray(ProverOptions[]::new));
    frameProvers.add(prover);
  }

  @Override
  public void close() {
    for (ProverEnvironmentWithFallback prover : frameProvers) {
      prover.close();
    }
  }

  @Override
  public String toString() {
    return IntStream.rangeClosed(0, getFrontierIndex())
        .mapToObj(
            i -> {
              Iterable<? extends Object> result = frames.get(i);
              if (i == 0) {
                result = Iterables.concat(Collections.singleton("I"), result);
              }
              return result;
            })
        .map(Object::toString)
        .collect(Collectors.joining(", "));
  }

  public int getFrontierIndex() {
    return frames.size() - 1;
  }

  public Set<CandidateInvariant> getFrameClauses(int pFrameIndex) {
    return Collections.unmodifiableSet(frames.get(pFrameIndex));
  }

  public Iterable<CandidateInvariant> getPushableFrameClauses(int pFrameIndex) {
    return Iterables.filter(
        getFrameClauses(pFrameIndex), c -> !rootCandidateInvariantFrontierIndices.containsKey(c));
  }

  public ProverEnvironmentWithFallback getFrameProver(int pFrameIndex) {
    return frameProvers.get(pFrameIndex);
  }

  public Set<CandidateInvariant> getInvariants(int pFrameIndex) {
    if (pFrameIndex < 0) {
      throw new IndexOutOfBoundsException("Illegal frame index: " + pFrameIndex);
    }
    if (pFrameIndex > getFrontierIndex()) {
      return Collections.emptySet();
    }
    return IntStream.rangeClosed(pFrameIndex, getFrontierIndex())
        .mapToObj(frames::get)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  public void addFrameClause(int pFrameIndex, CandidateInvariant pClause) {
    if (pFrameIndex > getFrontierIndex()) {
      throw new IllegalArgumentException("To push the frontier, use pushFrontier");
    }
    Set<CandidateInvariant> frame = frames.get(pFrameIndex);
    boolean added = false;
    for (CandidateInvariant clauseComponent :
        CandidateInvariantCombination.getConjunctiveParts(pClause)) {
      if (!rootCandidateInvariantFrontierIndices.containsKey(clauseComponent)
          && frame.add(clauseComponent)) {
        added = true;
      }
    }
    if (added) {
      emptyFrames.remove(pFrameIndex);
    }
  }

  public void pushFrameClause(int pFrameIndex, CandidateInvariant pClause) {
    Set<CandidateInvariant> oldFrame = frames.get(pFrameIndex);
    if (!oldFrame.remove(pClause)) {
      throw new IllegalArgumentException(pClause + " not found in frame " + pFrameIndex);
    }
    for (CandidateInvariant clauseComponent :
        CandidateInvariantCombination.getConjunctiveParts(pClause)) {
      oldFrame.remove(clauseComponent);
    }
    if (oldFrame.isEmpty()) {
      emptyFrames.add(pFrameIndex);
    }
    addFrameClause(pFrameIndex + 1, pClause);
    frames.get(pFrameIndex + 1).add(pClause);
  }

  public int getFrontierIndex(CandidateInvariant pRootInvariant) {
    Integer index = rootCandidateInvariantFrontierIndices.get(pRootInvariant);
    if (index == null) {
      throw new IllegalArgumentException("Unknown root invariant: " + pRootInvariant);
    }
    return index;
  }

  public void pushFrontier(int pFrontierIndex, CandidateInvariant pRootInvariant) {
    Integer previousIndex = rootCandidateInvariantFrontierIndices.get(pRootInvariant);
    if (previousIndex == null && pFrontierIndex != 1) {
      throw new IllegalArgumentException(
          "Root invariants must initially be pushed to frame one instead of frame "
              + pFrontierIndex);
    } else if (previousIndex != null && previousIndex < pFrontierIndex - 1) {
      throw new IllegalArgumentException(
          "Incorrect new frontier index "
              + pFrontierIndex
              + ": The frontier for the root invariant "
              + pRootInvariant
              + " is currently at "
              + previousIndex);
    } else if (previousIndex != null && previousIndex >= pFrontierIndex) {
      return;
    }
    assert previousIndex == null || previousIndex == pFrontierIndex - 1;
    if (previousIndex != null) {
      Set<CandidateInvariant> oldFrame = frames.get(previousIndex);
      boolean removed = oldFrame.remove(pRootInvariant);
      assert removed;
      if (oldFrame.isEmpty()) {
        emptyFrames.add(previousIndex);
      }
    }
    if (pFrontierIndex > getFrontierIndex()) {
      assert getFrontierIndex() + 1 == pFrontierIndex;
      newFrame();
    }
    assert getFrontierIndex() >= pFrontierIndex;
    frames.get(pFrontierIndex).add(pRootInvariant);
    rootCandidateInvariantFrontierIndices.put(pRootInvariant, pFrontierIndex);
  }

  public boolean isConfirmed(CandidateInvariant pRootInvariant) {
    int index = getFrontierIndex(pRootInvariant);
    return IntStream.range(1, index).filter(emptyFrames::contains).findAny().isPresent();
  }
}
