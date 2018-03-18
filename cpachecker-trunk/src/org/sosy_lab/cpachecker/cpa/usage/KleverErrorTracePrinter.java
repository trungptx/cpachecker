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
package org.sosy_lab.cpachecker.cpa.usage;

import static com.google.common.collect.FluentIterable.from;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.sosy_lab.common.Appender;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.AssumeCase;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

@Options(prefix="cpa.usage.export")
public class KleverErrorTracePrinter extends ErrorTracePrinter {

  @Option(secure=true, name="witnessTemplate",
      description="export counterexample core as text file")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate errorPathFile = PathTemplate.ofFormatString("witness.%s.graphml");

  private static class ThreadIterator implements Iterator<Integer> {
    private Set<Integer> usedThreadIds;
    private int currentThread;

    public ThreadIterator() {
      usedThreadIds = new HashSet<>();
      currentThread = 0;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public Integer next() {
      int nextThread = currentThread + 1;
      while (usedThreadIds.contains(nextThread)) {
        nextThread++;
      }
      currentThread = nextThread;
      usedThreadIds.add(Integer.valueOf(nextThread));
      return getCurrentThread();
    }

    public int getCurrentThread() {
      return currentThread;
    }

    public void setCurrentThread(int newVal) {
      currentThread = newVal;
    }
  }

  public KleverErrorTracePrinter(
      Configuration c,
      BAMMultipleCEXSubgraphComputer pT,
      CFA pCfa,
      LogManager pL,
      LockTransferRelation lT)
      throws InvalidConfigurationException {
    super(c, pT, pCfa, pL, lT);
    config.inject(this, KleverErrorTracePrinter.class);
  }

  int idCounter = 0;
  ThreadIterator threadIterator;

  private String getCurrentId() {
    return "A" + idCounter;
  }

  private String getNextId() {
    idCounter++;
    return getCurrentId();
  }

  private Element currentNode;

  @Override
  protected void printUnsafe(SingleIdentifier pId, Pair<UsageInfo, UsageInfo> pTmpPair) {
    UsageInfo firstUsage = pTmpPair.getFirst();
    UsageInfo secondUsage = pTmpPair.getSecond();
    List<CFAEdge> firstPath, secondPath;

    firstPath = getPath(firstUsage);
    if (firstPath == null) {
      return;
    }
    secondPath = getPath(secondUsage);
    if (secondPath == null) {
      return;
    }
    try {
      String defaultSourcefileName =
          from(firstPath)
              .filter(FILTER_EMPTY_FILE_LOCATIONS)
              .get(0)
              .getFileLocation()
              .getFileName();

      GraphMlBuilder builder =
          new GraphMlBuilder(
              WitnessType.VIOLATION_WITNESS,
              defaultSourcefileName,
              cfa,
              new VerificationTaskMetaData(config, Specification.alwaysSatisfied()));

      idCounter = 0;
      threadIterator = new ThreadIterator();
      Element result = builder.createNodeElement(getCurrentId(), NodeType.ONPATH);
      builder.addDataElementChild(result, NodeFlag.ISENTRY.key, "true");

      Iterator<CFAEdge> firstIterator = getIterator(firstPath);
      Iterator<CFAEdge> secondIterator = getIterator(secondPath);

      if (!firstIterator.hasNext()) {
        // Empty path is strange
        logger.log(Level.WARNING, "Path to " + firstUsage + "is empty");
        return;
      }

      if (!secondIterator.hasNext()) {
        // Empty path is strange
        logger.log(Level.WARNING, "Path to " + secondUsage + "is empty");
        return;
      }

      CFAEdge firstEdge = firstIterator.next();
      CFAEdge secondEdge = secondIterator.next();
      int forkThread = 0;

      while (firstEdge.equals(secondEdge)) {
        if (isThreadCreateNFunction(firstEdge)) {
          break;
        }

        printEdge(builder, firstEdge);

        if (!firstIterator.hasNext()) {
          logger.log(Level.WARNING, "Path to " + firstUsage + "is ended before deviding");
          return;
        } else if (!secondIterator.hasNext()) {
          logger.log(Level.WARNING, "Path to " + secondUsage + "is ended before deviding");
          return;
        }

        firstEdge = firstIterator.next();
        secondEdge = secondIterator.next();
      }

      forkThread = threadIterator.getCurrentThread();
      printEdge(builder, firstEdge);
      printPath(firstUsage, firstIterator, builder);

      threadIterator.setCurrentThread(forkThread);
      printEdge(builder, secondEdge);
      printPath(secondUsage, secondIterator, builder);

      builder.addDataElementChild(currentNode, NodeFlag.ISVIOLATION.key, "true");

      Path currentPath = errorPathFile.getPath(createUniqueName(pId));
      IO.writeFile(currentPath, Charset.defaultCharset(), (Appender) a -> builder.appendTo(a));

    } catch (IOException e) {
      logger.log(Level.SEVERE, "Exception during printing unsafe " + pId + ": " + e.getMessage());
    } catch (ParserConfigurationException e) {
      logger.log(Level.SEVERE, "Exception during printing unsafe " + pId + ": " + e.getMessage());
    } catch (DOMException e1) {
      logger.log(Level.SEVERE, "Exception during printing unsafe " + pId + ": " + e1.getMessage());
    } catch (InvalidConfigurationException e1) {
      logger.log(Level.SEVERE, "Exception during printing unsafe " + pId + ": " + e1.getMessage());
    }
  }

  private void printPath(UsageInfo usage, Iterator<CFAEdge> iterator, GraphMlBuilder builder) {
    SingleIdentifier pId = usage.getId();
    List<CFAEdge> path = usage.getPath();
    CFAEdge warning = null;

    for (CFAEdge edge : path) {
      if (edge.getPredecessor() == usage.getLine().getNode()) {
        if (edge.toString().contains(pId.getName())) {
          warning = edge;
          break;
        } else if (edge instanceof CFunctionCallEdge) {
          // if the whole line is 'a = f(b)' the edge contains only 'f(b)'
          if (((CFunctionCallEdge) edge)
              .getSummaryEdge()
              .getRawStatement()
              .contains(pId.getName())) {
            warning = edge;
            break;
          }
        }
      }
    }

    if (warning == null) {
      logger.log(Level.WARNING, "Can not determine an unsafe edge");
    }

    Element lastWarningEdge = null;

    while (iterator.hasNext()) {
      CFAEdge pEdge = iterator.next();

      Element edge = printEdge(builder, pEdge);

      if (pEdge == warning) {
        lastWarningEdge = edge;
      }
    }

    if (lastWarningEdge != null) {
      builder.addDataElementChild(lastWarningEdge, KeyDef.WARNING, usage.getWarningMessage());
    }
  }

  private Element printEdge(GraphMlBuilder builder, CFAEdge edge) {

    if (isThreadCreateFunction(edge)) {
      CFunctionSummaryEdge sEdge = ((CFunctionCallEdge) edge).getSummaryEdge();
      Element result = printEdge(builder, sEdge);
      builder.addDataElementChild(
          result, KeyDef.CREATETHREAD, Integer.toString(threadIterator.next()));
    }
    return printEdge(builder, edge, getCurrentId(), getNextId());
  }

  private Element printEdge(GraphMlBuilder builder, CFAEdge edge, String currentId, String nextId) {
    Element result = builder.createEdgeElement(currentId, nextId);
    dumpCommonInfoForEdge(builder, result, edge);

    String note = getNoteFor(edge);
    if (!note.isEmpty()) {
      builder.addDataElementChild(result, KeyDef.NOTE, note);
    }
    currentNode = builder.createNodeElement(nextId, NodeType.ONPATH);
    return result;
  }

  private void dumpCommonInfoForEdge(GraphMlBuilder builder, Element result, CFAEdge pEdge) {
    if (pEdge.getSuccessor() instanceof FunctionEntryNode) {
      FunctionEntryNode in = (FunctionEntryNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONENTRY, in.getFunctionName());
    }
    if (pEdge.getSuccessor() instanceof FunctionExitNode) {
      FunctionExitNode out = (FunctionExitNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONEXIT, out.getFunctionName());
    }

    if (pEdge instanceof AssumeEdge) {
      AssumeEdge a = (AssumeEdge) pEdge;
      AssumeCase assumeCase = a.getTruthAssumption() ? AssumeCase.THEN : AssumeCase.ELSE;
      builder.addDataElementChild(result, KeyDef.CONTROLCASE, assumeCase.toString());
    }

    FileLocation location = pEdge.getFileLocation();
    assert (location != null) : "should be filtered";
    builder.addDataElementChild(result, KeyDef.ORIGINFILE, location.getFileName());
    builder.addDataElementChild(
        result, KeyDef.STARTLINE, Integer.toString(location.getStartingLineInOrigin()));
    builder.addDataElementChild(result, KeyDef.OFFSET, Integer.toString(location.getNodeOffset()));

    if (!pEdge.getRawStatement().trim().isEmpty()) {
      builder.addDataElementChild(result, KeyDef.SOURCECODE, pEdge.getRawStatement());
    }

    builder.addDataElementChild(
        result, KeyDef.THREADID, Integer.toString(threadIterator.getCurrentThread()));
  }

  private Iterator<CFAEdge> getIterator(List<CFAEdge> path) {
    return from(path).filter(FILTER_EMPTY_FILE_LOCATIONS).iterator();
  }

  private boolean isThreadCreateFunction(CFAEdge pEdge) {
    return getThreadCreateStatementIfExists(pEdge) != null;
  }

  private boolean isThreadCreateNFunction(CFAEdge pEdge) {
    CThreadCreateStatement stmnt = getThreadCreateStatementIfExists(pEdge);
    return stmnt == null ? false : stmnt.isSelfParallel();
  }

  private CThreadCreateStatement getThreadCreateStatementIfExists(CFAEdge pEdge) {
    if (pEdge instanceof CFunctionCallEdge) {
      CFunctionSummaryEdge sEdge = ((CFunctionCallEdge) pEdge).getSummaryEdge();
      CFunctionCall fCall = sEdge.getExpression();
      if (fCall instanceof CThreadCreateStatement) {
        return (CThreadCreateStatement) fCall;
      }
    }
    return null;
  }
}
