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
package org.sosy_lab.cpachecker.util.dependencegraph;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ForwardingTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.dominator.DominatorState;
import org.sosy_lab.cpachecker.cpa.flowdep.FlowDependenceState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState.ProgramDefinitionPoint;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.EdgeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.dependencegraph.DependenceGraph.DependenceType;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatistics;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;

/** Factory for creating a {@link DependenceGraph} from a {@link CFA}. */
@Options(prefix = "dependenceGraph")
public class DGBuilder {

  private final CFA cfa;
  private final Configuration config;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private Table<CFAEdge, Optional<MemoryLocation>, DGNode> nodes;
  private Table<DGNode, DGNode, DependenceType> adjacencyMatrix;

  private StatInt flowDependenceNumber = new StatInt(StatKind.SUM, "Number of flow dependences");
  private StatInt controlDependenceNumber =
      new StatInt(StatKind.SUM, "Number of control dependences");
  private StatCounter isolatedNodes = new StatCounter("Number of isolated nodes");

  @Option(
    secure = true,
    description =
        "File to export dependence graph to. If `null`, dependence"
            + " graph will not be exported as dot."
  )
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportDot = Paths.get("DependenceGraph.dot");

  public DGBuilder(
      final CFA pCfa,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    config = pConfig;
    cfa = pCfa;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  public DependenceGraph build()
      throws InvalidConfigurationException, InterruptedException, CPAException {
    nodes = HashBasedTable.create();
    adjacencyMatrix = HashBasedTable.create();
    addFlowDependences();
    addControlDependences();
    addMissingNodes();

    DependenceGraph dg = new DependenceGraph(nodes, adjacencyMatrix, shutdownNotifier);
    export(dg);
    logger.log(
        Level.FINE,
        "Create dependence graph with ",
        nodes.size(),
        " nodes and ",
        adjacencyMatrix.size(),
        " edges.");
    return dg;
  }

  private void addMissingNodes() {
    EdgeCollectingCFAVisitor edgeCollector = new EdgeCollectingCFAVisitor();
    CFATraversal.dfs().traverse(cfa.getMainFunction(), edgeCollector);
    List<CFAEdge> allEdges = edgeCollector.getVisitedEdges();

    for (CFAEdge e : allEdges) {
      if (!nodes.containsRow(e)) {
        nodes.put(e, Optional.empty(), new DGNode(e));
        isolatedNodes.inc();
      }
    }
  }

  /**
   * Adds control dependencies to dependence graph.
   */
  private void addControlDependences()
      throws InterruptedException, InvalidConfigurationException, CPAException {
    PostDominators postDoms = PostDominators.create(cfa, logger, shutdownNotifier);
    Set<CFANode> reachableNodes = postDoms.getNodes();
    List<CFANode> branchingNodes =
        reachableNodes
            .stream()
            .filter(n -> n.getNumLeavingEdges() > 1)
            .filter(n -> n.getLeavingEdge(0) instanceof CAssumeEdge)
            .collect(Collectors.toList());

    for (CFANode branch : branchingNodes) {
      Set<CFANode> postDominatorsOfBranchingNode = postDoms.getPostDominators(branch);
      FluentIterable<CFAEdge> assumeEdges = CFAUtils.leavingEdges(branch);
      assert assumeEdges.size() == 2;
      for (CFAEdge g : assumeEdges) {
        DGNode nodeDependentOn = getDGNode(g, Optional.empty());
        int controlDepCount = 0;
        assert getDGNodes(g).size() == 1
            : "Only using one DG node, but multiple would exist: " + nodeDependentOn;
        List<CFANode> nodesOnPath = new ArrayList<>();
        Queue<CFAEdge> waitlist = new ArrayDeque<>(8);
        Set<CFAEdge> reached = new HashSet<>();
        waitlist.offer(g);
        while (!waitlist.isEmpty()) {
          CFAEdge current = waitlist.poll();
          CFANode succ = current.getSuccessor();
          if (!reachableNodes.contains(succ)) {
            continue;
          }
          if (!reached.contains(current)) {
            reached.add(current);
            CFANode precessorNode = current.getPredecessor();
            if (precessorNode.equals(branch)) {
              CFAUtils.leavingEdges(succ).forEach(waitlist::offer);

            } else
            // branch node is not post-dominated by current node (condition 2 of control dependence)
            if (!postDominatorsOfBranchingNode.contains(precessorNode)) {
              // all nodes on path from branch to current are post-dominated by current
              // (condition 1 of control dependence)
              if (isPostDomOfAll(precessorNode, nodesOnPath, postDoms)) {
                Collection<DGNode> nodesDepending = getDGNodes(current);
                for (DGNode nodeDepending : nodesDepending) {
                  addDependence(nodeDependentOn, nodeDepending, DependenceType.CONTROL);
                  controlDepCount++;
                }
                nodesOnPath.add(precessorNode);
              }
              CFAUtils.leavingEdges(current.getSuccessor()).forEach(waitlist::offer);
            }
          }
        }
        controlDependenceNumber.setNextValue(controlDepCount);
      }
    }

    Collection<FunctionEntryNode> functionEntries = cfa.getAllFunctionHeads();
    CFATraversal traversalInsideFunction = CFATraversal.dfs().ignoreFunctionCalls();
    for (FunctionEntryNode fctEntry : functionEntries) {
      Collection<DGNode> functionCalls =
          CFAUtils.enteringEdges(fctEntry).transform(x -> getDGNode(x, Optional.empty())).toList();
      assert CFAUtils.enteringEdges(fctEntry).allMatch(x -> x instanceof CFunctionCallEdge);
      int depCount = 0;
      Set<CFANode> functionNodes = traversalInsideFunction.collectNodesReachableFrom(fctEntry);
      for (CFANode n : functionNodes) {
        for (CFAEdge e : CFAUtils.leavingEdges(n)) {
          Collection<DGNode> candidates = getDGNodes(e);
          for (DGNode dgN : candidates) {
            if (adjacencyMatrix.column(dgN).values().contains(DependenceType.CONTROL)) {
              for (DGNode nodeDependentOn : functionCalls) {
                addDependence(nodeDependentOn, dgN, DependenceType.CONTROL);
                depCount++;
              }
            }
          }
        }
      }
      controlDependenceNumber.setNextValue(depCount);
    }
  }

  private boolean isPostDomOfAll(
      final CFANode pNode,
      final Collection<CFANode> pNodeSet,
      final PostDominators pPostDominators) {

    for (CFANode n : pNodeSet) {
      if (!pPostDominators.getPostDominators(n).contains(pNode)) {
        return false;
      }
    }
    return true;
  }

  private void addFlowDependences()
      throws InvalidConfigurationException, InterruptedException, CPAException {
    FlowDependences flowDependences = FlowDependences.create(cfa, config, logger, shutdownNotifier);

    for (Cell<CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>> c :
        flowDependences.cellSet()) {

      CFAEdge edgeDepending = checkNotNull(c.getRowKey());
      Optional<MemoryLocation> defOfEdge = checkNotNull(c.getColumnKey());
      Multimap<MemoryLocation, CFAEdge> uses = checkNotNull(c.getValue());
      DGNode nodeDepending;
      if (defOfEdge.isPresent()) {
        nodeDepending = getDGNode(edgeDepending, defOfEdge);
      } else {
        nodeDepending = getDGNode(edgeDepending, Optional.empty());
      }
      int flowDepCount = 0;
      for (Entry<MemoryLocation, CFAEdge> useAndDef : uses.entries()) {
        MemoryLocation use = checkNotNull(useAndDef.getKey());
        DGNode dependency = getDGNode(useAndDef.getValue(), Optional.of(use));
        addDependence(dependency, nodeDepending, DependenceType.FLOW);
        flowDepCount++;
      }
      flowDependenceNumber.setNextValue(flowDepCount);
    }
  }

  /**
   * Returns the {@link DGNode} corresponding to the given {@link CFAEdge}. If a node for this edge
   * already exists, the existing node is returned. Otherwise, a new node is created.
   *
   * <p>Always use this method and never use {@link #createNode(CFAEdge, Optional)}!
   */
  private DGNode getDGNode(final CFAEdge pCfaEdge, final Optional<MemoryLocation> pCause) {
    if (!nodes.contains(pCfaEdge, pCause)) {
      nodes.put(pCfaEdge, pCause, createNode(pCfaEdge, pCause));
    }
    return nodes.get(pCfaEdge, pCause);
  }

  private Collection<DGNode> getDGNodes(final CFAEdge pCfaEdge) {
    if (!nodes.containsRow(pCfaEdge)) {
      nodes.put(pCfaEdge, Optional.empty(), createNode(pCfaEdge, Optional.empty()));
    }
    return nodes.row(pCfaEdge).values();
  }

  /**
   * Adds the given dependence edge to the set of dependence edges and tells the nodes of the edge
   * about the new edge.
   */
  private void addDependence(DGNode pDependentOn, DGNode pDepending, DependenceType pType) {
    adjacencyMatrix.put(pDependentOn, pDepending, pType);
  }

  /**
   * Creates a new node. Never call this method directly, but use {@link #getDGNode(CFAEdge,
   * Optional)} to retrieve nodes for {@link CFAEdge CFAEdges}!
   */
  private DGNode createNode(final CFAEdge pCfaEdge, final Optional<MemoryLocation> pCause) {
    if (pCause.isPresent()) {
      return new DGNode(pCfaEdge, pCause.get());
    } else {
      return new DGNode(pCfaEdge);
    }
  }

  private void export(DependenceGraph pDg) {
    if (exportDot != null) {
      try (Writer w = IO.openOutputFile(exportDot, Charset.defaultCharset())) {
        DGExporter.generateDOT(w, pDg);
      } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Could not write dependence graph to dot file");
        // continue with analysis
      }
    }
  }

  public Statistics getStatistics() {
    return new AbstractStatistics() {

      @Override
      public void printStatistics(
          final PrintStream pOut, final Result pResult, final UnmodifiableReachedSet pReached) {
        StatInt nodeNumber = new StatInt(StatKind.SUM, "Number of DG nodes");
        nodeNumber.setNextValue(nodes.size());
        put(pOut, 4, nodeNumber);
        put(pOut, 4, flowDependenceNumber);
        put(pOut, 4, controlDependenceNumber);
        put(pOut, 4, isolatedNodes);
      }

      @Override
      public String getName() {
        return ""; // empty name for nice output under CFACreator statistics
      }
    };
  }

  /**
   * Flow dependences of nodes in a {@link CFA}.
   *
   * <p>A node <code>I</code> is flow dependent on a node <code>J</code> if <code>J</code>
   * represents a variable assignment and the assignment is part of node <code>I</code>'s use-def
   * relation.
   */
  private static class FlowDependences
      extends ForwardingTable<
          CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>> {

    @Options(prefix = "dependencegraph.flowdep")
    private static class FlowDependenceConfig {
      @Option(secure = true, description = "Run flow dependence analysis with constant propagation")
      boolean constantPropagation = false;

      FlowDependenceConfig(final Configuration pConfig) throws InvalidConfigurationException {
        pConfig.inject(this);
      }
    }

    // CFAEdge + defined memory location -> Edge defining the uses
    private Table<CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>> dependences;

    private FlowDependences(
        final Table<CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>>
            pDependences) {
      dependences = pDependences;
    }

    @Override
    protected Table<CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>>
        delegate() {
      return dependences;
    }

    public static FlowDependences create(
        final CFA pCfa,
        final Configuration pConfig,
        final LogManager pLogger,
        final ShutdownNotifier pShutdownNotifier)
        throws InvalidConfigurationException, CPAException, InterruptedException {
      FlowDependenceConfig options = new FlowDependenceConfig(pConfig);
      String configFile;
      if (options.constantPropagation) {
        configFile = "flowDependences-constantProp.properties";
      } else {
        configFile = "flowDependences.properties";
      }

      Configuration config =
          Configuration.builder().loadFromResource(FlowDependences.class, configFile).build();
      ReachedSetFactory reachedFactory = new ReachedSetFactory(config, pLogger);
      ConfigurableProgramAnalysis cpa =
          new CPABuilder(config, pLogger, pShutdownNotifier, reachedFactory)
              .buildCPAs(pCfa, Specification.alwaysSatisfied(), new AggregatedReachedSets());
      Algorithm algorithm = CPAAlgorithm.create(cpa, pLogger, config, pShutdownNotifier);
      ReachedSet reached = reachedFactory.create();

      AbstractState initialState =
          cpa.getInitialState(pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
      Precision initialPrecision =
          cpa.getInitialPrecision(
              pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
      reached.add(initialState, initialPrecision);

      // populate reached set
      algorithm.run(reached);
      assert !reached.hasWaitingState()
          : "CPA algorithm finished, but waitlist not empty: " + reached.getWaitlist();

      Table<CFAEdge, Optional<MemoryLocation>, Multimap<MemoryLocation, CFAEdge>> dependencyMap =
          HashBasedTable.create();
      for (AbstractState s : reached) {
        assert s instanceof ARGState;
        ARGState wrappingState = (ARGState) s;
        FlowDependenceState flowDepState = getState(wrappingState, FlowDependenceState.class);

        for (CFAEdge g : flowDepState.getDependees()) {
          Set<Optional<MemoryLocation>> defs = flowDepState.getDefinitions(g);
          for (Optional<MemoryLocation> d : defs) {
            Multimap<MemoryLocation, CFAEdge> memLocUsedAndDefiner =
                getDependences(flowDepState, g, d);
            if (dependencyMap.contains(g, d)) {
              dependencyMap.get(g, d).putAll(memLocUsedAndDefiner);
            } else {
              dependencyMap.put(g, d, memLocUsedAndDefiner);
            }
          }
        }
      }

      return new FlowDependences(dependencyMap);
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractState> T getState(
        final ARGState pState, final Class<T> stateClass) {
      T s = AbstractStates.extractStateByType(pState, stateClass);
      assert s != null
          : "No "
              + ReachingDefState.class.getCanonicalName()
              + " found in composite state: "
              + pState.toString();

      return s;
    }

    private static Multimap<MemoryLocation, CFAEdge> getDependences(
        final FlowDependenceState pFlowDepState,
        final CFAEdge pEdge,
        final Optional<MemoryLocation> pDef) {
      Multimap<MemoryLocation, CFAEdge> dependencies = HashMultimap.create();

      Multimap<MemoryLocation, ProgramDefinitionPoint> dependentDefs =
          pFlowDepState.getDependentDefs(pEdge, pDef);

      for (Entry<MemoryLocation, ProgramDefinitionPoint> e : dependentDefs.entries()) {
        ProgramDefinitionPoint defPoint = e.getValue();
        CFANode start = defPoint.getDefinitionEntryLocation();
        CFANode stop = defPoint.getDefinitionExitLocation();

        boolean added = false;
        for (CFAEdge g : CFAUtils.leavingEdges(start)) {
          if (g.getSuccessor().equals(stop)) {
            dependencies.put(e.getKey(), g);
            added = true;
          }
        }
        assert added : "No edge added for nodes " + start + " to " + stop;
      }

      return dependencies;
    }
  }

  /**
   * Map of {@link CFANode CFANodes} to their post-dominators.
   *
   * <p>Node <code>I</code> is post-dominated by node <code>J</code> if every path from <code>I
   * </code> to the program exit goes through <code>J</code>.
   */
  private static class PostDominators {

    private Map<CFANode, Set<CFANode>> postDominatorMap;

    private PostDominators(final Map<CFANode, Set<CFANode>> pPostDominatorMap) {
      postDominatorMap = pPostDominatorMap;
    }

    /**
     * Get all post-dominators of the given node.
     *
     * <p>Node <code>I</code> is post-dominated by node <code>J</code> if every path from <code>I
     * </code> to the program exit goes through <code>J</code>.
     *
     * <p>That means that every program path from the given node to the program exit has to go
     * through each node that is in the returned collection
     */
    private Set<CFANode> getPostDominators(final CFANode pNode) {
      checkState(
          postDominatorMap.containsKey(pNode), "Node " + pNode + " not in post-dominator map");
      return postDominatorMap.get(pNode);
    }

    public static PostDominators create(
        final CFA pCfa, final LogManager pLogger, final ShutdownNotifier pShutdownNotifier)
        throws InvalidConfigurationException, CPAException, InterruptedException {
      String configFile = "postDominators.properties";

      Configuration config =
          Configuration.builder().loadFromResource(PostDominators.class, configFile).build();
      ReachedSetFactory reachedFactory = new ReachedSetFactory(config, pLogger);
      ConfigurableProgramAnalysis cpa =
          new CPABuilder(config, pLogger, pShutdownNotifier, reachedFactory)
              .buildCPAs(pCfa, Specification.alwaysSatisfied(), new AggregatedReachedSets());
      Algorithm algorithm = CPAAlgorithm.create(cpa, pLogger, config, pShutdownNotifier);
      ReachedSet reached = reachedFactory.create();

      FunctionEntryNode mainFunction = pCfa.getMainFunction();
      Collection<CFANode> startNodes =
          CFAUtils.getProgramSinks(pCfa, pCfa.getLoopStructure().get(), mainFunction);

      for (CFANode n : startNodes) {
        StateSpacePartition partition = StateSpacePartition.getDefaultPartition();
        AbstractState initialState = cpa.getInitialState(n, partition);
        Precision initialPrecision = cpa.getInitialPrecision(n, partition);
        reached.add(initialState, initialPrecision);
      }

      // populate reached set
      algorithm.run(reached);
      assert !reached.hasWaitingState()
          : "CPA algorithm finished, but waitlist not empty: " + reached.getWaitlist();

      Map<CFANode, Set<CFANode>> dependencyMap = new HashMap<>();
      for (AbstractState s : reached) {
        assert s instanceof ARGState : "AbstractState of reached set not a composite state: " + s;
        DominatorState postDomState = AbstractStates.extractStateByType(s, DominatorState.class);
        if (postDomState == null) {
          throw new InvalidConfigurationException("No dominator state in computed composite "
              + "states");
        }
        CFANode currNode = AbstractStates.extractLocation(s);

        if (dependencyMap.containsKey(currNode)) {
          dependencyMap.get(currNode).addAll(postDomState);
        } else {
          dependencyMap.put(currNode, postDomState);
        }
      }

      return new PostDominators(dependencyMap);
    }

    public Set<CFANode> getNodes() {
      return postDominatorMap.keySet();
    }
  }
}
