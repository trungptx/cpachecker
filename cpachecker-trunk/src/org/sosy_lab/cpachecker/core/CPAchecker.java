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
package org.sosy_lab.cpachecker.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.common.ShutdownNotifier.interruptCurrentThreadOnShutdown;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import org.sosy_lab.common.AbstractMBean;
import org.sosy_lab.common.Optionals;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.ExternalCBMCAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.impact.ImpactAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.SpecificationProperty;
import org.sosy_lab.cpachecker.util.automaton.TargetLocationProvider;
import org.sosy_lab.cpachecker.util.automaton.TargetLocationProviderImpl;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

@Options
public class CPAchecker {

  public static interface CPAcheckerMXBean {
    public int getReachedSetSize();

    public void stop();
  }

  private static class CPAcheckerBean extends AbstractMBean implements CPAcheckerMXBean {

    private final ReachedSet reached;
    private final ShutdownManager shutdownManager;

    public CPAcheckerBean(ReachedSet pReached, LogManager logger, ShutdownManager pShutdownManager) {
      super("org.sosy_lab.cpachecker:type=CPAchecker", logger);
      reached = pReached;
      shutdownManager = pShutdownManager;
    }

    @Override
    public int getReachedSetSize() {
      return reached.size();
    }

    @Override
    public void stop() {
      shutdownManager.requestShutdown("A stop request was received via the JMX interface.");
    }

  }

  @Option(
    secure = true,
    name = "analysis.stopAfterError",
    description = "stop after the first error has been found"
  )
  private boolean stopAfterError = true;

  @Option(
    secure = true,
    name = "analysis.disable",
    description = "stop CPAchecker after startup (internal option, not intended for users)"
  )
  private boolean disableAnalysis = false;

  public static enum InitialStatesFor {
    /**
     * Function entry node of the entry function
     */
    ENTRY,

    /**
     * Set of function entry nodes of all functions.
     */
    FUNCTION_ENTRIES,

    /**
     * All locations that are possible targets of the analysis.
     */
    TARGET,

    /**
     * Function exit node of the entry function.
     */
    EXIT,

    /**
     * All function exit nodes of all functions and all loop heads of endless loops.
     */
    FUNCTION_SINKS,

    /**
     * All function exit nodes of the entry function, and all loop heads of endless loops.
     */
    PROGRAM_SINKS
  }

  @Option(
    secure = true,
    name = "analysis.initialStatesFor",
    description = "What CFA nodes should be the starting point of the analysis?"
  )
  private Set<InitialStatesFor> initialStatesFor = Sets.newHashSet(InitialStatesFor.ENTRY);

  @Option(
    secure = true,
    name = "analysis.partitionInitialStates",
    description =
        "Partition the initial states based on the type of location they were created for (see 'initialStatesFor')"
  )
  private boolean partitionInitialStates = false;

  @Option(
    secure = true,
    name = "specification",
    description =
        "comma-separated list of files with specifications that should be checked"
            + "\n(see config/specification/ for examples)"
  )
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private List<Path> specificationFiles = ImmutableList.of();

  @Option(
    secure = true,
    name = "backwardSpecification",
    description =
        "comma-separated list of files with specifications that should be used "
            + "\nin a backwards analysis; used if the analysis starts at the target states!"
            + "\n(see config/specification/ for examples)"
  )
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private List<Path> backwardSpecificationFiles = ImmutableList.of();

  @Option(
    secure = true,
    name = "analysis.algorithm.CBMC",
    description = "use CBMC as an external tool from CPAchecker"
  )
  private boolean runCBMCasExternalTool = false;

  @Option(
    secure = true,
    name = "analysis.serializedCfaFile",
    description =
        "if this option is used, the CFA will be loaded from the given file "
            + "instead of parsed from sourcefile."
  )
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private @Nullable Path serializedCfaFile = null;

  @Option(
    secure = true,
    name = "analysis.unknownAsTrue",
    description = "Do not report unknown if analysis terminated, report true (UNSOUND!)."
  )
  private boolean unknownAsTrue = false;

  @Option(
      secure = true,
      name = "analysis.counterexampleLimit",
      description = "Maximum number of counterexamples to be created."
    )
  private int cexLimit = 0;

  private final LogManager logger;
  private final Configuration config;
  private final ShutdownManager shutdownManager;
  private final ShutdownNotifier shutdownNotifier;
  private final CoreComponentsFactory factory;


  // The content of this String is read from a file that is created by the
  // ant task "init".
  // To change the version, update the property in build.xml.
  private static final String version;
  static {
    String v = "(unknown version)";
    try {
      URL url = CPAchecker.class.getClassLoader().getResource("org/sosy_lab/cpachecker/VERSION.txt");
      if (url != null) {
        String content = Resources.toString(url, StandardCharsets.US_ASCII).trim();
        if (content.matches("[a-zA-Z0-9 ._+:-]+")) {
          v = content;
        }
      }
    } catch (IOException e) {
      // Ignore exception, no better idea what to do here.
    }
    version = v;
  }

  public static String getVersion() {
    return getCPAcheckerVersion()
        + " (" + StandardSystemProperty.JAVA_VM_NAME.value()
        +  " " + StandardSystemProperty.JAVA_VERSION.value() + ")";
  }

  public static String getCPAcheckerVersion() {
    return version;
  }

  public CPAchecker(
      Configuration pConfiguration, LogManager pLogManager, ShutdownManager pShutdownManager)
      throws InvalidConfigurationException {
    config = pConfiguration;
    logger = pLogManager;
    shutdownManager = pShutdownManager;
    shutdownNotifier = pShutdownManager.getNotifier();

    config.inject(this);
    factory =
        new CoreComponentsFactory(
            pConfiguration, pLogManager, shutdownNotifier, new AggregatedReachedSets());
  }

  public CPAcheckerResult run(
      List<String> programDenotation, Set<SpecificationProperty> properties) {
    checkArgument(!programDenotation.isEmpty());

    logger.log(Level.INFO, "CPAchecker", getVersion(), "started");

    MainCPAStatistics stats = null;
    Algorithm algorithm = null;
    ReachedSet reached = null;
    CFA cfa = null;
    Result result = Result.NOT_YET_STARTED;
    String violatedPropertyDescription = "";

    final ShutdownRequestListener interruptThreadOnShutdown = interruptCurrentThreadOnShutdown();
    shutdownNotifier.register(interruptThreadOnShutdown);

    try {
      try {
        stats = new MainCPAStatistics(config, logger, shutdownNotifier);

        // create reached set, cpa, algorithm
        stats.creationTime.start();
        reached = factory.createReachedSet();

        if (runCBMCasExternalTool) {
          algorithm =
              new ExternalCBMCAlgorithm(checkIfOneValidFile(programDenotation), config, logger);

        } else {
          cfa = parse(programDenotation, stats);
          GlobalInfo.getInstance().storeCFA(cfa);
          shutdownNotifier.shutdownIfNecessary();

          ConfigurableProgramAnalysis cpa;
          Specification specification;
          stats.cpaCreationTime.start();
          try {
            specification =
                Specification.fromFiles(properties, specificationFiles, cfa, config, logger);
            cpa = factory.createCPA(cfa, specification);
          } finally {
            stats.cpaCreationTime.stop();
          }
          stats.setCPA(cpa);

          if (cpa instanceof StatisticsProvider) {
            ((StatisticsProvider)cpa).collectStatistics(stats.getSubStatistics());
          }

          GlobalInfo.getInstance().setUpInfoFromCPA(cpa);

          algorithm = factory.createAlgorithm(cpa, cfa, specification);

          if (algorithm instanceof StatisticsProvider) {
            ((StatisticsProvider)algorithm).collectStatistics(stats.getSubStatistics());
          }

          if (algorithm instanceof ImpactAlgorithm) {
            ImpactAlgorithm mcmillan = (ImpactAlgorithm)algorithm;
            reached.add(mcmillan.getInitialState(cfa.getMainFunction()), mcmillan.getInitialPrecision(cfa.getMainFunction()));
          } else {
            initializeReachedSet(reached, cpa, properties, cfa.getMainFunction(), cfa);
          }
        }

        printConfigurationWarnings();

        stats.creationTime.stop();
        shutdownNotifier.shutdownIfNecessary();
        // now everything necessary has been instantiated

        if (disableAnalysis) {
          return new CPAcheckerResult(
              Result.NOT_YET_STARTED, violatedPropertyDescription, null, cfa, stats);
        }

        // run analysis
        result = Result.UNKNOWN; // set to unknown so that the result is correct in case of exception

        AlgorithmStatus status = runAlgorithm(algorithm, reached, stats);

        stats.resultAnalysisTime.start();
        Collection<Property> violatedProperties = reached.getViolatedProperties();
        if (!violatedProperties.isEmpty()) {
          violatedPropertyDescription = Joiner.on(", ").join(violatedProperties);

          if (!status.isPrecise()) {
            result = Result.UNKNOWN;
          } else {
            result = Result.FALSE;
          }
        } else {
          result = analyzeResult(reached, status.isSound());
          if (unknownAsTrue && result == Result.UNKNOWN) {
            result = Result.TRUE;
          }
        }
        stats.resultAnalysisTime.stop();
      } catch (CPAException e) {
        // Dirty hack necessary until broken exception handling in parallel algorithm is fixed
        Throwable cause = e.getCause();
        if (cause != null && ParallelAlgorithm.UNEXPECTED_EXCEPTION_MSG.equals(e.getMessage())) {
          Throwables.throwIfInstanceOf(cause, IOException.class);
          Throwables.throwIfInstanceOf(cause, ParserException.class);
          Throwables.throwIfInstanceOf(cause, InvalidConfigurationException.class);
          Throwables.throwIfInstanceOf(cause, CPAException.class);
          Throwables.throwIfUnchecked(cause);
        }
        throw e;
      }

    } catch (IOException e) {
      logger.logUserException(Level.SEVERE, e, "Could not read file");

    } catch (ParserException e) {
      logger.logUserException(Level.SEVERE, e, "Parsing failed");
      StringBuilder msg = new StringBuilder();
      msg.append("Please make sure that the code can be compiled by a compiler.\n");
      if (e.getLanguage() == Language.C) {
        msg.append("If the code was not preprocessed, please use a C preprocessor\nor specify the -preprocess command-line argument.\n");
      }
      msg.append("If the error still occurs, please send this error message\ntogether with the input file to cpachecker-users@googlegroups.com.\n");
      logger.log(Level.INFO, msg);

    } catch (ClassNotFoundException e) {
      logger.logUserException(Level.SEVERE, e, "Could not read serialized CFA. Class is missing.");

    } catch (InvalidConfigurationException e) {
      logger.logUserException(Level.SEVERE, e, "Invalid configuration");

    } catch (InterruptedException e) {
      // CPAchecker must exit because it was asked to
      // we return normally instead of propagating the exception
      // so we can return the partial result we have so far
      logger.logUserException(Level.WARNING, e, "Analysis interrupted");

    } catch (CPAException e) {
      logger.logUserException(Level.SEVERE, e, null);

    } finally {
      CPAs.closeIfPossible(algorithm, logger);
      shutdownNotifier.unregister(interruptThreadOnShutdown);
    }
    return new CPAcheckerResult(result, violatedPropertyDescription, reached, cfa, stats);
  }

  private Path checkIfOneValidFile(List<String> fileDenotation)
      throws InvalidConfigurationException {
    if (fileDenotation.size() != 1) {
      throw new InvalidConfigurationException(
        "Exactly one code file has to be given.");
    }

    Path file = Paths.get(fileDenotation.get(0));

    try {
      IO.checkReadableFile(file);
    } catch (FileNotFoundException e) {
      throw new InvalidConfigurationException(e.getMessage());
    }

    return file;
  }

  private CFA parse(List<String> fileNames, MainCPAStatistics stats)
      throws InvalidConfigurationException, IOException, ParserException, InterruptedException,
      ClassNotFoundException {

    final CFA cfa;
    if (serializedCfaFile == null) {
      // parse file and create CFA
      CFACreator cfaCreator = new CFACreator(config, logger, shutdownNotifier);
      stats.setCFACreator(cfaCreator);
      cfa = cfaCreator.parseFileAndCreateCFA(fileNames);

    } else {
      // load CFA from serialization file
      try (InputStream inputStream = Files.newInputStream(serializedCfaFile);
          InputStream gzipInputStream = new GZIPInputStream(inputStream);
          ObjectInputStream ois = new ObjectInputStream(gzipInputStream)) {
        cfa = (CFA) ois.readObject();
      }
    }

    stats.setCFA(cfa);
    return cfa;
  }

  private void printConfigurationWarnings() {
    Set<String> unusedProperties = config.getUnusedProperties();
    if (!unusedProperties.isEmpty()) {
      logger.log(Level.WARNING, "The following configuration options were specified but are not used:\n",
          Joiner.on("\n ").join(unusedProperties), "\n");
    }
    Set<String> deprecatedProperties = config.getDeprecatedProperties();
    if (!deprecatedProperties.isEmpty()) {
      logger.log(Level.WARNING, "The following options are deprecated and will be removed in the future:\n",
          Joiner.on("\n ").join(deprecatedProperties), "\n");
    }
  }

  private AlgorithmStatus runAlgorithm(final Algorithm algorithm,
      final ReachedSet reached,
      final MainCPAStatistics stats) throws CPAException, InterruptedException {

    logger.log(Level.INFO, "Starting analysis ...");

    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    // register management interface for CPAchecker
    CPAcheckerBean mxbean = new CPAcheckerBean(reached, logger, shutdownManager);
    mxbean.register();

    stats.startAnalysisTimer();
    try {
      int counterExampleCount = 0;
      do {
        status = status.update(algorithm.run(reached));

        if (cexLimit > 0) {
          counterExampleCount = Optionals.presentInstances(
              from(reached)
              .filter(IS_TARGET_STATE)
              .filter(ARGState.class)
              .transform(ARGState::getCounterexampleInformation)).toList().size();
        }
        // either run only once (if stopAfterError == true)
        // or until the waitlist is empty
        // or until maximum number of counterexamples is reached
      } while (!stopAfterError && reached.hasWaitingState() && (cexLimit == 0 || cexLimit > counterExampleCount));

      logger.log(Level.INFO, "Stopping analysis ...");
      return status;

    } finally {
      stats.stopAnalysisTimer();

      // unregister management interface for CPAchecker
      mxbean.unregister();
    }
  }

  private Result analyzeResult(final ReachedSet reached, boolean isSound) {
    if (reached.hasWaitingState()) {
      logger.log(Level.WARNING, "Analysis not completed: there are still states to be processed.");
      return Result.UNKNOWN;
    }

    if (!isSound) {
      logger.log(Level.WARNING, "Analysis incomplete: no errors found, but not everything could be checked.");
      return Result.UNKNOWN;
    }

    return Result.TRUE;
  }

  private void addToInitialReachedSet(
      final Set<? extends CFANode> pLocations,
      final Object pPartitionKey,
      final ReachedSet pReached,
      final ConfigurableProgramAnalysis pCpa) throws InterruptedException {

    for (CFANode loc: pLocations) {
      StateSpacePartition putIntoPartition = partitionInitialStates
          ? StateSpacePartition.getPartitionWithKey(pPartitionKey)
          : StateSpacePartition.getDefaultPartition();

      AbstractState initialState = pCpa.getInitialState(loc, putIntoPartition);
      Precision initialPrecision = pCpa.getInitialPrecision(loc, putIntoPartition);

      pReached.add(initialState, initialPrecision);
    }
  }

  private void initializeReachedSet(
      final ReachedSet pReached,
      final ConfigurableProgramAnalysis pCpa,
      final Set<SpecificationProperty> pProperties,
      final FunctionEntryNode pAnalysisEntryFunction,
      final CFA pCfa)
      throws InvalidConfigurationException, InterruptedException {

    logger.log(Level.FINE, "Creating initial reached set");

    for (InitialStatesFor isf: initialStatesFor) {
      final ImmutableSet<? extends CFANode> initialLocations;
      switch (isf) {
      case ENTRY:
        initialLocations = ImmutableSet.of(pAnalysisEntryFunction);
        break;
      case EXIT:
        initialLocations = ImmutableSet.of(pAnalysisEntryFunction.getExitNode());
        break;
      case FUNCTION_ENTRIES:
        initialLocations = ImmutableSet.copyOf(pCfa.getAllFunctionHeads());
        break;
      case FUNCTION_SINKS:
        initialLocations = ImmutableSet.<CFANode>builder().addAll(getAllEndlessLoopHeads(pCfa.getLoopStructure().get()))
                                                          .addAll(getAllFunctionExitNodes(pCfa))
                                                          .build();
        break;
      case PROGRAM_SINKS:
          initialLocations =
              ImmutableSet.<CFANode>builder()
                  .addAll(
                      CFAUtils.getProgramSinks(
                          pCfa, pCfa.getLoopStructure().get(), pAnalysisEntryFunction))
                  .build();

        break;
        case TARGET:
          TargetLocationProvider tlp =
              new TargetLocationProviderImpl(shutdownNotifier, logger, pCfa);
          initialLocations =
              tlp.tryGetAutomatonTargetLocations(
                  pAnalysisEntryFunction,
                  Specification.fromFiles(
                      pProperties, backwardSpecificationFiles, pCfa, config, logger));
          break;
      default:
        throw new AssertionError("Unhandled case statement: " + initialStatesFor);
      }

      addToInitialReachedSet(initialLocations, isf, pReached, pCpa);
    }

    if (!pReached.hasWaitingState()
        && !(initialStatesFor.equals(Collections.singleton(InitialStatesFor.TARGET)))) {
      throw new InvalidConfigurationException("Initialization of the set of initial states failed: No analysis target found!");
    } else {
      logger.logf(
          Level.FINE,
          "Initial reached set has a waitlist of %d states.",
          pReached.getWaitlist().size());
    }

  }

  private Set<CFANode> getAllFunctionExitNodes(CFA cfa) {
    Set<CFANode> functionExitNodes = new HashSet<>();

    for (FunctionEntryNode node : cfa.getAllFunctionHeads()) {
      FunctionExitNode exitNode = node.getExitNode();
      if (cfa.getAllNodes().contains(exitNode)) {
        functionExitNodes.add(exitNode);
      }
    }
    return functionExitNodes;
  }

  private Collection<CFANode> getAllEndlessLoopHeads(LoopStructure structure) {
    return CFAUtils.getEndlessLoopHeads(structure);
  }
}
