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
package org.sosy_lab.cpachecker.cpa.bam;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGToDotWriter;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.util.Pair;

@Options(prefix = "cpa.bam")
class BAMReachedSetExporter implements Statistics {

  @Option(secure = true, description = "export blocked ARG as .dot file")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path argFile = Paths.get("BlockedARG.dot");

  @Option(
    secure = true,
    description = "export single blocked ARG as .dot files, should contain '%d'"
  )
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate indexedArgFile = PathTemplate.ofFormatString("ARGs/ARG_%d.dot");

  @Option(secure = true, description = "export used parts of blocked ARG as .dot file")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path simplifiedArgFile = Paths.get("BlockedARGSimplified.dot");

  private final Predicate<Pair<ARGState, ARGState>> highlightSummaryEdge =
      input -> input.getFirst().getEdgeToChild(input.getSecond()) instanceof FunctionSummaryEdge;

  private final LogManager logger;
  private final AbstractBAMCPA bamcpa;

  BAMReachedSetExporter(Configuration pConfig, LogManager pLogger, AbstractBAMCPA pCpa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    bamcpa = pCpa;
  }

  @Override
  public @Nullable String getName() {
    return null; // not needed
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    exportAllReachedSets(argFile, indexedArgFile, pReached);
    exportUsedReachedSets(simplifiedArgFile, pReached);
  }

  private void exportAllReachedSets(
      final Path superArgFile,
      final PathTemplate indexedFile,
      final UnmodifiableReachedSet mainReachedSet) {

    if (superArgFile != null) {

      final Set<UnmodifiableReachedSet> allReachedSets = new HashSet<>();
      allReachedSets.addAll(bamcpa.getData().getCache().getAllCachedReachedStates());
      allReachedSets.add(mainReachedSet);

      final Set<ARGState> rootStates = new HashSet<>();
      final Multimap<ARGState, ARGState> connections = HashMultimap.create();

      for (final UnmodifiableReachedSet reachedSet : allReachedSets) {
        ARGState rootState = (ARGState) reachedSet.getFirstState();
        rootStates.add(rootState);
        Multimap<ARGState, ARGState> localConnections = HashMultimap.create();
        getConnections(rootState, localConnections);
        connections.putAll(localConnections);

        // dump small graph
        writeArg(
            indexedFile.getPath(((ARGState) reachedSet.getFirstState()).getStateId()),
            localConnections,
            Collections.singleton((ARGState) reachedSet.getFirstState()));
      }

      // dump super-graph
      writeArg(superArgFile, connections, rootStates);
    }
  }

  /** dump only those ReachedSets, that are reachable from mainReachedSet. */
  private void exportUsedReachedSets(
      final Path superArgFile, final UnmodifiableReachedSet mainReachedSet) {

    if (superArgFile != null) {

      final Multimap<ARGState, ARGState> connections = HashMultimap.create();
      final Set<ARGState> rootStates = getUsedRootStates(mainReachedSet, connections);
      writeArg(superArgFile, connections, rootStates);
    }
  }

  private void writeArg(
      final Path file,
      final Multimap<ARGState, ARGState> connections,
      final Set<ARGState> rootStates) {
    try (Writer w = IO.openOutputFile(file, Charset.defaultCharset())) {
      ARGToDotWriter.write(
          w,
          rootStates,
          connections,
          ARGState::getChildren,
          Predicates.alwaysTrue(),
          highlightSummaryEdge);
    } catch (IOException e) {
      logger.logUserException(
          Level.WARNING, e, String.format("Could not write ARG to file: %s", file));
    }
  }

  private Set<ARGState> getUsedRootStates(
      final UnmodifiableReachedSet mainReachedSet, final Multimap<ARGState, ARGState> connections) {
    final Set<UnmodifiableReachedSet> finished = new HashSet<>();
    final Deque<UnmodifiableReachedSet> waitlist = new ArrayDeque<>();
    waitlist.add(mainReachedSet);
    while (!waitlist.isEmpty()) {
      final UnmodifiableReachedSet reachedSet = waitlist.pop();
      if (!finished.add(reachedSet)) {
        continue;
      }
      final ARGState rootState = (ARGState) reachedSet.getFirstState();
      final Set<ReachedSet> referencedReachedSets = getConnections(rootState, connections);
      waitlist.addAll(referencedReachedSets);
    }

    final Set<ARGState> rootStates = new HashSet<>();
    for (UnmodifiableReachedSet reachedSet : finished) {
      rootStates.add((ARGState) reachedSet.getFirstState());
    }
    return rootStates;
  }

  /**
   * This method iterates over all reachable states from rootState and searches for connections to
   * other reachedSets (a set of all those other reachedSets is returned). As side-effect we collect
   * a Multimap of all connections: - from a state (in current reachedSet) to its reduced state (in
   * other rechedSet) and - from a foreign state (in other reachedSet) to its expanded state(s) (in
   * current reachedSet).
   */
  private Set<ReachedSet> getConnections(
      final ARGState rootState, final Multimap<ARGState, ARGState> connections) {
    final BAMDataManager data = bamcpa.getData();
    final Set<ReachedSet> referencedReachedSets = new HashSet<>();
    final Set<ARGState> finished = new HashSet<>();
    final Deque<ARGState> waitlist = new ArrayDeque<>();
    waitlist.add(rootState);
    while (!waitlist.isEmpty()) {
      ARGState state = waitlist.pop();
      if (!finished.add(state)) {
        continue;
      }
      if (data.hasInitialState(state)) {
        for (ARGState child : state.getChildren()) {
          assert data.hasExpandedState(child);
          ARGState reducedExitState = (ARGState) data.getReducedStateForExpandedState(child);
          if (reducedExitState.isDestroyed()) {
            continue; // skip deleted reached-set, TODO why is reached-set deleted?
          }
          ReachedSet target = data.getReachedSetForInitialState(state, reducedExitState);

          referencedReachedSets.add(target);
          ARGState targetState = (ARGState) target.getFirstState();
          connections.put(state, targetState);
        }
      }
      if (data.hasExpandedState(state)) {
        AbstractState sourceState = data.getReducedStateForExpandedState(state);
        connections.put((ARGState) sourceState, state);
      }
      waitlist.addAll(state.getChildren());
    }
    return referencedReachedSets;
  }
}
