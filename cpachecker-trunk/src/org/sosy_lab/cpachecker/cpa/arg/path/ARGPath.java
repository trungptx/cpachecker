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
package org.sosy_lab.cpachecker.cpa.arg.path;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import org.sosy_lab.common.Appenders.AbstractAppender;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPathBuilder.DefaultARGPathBuilder;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPathBuilder.ReverseARGPathBuilder;
import org.sosy_lab.cpachecker.util.Pair;

/**
 * ARGPath contains a non-empty path through the ARG
 * consisting of both a sequence of states
 * and the edges between them.
 * Very often, the first state is the root state of the ARG,
 * and the last state is a target state, though this is not guaranteed.
 *
 * The number of states is always one larger than the number of edges.
 *
 * States on this path cannot be null.
 * Edges can be null,
 * if there is no corresponding CFAEdge between two consecutive abstract states.
 *
 * The recommended way to iterate through an ARGPath if you need both states and edges
 * is to use {@link #pathIterator()}.
 *
 * The usual way to get an ARGPath instance is from methods in {@link ARGUtils}
 * such as {@link ARGUtils#getOnePathTo(ARGState)} and {@link ARGUtils#getRandomPath(ARGState)}.
 */
@Immutable
public class ARGPath extends AbstractAppender {

  private final ImmutableList<ARGState> states;
  private final List<CFAEdge> edges; // immutable, but may contain null

  @SuppressFBWarnings(
      value="JCIP_FIELD_ISNT_FINAL_IN_IMMUTABLE_CLASS",
      justification="This variable is only used for caching the full path for later use"
          + " without having to compute it again.")
  private List<CFAEdge> fullPath = null;

  protected ARGPath(ARGPath pArgPath) {
    states = pArgPath.states;
    edges = pArgPath.edges;
  }

  public ARGPath(List<ARGState> pStates) {
    checkArgument(!pStates.isEmpty(), "ARGPaths may not be empty");
    states = ImmutableList.copyOf(pStates);

    List<CFAEdge> edgesBuilder = new ArrayList<>(states.size()-1);
    for (int i = 0; i < states.size() - 1; i++) {
      ARGState parent = states.get(i);
      ARGState child = states.get(i+1);
      edgesBuilder.add(parent.getEdgeToChild(child)); // may return null
    }

    edges = Collections.unmodifiableList(edgesBuilder);
    assert states.size() - 1 == edges.size();
  }

  public ARGPath(List<ARGState> pStates, List<CFAEdge> pEdges) {
    checkArgument(!pStates.isEmpty(), "ARGPaths may not be empty");
    checkArgument(pStates.size() - 1 == pEdges.size(), "ARGPaths must have one state more than edges");

    states = ImmutableList.copyOf(pStates);
    edges = Collections.unmodifiableList(new ArrayList<>(pEdges));
  }

  public ImmutableList<ARGState> asStatesList() {
    return states;
  }

  /**
   * Return the list of edges between the states.
   * The result of this method is always one element shorter
   * than {@link #asStatesList()}.
   */
  public List<CFAEdge> getInnerEdges() {
    return edges;
  }

  /**
   * Returns the full path contained in this {@link ARGPath}. This means, edges
   * which are null while using getInnerEdges or the pathIterator will be resolved
   * and the complete path from the first {@link ARGState} to the last ARGState
   * is created. This is done by filling up the wholes in the path.
   *
   * If there is no path (null edges can not be filled up, may be happening when
   * using bam) we return an empty list instead.
   */
  public List<CFAEdge> getFullPath() {
    if (fullPath != null) {
      return fullPath;
    }

    List<CFAEdge> newFullPath = new ArrayList<>();
    PathIterator it = pathIterator();

    while (it.hasNext()) {
      ARGState prev = it.getAbstractState();
      CFAEdge curOutgoingEdge = it.getOutgoingEdge();
      it.advance();
      ARGState succ = it.getAbstractState();

      // assert prev.getEdgeToChild(succ) == curOutgoingEdge : "invalid ARGPath";

      // compute path between cur and next node
      if (curOutgoingEdge == null) {
        // we assume a linear chain of edges from 'prev' to 'succ'
        CFANode curNode = extractLocation(prev);
        CFANode nextNode = extractLocation(succ);

        do { // the chain must not be empty
          if (!(curNode.getNumLeavingEdges() == 1 && curNode.getLeavingSummaryEdge() == null)) {
            return Collections.emptyList();
          }

          CFAEdge intermediateEdge = curNode.getLeavingEdge(0);
          newFullPath.add(intermediateEdge);
          curNode = intermediateEdge.getSuccessor();
        } while (curNode != nextNode);

      // we have a normal connection without hole in the edges
      } else {
        newFullPath.add(curOutgoingEdge);
      }
    }

    this.fullPath = newFullPath;
    return newFullPath;
  }

  public ImmutableSet<ARGState> getStateSet() {
    return ImmutableSet.copyOf(states);
  }

  /**
   * Return (predecessor,successor) pairs of ARGStates for every edge in the path.
   */
  public List<Pair<ARGState, ARGState>> getStatePairs() {
    return new AbstractList<Pair<ARGState, ARGState>>() {

      @Override
      public Pair<ARGState, ARGState> get(int pIndex) {
        return Pair.of(states.get(pIndex), states.get(pIndex+1));
      }

      @Override
      public int size() {
        return states.size() - 1;
      }
    };
  }

  /**
   * Create a fresh {@link PathIterator} for this path,
   * with its position at the first state.
   * Note that you cannot call {@link PathIterator#getIncomingEdge()} before calling
   * {@link PathIterator#advance()} at least once.
   */
  public PathIterator pathIterator() {
    return new DefaultPathIterator(this);
  }

  /**
   * Create a fresh {@link PathIterator} for this path,
   * with its position at the last state and iterating backwards.
   * Note that you cannot call {@link PathIterator#getOutgoingEdge()} before calling
   * {@link PathIterator#advance()} at least once.
   */
  public PathIterator reversePathIterator() {
    return new ReversePathIterator(this);
  }

  /**
   * Create a fresh {@link PathIterator} for this path, with its position at the
   * first state. Holes in the path are filled up by inserting more {@link CFAEdge}.
   * Note that you cannot call {@link PathIterator#getIncomingEdge()} before calling
   * {@link PathIterator#advance()} at least once.
   */
  public PathIterator fullPathIterator() {
    return new DefaultFullPathIterator(this);
  }

  /**
   * Create a fresh {@link PathIterator} for this path, with its position at the
   * last state and iterating backwards. Holes in the path are filled up by inserting
   * more {@link CFAEdge}.
   * Note that you cannot call {@link PathIterator#getOutgoingEdge()} before calling
   * {@link PathIterator#advance()} at least once.
   */
  public PathIterator reverseFullPathIterator() {
    return new ReverseFullPathIterator(this);
  }

  /**
   * A forward directed {@link ARGPathBuilder} with no initial states and edges
   * added. (States and edges are always appended to the end of the current path)
   */
  public static ARGPathBuilder builder() {
    return new DefaultARGPathBuilder();
  }

  /**
   * A backward directed {@link ARGPathBuilder} with no initial states and edges
   * added. (States and edges are always appended to the beginning of the current path)
   */
  public static ARGPathBuilder reverseBuilder() {
    return new ReverseARGPathBuilder();
  }

  /**
   * The length of the path, i.e., the number of states
   * (this is different from the number of edges).
   */
  public int size() {
    return states.size();
  }

  public ARGState getFirstState() {
    return states.get(0);
  }

  public ARGState getLastState() {
    return Iterables.getLast(states);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((edges == null) ? 0 : edges.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) { return true; }
    if (!(pOther instanceof ARGPath)) { return false; }

    ARGPath other = (ARGPath) pOther;

    if (edges == null) {
      if (other.edges != null) { return false; }
    } else if (!edges.equals(other.edges)) { return false; }

    // We do not compare the states because they are different from iteration to iteration!

    return true;
  }

  @Override
  public void appendTo(Appendable appendable) throws IOException {
    Joiner.on(System.lineSeparator()).skipNulls().appendTo(appendable, getFullPath());
    appendable.append(System.lineSeparator());
  }
}
