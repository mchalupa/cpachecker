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
package org.sosy_lab.cpachecker.cpa.automaton;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CStorageClass;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class MarkingAutomatonBuilder {

  private static class MarkedState {

    @Nullable private final MarkedState predecessor;
    private final AutomatonInternalState state;
    @Nullable private final Integer markerId;

    public static MarkedState of(@Nullable MarkedState pPred, AutomatonInternalState pState, @Nullable Integer pMarkerId) {
      return new MarkedState(pPred, pState, pMarkerId);
    }

    private MarkedState(@Nullable MarkedState pPred, AutomatonInternalState pState, @Nullable Integer pMarkerId) {
      state = Preconditions.checkNotNull(pState);
      predecessor = pPred;
      markerId = pMarkerId;
    }

    public Optional<MarkedState> getPredecessor() {
      return Optional.fromNullable(predecessor);
    }

    public AutomatonInternalState getState() {
      return state;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((predecessor == null) ? 0 : predecessor.hashCode());
      result = prime * result + state.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) { return true; }
      if (obj == null) { return false; }
      if (!(obj instanceof MarkedState)) { return false; }
      MarkedState other = (MarkedState) obj;
      if (predecessor == null) {
        if (other.predecessor != null) { return false; }
      } else if (!predecessor.equals(other.predecessor)) { return false; }
      if (!state.equals(other.state)) { return false; }
      return true;
    }
  }

  private static class MarkerCode {

    private final int markerId;
    private final String namePrefix;

    private final CIdExpression markerVariable;
    private final CVariableDeclaration markerDeclaration;

    private final AAstNode markerIncrementStatement;
    private final AAstNode markerDeclarationStatement;

    public MarkerCode(String pNamePrefix, int pMarkerId) {
      markerId = pMarkerId;
      namePrefix = pNamePrefix;

      final String varName = String.format("__%s_MARKER_%d", namePrefix, markerId);

      markerDeclaration = new CVariableDeclaration(
          FileLocation.DUMMY, true, CStorageClass.AUTO,
          CNumericTypes.BOOL, varName, varName, varName,
          new CInitializerExpression(FileLocation.DUMMY, CIntegerLiteralExpression.ZERO) );

      markerVariable = new CIdExpression(FileLocation.DUMMY, CNumericTypes.BOOL, varName, markerDeclaration);

      markerDeclarationStatement = markerDeclaration;
      markerIncrementStatement = new CExpressionAssignmentStatement(FileLocation.DUMMY,
          markerVariable, CIntegerLiteralExpression.ONE);
    }

  }

  public static Automaton build(Automaton pInput) {
    Preconditions.checkNotNull(pInput);

    // Initialize the data structures
    Map<AutomatonTransition, Integer> edgeToMarkerMap = Maps.newHashMap();
    Set<AutomatonTransition> visited = Sets.newHashSet();
    Deque<MarkedState> worklist = Lists.newLinkedList();
    Map<Integer, MarkerCode> markerCode = Maps.newHashMap();
    Multimap<AutomatonTransition, MarkedState> markedTargetStates = HashMultimap.create();

    int edgeId = 0;

    // We start from the initial automaton state
    worklist.add(MarkedState.of(null, pInput.getInitialState(), null));

    // Perform the marking...
    while (worklist.size() > 0) {
      MarkedState q = worklist.pop();

      for (AutomatonTransition t: q.getState().getTransitions()) {
        if (!visited.add(t)) {
          // Detect and break cycles
          continue;
        }
        if (t.getFollowState().equals(q.getState())) {
          // Skip stuttering transitions
          continue;
        }

        final int markerId = edgeId++;

        edgeToMarkerMap.put(t, Integer.valueOf(markerId));

        final MarkedState ms = MarkedState.of(q, t.getFollowState(), markerId);
        final MarkerCode mc = new MarkerCode(pInput.getName(), markerId);
        markerCode.put(markerId, mc);

        if (ms.getState().isTarget()) {
          markedTargetStates.put(t, ms);
        }

        worklist.add(ms);
      }
    }

    // Build the new automaton
    final List<AutomatonInternalState> resultStates = Lists.newArrayList();
    final String resultInitialStateName = "qInitMarkers";

    // -- construct new states and transitions
    for (AutomatonInternalState q: pInput.getStates()) {

      List<AutomatonTransition> qPrimeTrans = Lists.newArrayList();

      for (AutomatonTransition t: q.getTransitions()) {
        Integer markerId = edgeToMarkerMap.get(t);

        List<AAstNode> newShadowCode = Lists.newLinkedList();
        newShadowCode.addAll(t.getShadowCode());

        if (markerId != null) {
          final MarkerCode mc = markerCode.get(markerId);

          // -- add the marker code to the transition
          newShadowCode.add(0, mc.markerIncrementStatement);
        }

        // -- in case of a target state: add an assumption on the markers
        List<AStatement> assumptions = Lists.newArrayList();
        if (t.getFollowState().isTarget()) {
          Collection<MarkedState> markers = markedTargetStates.get(t);
          for (MarkedState m: markers) {

            List<Integer> pathMarkers = Lists.newArrayList();
            MarkedState travers = m;
            while (travers != null) {
              if (travers.markerId != null) {
                pathMarkers.add(travers.markerId);
              }
              travers = travers.predecessor;
            }

            for (Integer pm: pathMarkers) {
              MarkerCode mc = markerCode.get(pm);
              Preconditions.checkState(mc != null);
              assumptions.add(new CExpressionStatement(FileLocation.DUMMY,
                  new CBinaryExpression(FileLocation.DUMMY,
                      CNumericTypes.BOOL, CNumericTypes.BOOL,
                      mc.markerVariable,
                      CIntegerLiteralExpression.ONE, CBinaryExpression.BinaryOperator.EQUALS)));
            }

            qPrimeTrans.add(new AutomatonTransition(
                t.getTrigger(),
                ImmutableList.<AutomatonBoolExpr>of(), //FIXME
                assumptions,
                true,               //FIXME
                newShadowCode,
                t.getActions(),
                t.getFollowState().getName(), null,
                t.getViolatedWhenEnteringTarget(),
                t.getViolatedWhenAssertionFailed()));
          }
        } else {
          qPrimeTrans.add(new AutomatonTransition(
              t.getTrigger(),
              ImmutableList.<AutomatonBoolExpr>of(), //FIXME
              t.getAssumptions(),
              true,               //FIXME
              newShadowCode,
              t.getActions(),
              t.getFollowState().getName(), null,
              t.getViolatedWhenEnteringTarget(),
              t.getViolatedWhenAssertionFailed()));
        }
      }

      resultStates.add(new AutomatonInternalState(q.getName(), qPrimeTrans, q.isTarget(), q.isNonDetState()));
    }

    // -- construct the new initial state that initializes the markers when entering the program
    List<AAstNode> initMarkersCode = Lists.newLinkedList();
    for (MarkerCode e: markerCode.values()) {
      initMarkersCode.add(e.markerDeclarationStatement);
    }
    final AutomatonTransition initTransition = new AutomatonTransition(AutomatonBoolExpr.MatchProgramEntry.INSTANCE,
        ImmutableList.<AutomatonBoolExpr>of(),
        ImmutableList.<AStatement>of(), true,
        initMarkersCode, ImmutableList.<AutomatonAction>of(), pInput.getInitialState().getName(),
        null, ImmutableSet.<SafetyProperty>of(), ImmutableSet.<SafetyProperty>of());

    final AutomatonInternalState resultInitialState = new AutomatonInternalState(resultInitialStateName,
        ImmutableList.of(initTransition), false, false);
    resultStates.add(resultInitialState);

    resultStates.add(AutomatonInternalState.BOTTOM);
    resultStates.add(AutomatonInternalState.INTERMEDIATEINACTIVE);

    // -- assemble the resulting automaton
    try {
      return new Automaton(pInput.getPropertyFactory(),
          pInput.getName(), Maps.<String, AutomatonVariable> newHashMap(),
          resultStates, resultInitialStateName);

    } catch (InvalidAutomatonException e) {
      throw new RuntimeException("Conversion failed!", e);
    }

  }

}
