/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.invariant.dump;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import symbpredabstraction.interfaces.SymbolicFormula;
import symbpredabstraction.interfaces.SymbolicFormulaManager;
import cfa.objectmodel.CFAEdge;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.TransferRelation;
import exceptions.CPATransferException;

/**
 * Transfer relation and strengthening for the DumpInvariant CPA
 * @author g.theoduloz
 */
public class DumpInvariantTransferRelation implements TransferRelation {

  private final SymbolicFormulaManager symbolicFormulaManager;
  
  public DumpInvariantTransferRelation(DumpInvariantCPA cpa)
  {
    symbolicFormulaManager = cpa.getSymbolicFormulaManager();
  }

  @Override
  public Collection<? extends AbstractElement> getAbstractSuccessors(
      AbstractElement pElement, Precision pPrecision, CFAEdge cfaEdge)
      throws CPATransferException {
    return Collections.singleton(DumpInvariantElement.TOP);
  }

  @Override
  public AbstractElement strengthen(AbstractElement el, List<AbstractElement> others, CFAEdge edge, Precision p)
    throws CPATransferException
  {
    SymbolicFormula result = null;
    for (AbstractElement other : others) {
      if (other instanceof InvariantReportingAbstractElement) {
        SymbolicFormula otherInv = ((InvariantReportingAbstractElement)other).getInvariant();
        if (otherInv != null) {
          if (result == null)
            result = otherInv;
          else
            result = symbolicFormulaManager.makeAnd(result, otherInv);
        }
      }
    }
    if (result != null)
      return new DumpInvariantElement(result);
    else
      return null;
  }

}
