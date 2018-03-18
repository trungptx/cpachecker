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
package org.sosy_lab.cpachecker.cpa.bdd;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sosy_lab.common.collect.CopyOnWriteSortedMap;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.variableclassification.Partition;

/**
 * This class guarantees a fixed order of variables in the BDD, that should be good for the
 * operations in the BitvectorManager.
 *
 * <p>This class is thread-safe, iff the used (Named-)RegionManager is thread-safe.
 */
@Options(prefix = "cpa.bdd")
public class PredicateManager {

  @Option(secure=true, description = "declare first bit of all vars, then second bit,...")
  private boolean initBitwise = true;

  @Option(secure=true, description = "declare the bits of a var from 0 to N or from N to 0")
  private boolean initBitsIncreasing = true;

  @Option(secure=true, description = "declare partitions ordered")
  private boolean initPartitionsOrdered = true;

  @Option(secure=true, description = "declare vars partitionwise")
  private boolean initPartitions = true;

  protected static final String TMP_VARIABLE = "__CPAchecker_tmp_var";
  private final ImmutableMap<Partition, String> varsToTmpVar;

  /**
   * Contains the varNames of all really tracked vars. This set may differ from the union of all
   * partitions, because not every variable, that appears in the sourcecode, is analyzed or even
   * reachable. This map contains the name of the variable and its bitsize in the BDD.
   */
  private final CopyOnWriteSortedMap<String, Integer> trackedVars =
      CopyOnWriteSortedMap.copyOf(PathCopyingPersistentTreeMap.<String, Integer>of());

  private final NamedRegionManager rmgr;

  PredicateManager(final Configuration config, final NamedRegionManager pRmgr, final CFA pCfa)
      throws InvalidConfigurationException {
    config.inject(this);
    this.rmgr = pRmgr;

    if (initPartitions) {
      varsToTmpVar = initVars(pCfa);
    } else {
      varsToTmpVar = null; // never accessed afterwards
    }
  }

  Collection<String> getTrackedVars() {
    return trackedVars.keySet();
  }

  /** return a specific temp-variable, that should be at correct positions in the BDD. */
  String getTmpVariableForPartition(final Partition pPartition) {
    if (initPartitions) {
      return varsToTmpVar.get(pPartition);
    } else {
      return TMP_VARIABLE;
    }
  }

  /**
   * The JavaBDDRegionManager orders the variables as they are declared (later vars are deeper in
   * the BDD). This function declares those vars in the beginning of the analysis, so that we can
   * choose between some orders.
   */
  private ImmutableMap<Partition, String> initVars(CFA cfa) {
    Collection<Partition> partitions;
    if (initPartitionsOrdered) {
      BDDPartitionOrderer d = new BDDPartitionOrderer(cfa);
      partitions = d.getOrderedPartitions();
    } else {
      assert cfa.getVarClassification().isPresent();
      partitions = cfa.getVarClassification().get().getPartitions(); // may be unsorted
    }

    Map<Partition, String> partitionToTmpVar = new HashMap<>();
    MachineModel machineModel = cfa.getMachineModel();
    for (Partition partition : partitions) {
      // maxBitSize is too much for most variables. we only create an order here, so this should not
      // matter.
      createPredicates(
          partition,
          machineModel.getSizeofLongLongInt() * machineModel.getSizeofCharInBits(),
          partitionToTmpVar);
    }
    return ImmutableMap.copyOf(partitionToTmpVar);
  }

  /**
   * This function declares variables for a given collection of vars.
   *
   * <p>The value 'bitsize' chooses how much bits are used for each var. The varname is build as
   * "varname@pos".
   */
  private void createPredicates(
      final Partition pPartition,
      final int bitsize,
      final Map<Partition, String> partitionToTmpVar) {

    assert bitsize >= 1 : "you need at least one bit for a variable.";

    // add a temporary variable for each set of variables, introducing it here is cheap, later it
    // may be  expensive.
    String tmpVar = TMP_VARIABLE + "_" + partitionToTmpVar.size();
    partitionToTmpVar.put(pPartition, tmpVar);

    // bitvectors [a2, a1, a0]
    // 'initBitwise' chooses between initialing each var separately or bitwise overlapped.
    if (initBitwise) {

      // [a2, b2, c2, a1, b1, c1, a0, b0, c0]
      boolean isTrackingSomething = false;
      for (int i = 0; i < bitsize; i++) {
        int index = initBitsIncreasing ? i : (bitsize - i - 1);
        for (String var : pPartition.getVars()) {
          createPredicateDirectly(var, index);
          isTrackingSomething = true;
        }
        if (isTrackingSomething) {
          createPredicateDirectly(tmpVar, index);
        }
      }

    } else {
      // [a2, a1, a0, b2, b1, b0, c2, c1, c0]
      boolean isTrackingSomething = false;
      for (String var : pPartition.getVars()) { // different loop order!
        for (int i = 0; i < bitsize; i++) {
          int index = initBitsIncreasing ? i : (bitsize - i - 1);
          createPredicateDirectly(var, index);
          isTrackingSomething = true;
        }
      }
      if (isTrackingSomething) {
        for (int i = 0; i < bitsize; i++) {
          int index = initBitsIncreasing ? i : (bitsize - i - 1);
          createPredicateDirectly(tmpVar, index);
        }
      }
    }
  }

  /** This function returns a region for a variable.
   * This function does not track any statistics. */
  private Region createPredicateDirectly(final String varName, final int index) {
    return rmgr.createPredicate(varName + "@" + index);
  }

  /**
   * This function returns regions containing bits of a variable. returns regions for positions of a
   * variable, s --> [s@2, s@1, s@0]. There is no check, if the variable is tracked by the the
   * precision. We assume that the variable was seen before and its bitsize is already known.
   */
  Region[] createPredicateWithoutPrecisionCheck(final String varName) {
    assert trackedVars.containsKey(varName) : "variable should already be known: " + varName;
    return createPredicateWithoutPrecisionCheck(varName, trackedVars.get(varName));
  }

  /**
   * This function returns regions containing bits of a variable. returns regions for positions of a
   * variable, s --> [s@2, s@1, s@0]. There is no check, if the variable is tracked by the the
   * precision.
   */
  Region[] createPredicateWithoutPrecisionCheck(final String varName, final int size) {
    Integer oldSize = trackedVars.get(varName);
    if (oldSize == null || oldSize < size) {
      trackedVars.put(varName, size);
    }
    final Region[] newRegions = new Region[size];
    for (int i = size - 1; i >= 0; i--) {
      // inverse order should be faster, because 'most changing bits' are at bottom position in BDDs.
      newRegions[i] = createPredicateDirectly(varName, i);
    }
    return newRegions;
  }

  /**
   * This function returns regions containing bits of a variable. returns regions for positions of a
   * variable, s --> [s@2, s@1, s@0]. If the variable is not tracked by the the precision, Null is
   * returned.
   */
  Region[] createPredicate(
      final String varName,
      final CType varType,
      final CFANode location,
      final int size,
      final VariableTrackingPrecision precision) {
    if (precision != null && !precision.isTracking(MemoryLocation.valueOf(varName), varType, location)) {
      return null;
    }
    if (!(varType.getCanonicalType() instanceof CSimpleType)) {
      // we cannot handle pointers, arrays, and structs with BDDCPA, thus we ignore them.
      // This is imprecise and unsound in cases of pointer aliasing (int x=0; int* y=&x; *y=1;).
      return null;
    }
    return createPredicateWithoutPrecisionCheck(varName, size);
  }
}
