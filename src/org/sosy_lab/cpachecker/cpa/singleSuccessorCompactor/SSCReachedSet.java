/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cpa.singleSuccessorCompactor;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.singleSuccessorCompactor.SSCSubgraphComputer.SSCARGState;

class SSCReachedSet extends ARGReachedSet.ForwardingARGReachedSet {

  private final ARGPath path;

  SSCReachedSet(ARGReachedSet pReached, ARGPath pPath) {
    super(pReached);
    path = pPath;
    assert path.getFirstState().getSubgraph().containsAll(path.asStatesList())
        : "path should traverse reachable states";
  }

  @Override
  public UnmodifiableReachedSet asReachedSet() {
    return new SSCReachedSetView(path, super.asReachedSet());
  }

  @Override
  public void removeSubtree(ARGState state) throws InterruptedException {
    removeSubtree(state, ImmutableList.of(), ImmutableList.of());
  }

  @Override
  public void removeSubtree(
      ARGState element, Precision newPrecision, Predicate<? super Precision> pPrecisionType)
      throws InterruptedException {
    removeSubtree(element, ImmutableList.of(newPrecision), ImmutableList.of(pPrecisionType));
  }

  /** This method over-approximates the cut-point.
   * It searches the latest original state and cuts there, i.e., it might cut more than needed,
   * but sufficiently enough to remove the property violation.
   * We assume that the precision gets stronger along all paths. */
  @Override
  @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
  public void removeSubtree(
      ARGState cutState,
      List<Precision> newPrecisions,
      List<Predicate<? super Precision>> pPrecisionTypes)
      throws InterruptedException {
    Preconditions.checkArgument(newPrecisions.size() == pPrecisionTypes.size());
    assert path.getFirstState().getSubgraph().contains(cutState);

    // find latest original ssc-state
    SSCARGState sccState = (SSCARGState) cutState;
    while (sccState.getSSCState().getWrappedState() != sccState.getWrappedState()) {
      sccState = (SSCARGState) Iterables.getOnlyElement(sccState.getParents());
    }
    assert sccState.getSSCState() == ((SSCARGState) cutState).getSSCState();

    // remove original state and its subtree
    super.removeSubtree(sccState.getSSCState(), newPrecisions, pPrecisionTypes);

    // post-processing, cleanup data-structures.
    // We remove all states reachable from 'cutState'. This step is not precise.
    // The only important step is to remove the last state of the reached-set.
    // We can ignore waitlist-updates and coverage here, because those things should not be needed.
    for (ARGState state : sccState.getSubgraph()) {
      state.removeFromARG();
    }
  }

  @Override
  public String toString() {
    return "SSCReachedSet {{" + asReachedSet().asCollection() + "}}";
  }
}
