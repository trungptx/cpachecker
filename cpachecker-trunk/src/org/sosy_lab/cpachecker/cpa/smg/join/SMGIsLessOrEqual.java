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
package org.sosy_lab.cpachecker.cpa.smg.join;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.types.c.CVoidType;
import org.sosy_lab.cpachecker.cpa.smg.CLangStackFrame;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;

/**
 * This class implements a faster way to test, if one smg is less or equal to another.
 * Simply joining two smg and requesting its status takes too long.
 */
public class SMGIsLessOrEqual {

  public static final ThreadSafeTimerContainer isLEQTimer =
      new ThreadSafeTimerContainer("Time for joining SMGs");
  public static final ThreadSafeTimerContainer globalsTimer =
      new ThreadSafeTimerContainer("Time for joining globals");
  public static final ThreadSafeTimerContainer stackTimer =
      new ThreadSafeTimerContainer("Time for joining stacks");
  public static final ThreadSafeTimerContainer heapTimer =
      new ThreadSafeTimerContainer("Time for joining heaps");

  private SMGIsLessOrEqual() {} // Utility class.

  /**
   *
   * Checks, if smg2 is less or equal to smg1.
   * @return true, iff smg1 is less or equal to smg2, false otherwise.
   */
  public static boolean isLessOrEqual(CLangSMG pSMG1, CLangSMG pSMG2) {

    TimerWrapper timer = isLEQTimer.getNewTimer();
    timer.start();
    try {

      // if smg1 is smg2, smg1 is equal to smg2
      if (pSMG1 == pSMG2) {
        return true;
      }

      // if smg1 has not allocated the same number of SMGObjects in the heap, it is not equal to smg2
      if (pSMG1.getHeapObjects().size() != pSMG2.getHeapObjects().size()) {
        return false;
      }

      if (pSMG1.getStackFrames().size() != pSMG2.getStackFrames().size()) {
        return false;
      }

      TimerWrapper gt = globalsTimer.getNewTimer();
      gt.start();
      try {
        if (!maybeGlobalsLessOrEqual(pSMG1, pSMG2)) {
          return false;
        }
      } finally {
        gt.stop();
      }

      TimerWrapper st = stackTimer.getNewTimer();
      st.start();
      try {
        if (!maybeStackLessOrEqual(pSMG1, pSMG2)) {
          return false;
        }
      } finally {
        st.stop();
      }

      TimerWrapper ht = heapTimer.getNewTimer();
      ht.start();
      try {
        if (!maybeHeapLessOrEqual(pSMG1, pSMG2)) {
          return false;
        }
      } finally {
        ht.stop();
      }

      return true;

    } finally {
      timer.stop();
    }
  }

  /** returns whether globals variables are "maybe LEQ" or "definitely not LEQ". */
  private static boolean maybeGlobalsLessOrEqual(CLangSMG pSMG1, CLangSMG pSMG2) {
    Map<String, SMGRegion> globals_in_smg1 = pSMG1.getGlobalObjects();
    Map<String, SMGRegion> globals_in_smg2 = pSMG2.getGlobalObjects();

    //technically, one should look if any SMGHVE exist in additional region in SMG1
    if (globals_in_smg1.size() > globals_in_smg2.size()) {
      return false;
    }

    // Check, whether global variables of smg1 is less or equal to smg2
    for (Entry<String, SMGRegion> entry : globals_in_smg1.entrySet()) {
      SMGObject globalInSMG1 = entry.getValue();
      String globalVar = entry.getKey();

      //technically, one should look if any SMGHVE exist in additional region in SMG1
      if(!globals_in_smg2.containsKey(globalVar)) {
        return false;
      }

      SMGObject globalInSMG2 = globals_in_smg2.get(entry.getKey());
      if (!isLessOrEqualFields(pSMG1, pSMG2, globalInSMG1, globalInSMG2)) {
        return false;
      }
    }

    return true;
  }

  /** returns whether variables on the stack are "maybe LEQ" or "definitely not LEQ". */
  private static boolean maybeStackLessOrEqual(CLangSMG pSMG1, CLangSMG pSMG2) {
    Iterator<CLangStackFrame> smg1stackIterator = pSMG1.getStackFrames().iterator();
    Iterator<CLangStackFrame> smg2stackIterator = pSMG2.getStackFrames().iterator();

    // Check, whether the stack frames of smg1 are less or equal to smg 2
    while (smg1stackIterator.hasNext() && smg2stackIterator.hasNext()) {
      CLangStackFrame frameInSMG1 = smg1stackIterator.next();
      CLangStackFrame frameInSMG2 = smg2stackIterator.next();

      //check, whether it is the same stack
      if (!frameInSMG1.getFunctionDeclaration().getOrigName()
          .equals(frameInSMG2.getFunctionDeclaration().getOrigName())) {
        return false;
      }

      //technically, one should look if any SMGHVE exist in additional region in SMG1
      if (frameInSMG1.getAllObjects().size() > frameInSMG2.getAllObjects().size()) {
        return false;
      }

      // check, whether they have different return values if present
      if (!(frameInSMG1.getFunctionDeclaration().getType().getReturnType().getCanonicalType() instanceof CVoidType)
          && !isLessOrEqualFields(pSMG1, pSMG2, frameInSMG1.getReturnObject(), frameInSMG2.getReturnObject())) {
        return false;
      }

      for (String localVar : frameInSMG1.getVariables().keySet()) {

        //technically, one should look if any SMGHVE exist in additional region in SMG1
        if ((!frameInSMG2.containsVariable(localVar))) {
          return false;
        }

        SMGRegion localInSMG1 = frameInSMG1.getVariable(localVar);
        SMGRegion localInSMG2 = frameInSMG2.getVariable(localVar);

        if (!isLessOrEqualFields(pSMG1, pSMG2, localInSMG1, localInSMG2)) {
          return false;
        }
      }
    }

    return true;
  }

  /** returns whether two heaps are "maybe LEQ" or "definitely not LEQ". */
  private static boolean maybeHeapLessOrEqual(CLangSMG pSMG1, CLangSMG pSMG2) {
    Set<SMGObject> heap_in_smg1 = pSMG1.getHeapObjects();
    Set<SMGObject> heap_in_smg2 = pSMG2.getHeapObjects();

    for (SMGObject object_in_smg1 : heap_in_smg1) {

      //technically, one should look if any SMGHVE exist in additional region in SMG1
      if (!heap_in_smg2.contains(object_in_smg1)) {
        return false;
      }

      //FIXME SMG Objects in heap have to be the same object to be comparable
      if (!isLessOrEqualFields(pSMG1, pSMG2, object_in_smg1, object_in_smg1)) {
        return false;
      }

      if (pSMG1.isObjectValid(object_in_smg1) != pSMG2.isObjectValid(object_in_smg1)) {
        return false;
      }
    }

    return true;
  }

  /** check whether an object is LEQ than another object. */
  private static boolean isLessOrEqualFields(
      CLangSMG pSMG1, CLangSMG pSMG2, SMGObject pSMGObject1, SMGObject pSMGObject2) {

    if (pSMGObject1.getSize() != pSMGObject2.getSize()) {
      throw new IllegalArgumentException("SMGJoinFields object arguments need to have identical size");
    }

    if (!(pSMG1.getObjects().contains(pSMGObject1) && pSMG2.getObjects().contains(pSMGObject2))) {
      throw new IllegalArgumentException("SMGJoinFields object arguments need to be included in parameter SMGs");
    }

    SMGEdgeHasValueFilter filterForSMG1 = SMGEdgeHasValueFilter.objectFilter(pSMGObject1);
    SMGEdgeHasValueFilter filterForSMG2 = SMGEdgeHasValueFilter.objectFilter(pSMGObject2);

    Iterable<SMGEdgeHasValue> HVE1 = pSMG1.getHVEdges(filterForSMG1);
    Iterable<SMGEdgeHasValue> HVE2 = pSMG2.getHVEdges(filterForSMG2);

    //TODO Merge Zero.
    for (SMGEdgeHasValue edge1 : HVE1) {
      filterForSMG2.filterAtOffset(edge1.getOffset()).filterByType(edge1.getType()).filterHavingValue(edge1.getValue());

      if (!filterForSMG2.edgeContainedIn(HVE2)) {
        return false;
      }

      Integer value = edge1.getValue();

      if (pSMG1.isPointer(value)) {
        if (!pSMG2.isPointer(value)) {
          return false;
        }

        SMGEdgePointsTo ptE1 = pSMG1.getPointer(value);
        SMGEdgePointsTo ptE2 = pSMG2.getPointer(value);
        String label1 = ptE1.getObject().getLabel();
        String label2 = ptE2.getObject().getLabel();
        long offset1 = ptE1.getOffset();
        long offset2 = ptE2.getOffset();

        //TODO How does one check, if two pointers point to the same region? You would have to recover the stack frame.
        if (!(offset1 == offset2 && label1.equals(label2))) {
          return false;
        }
      }
    }

    return true;
  }
}