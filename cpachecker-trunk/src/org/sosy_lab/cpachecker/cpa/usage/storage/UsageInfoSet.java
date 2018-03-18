package org.sosy_lab.cpachecker.cpa.usage.storage;

import com.google.common.collect.Iterables;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;

public class UsageInfoSet extends TreeSet<UsageInfo> {

  private static final long serialVersionUID = -5057827815596702715L;

  public UsageInfoSet() {}

  public UsageInfoSet(UsageInfo uinfo) {
    // Means, that the race is found and this particular usage is a part of the result;
    super();
    add(uinfo);
  }

  private UsageInfoSet(SortedSet<UsageInfo> pSet) {
    super(pSet);
  }

  public boolean remove(UsageState pUstate) {
    Iterator<UsageInfo> iterator = this.iterator();
    boolean changed = false;
    while (iterator.hasNext()) {
      UsageInfo uinfo = iterator.next();
      AbstractState keyState = uinfo.getKeyState();
      assert (keyState != null);
      if (UsageState.get(keyState).equals(pUstate)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  public UsageInfo getOneExample() {
    return Iterables.get(this, 0);
  }

  public UsageInfoSet copy() {
    // For avoiding concurrent modification in refinement
    return new UsageInfoSet(this);
  }
}
