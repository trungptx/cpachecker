/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.lock.effects;

import java.util.Objects;
import org.sosy_lab.cpachecker.cpa.lock.LockIdentifier;

public abstract class LockEffect implements AbstractLockEffect {

  protected final LockIdentifier target;

  protected LockEffect(LockIdentifier id) {
    target = id;
  }

  public abstract LockEffect cloneWithTarget(LockIdentifier id);

  protected abstract String getAction();

  @Override
  public String toString() {
    return getAction() + " " + target;
  }

  public LockIdentifier getAffectedLock() {
    return target;
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, getAction());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null ||
        getClass() != obj.getClass()) {
      return false;
    }
    LockEffect other = (LockEffect) obj;
    return Objects.equals(target, other.target) &&
           Objects.equals(getAction(), other.getAction());
  }


}
