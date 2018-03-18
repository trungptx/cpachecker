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
package org.sosy_lab.cpachecker.cpa.testtargets;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;

public enum TestTargetState implements AbstractState, Targetable, Graphable {
  TARGET(true),
  NO_TARGET(false);

  private final boolean isTarget;

  private TestTargetState(final boolean isTarget) {
    this.isTarget = isTarget;
  }

  @Override
  public boolean isTarget() {
    return isTarget;
  }

  @Override
  public @Nonnull Set<Property> getViolatedProperties() throws IllegalStateException {
    return Collections.emptySet();
  }

  @Override
  public String toDOTLabel() {
    return this.name();
  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }

  @Override
  public String toString() {
    return this.name();
  }
}
