/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-04-26 16:57:51 -0500 (Thu, 26 Apr 2007) $
 * $Revision: 7502 $
 *
 * Copyright (C) 2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jmol.smiles;

import javajs.util.AU;
import javajs.util.Lst;
import javajs.util.P3;
import javajs.util.PT;

import org.jmol.api.SmilesMatcherInterface;
import org.jmol.java.BS;
import org.jmol.util.BNode;
import org.jmol.util.BSUtil;
import org.jmol.util.Node;
import org.jmol.util.Point3fi;
import org.jmol.viewer.JC;

/**
 * Originating author: Nicholas Vervelle
 * 
 * A class to handle a variety of SMILES/SMARTS-related functions, including: --
 * determining if two SMILES strings are equivalent -- determining the molecular
 * formula of a SMILES or SMARTS string -- searching for specific runs of atoms
 * in a 3D model -- searching for specific runs of atoms in a SMILES description
 * -- generating valid (though not canonical) SMILES and bioSMILES strings --
 * getting atom-atom correlation maps to be used with biomolecular alignment
 * methods
 * 
 * <p>
 * The original SMILES description can been found at the <a
 * href="http://www.daylight.com/smiles/">SMILES Home Page</a>.
 * 
 * Specification for this implementation can be found in package.html.
 * 
 * <p>
 * 
 * <pre>
 * <code>
 * public methods:
 * 
 * int areEqual  -- checks a SMILES string against a reference (-1 for error; 0 for no finds; >0 for number of finds)
 * 
 * BitSet[] find  -- finds one or more occurances of a SMILES or SMARTS string within a SMILES string
 * 
 * int[][] getCorrelationMaps  -- returns correlated arrays of atoms
 * 
 * String getLastError  -- returns any error that was last encountered.
 * 
 * String getMolecularFormula   -- returns the MF of a SMILES or SMARTS string
 * 
 * String getRelationship -- returns isomeric relationship
 * 
 * String getSmiles  -- returns a standard SMILES string or a
 *                  Jmol BIOSMILES string with comment header.
 * 
 * BitSet getSubstructureSet  -- returns a single BitSet with all found atoms included
 *   
 *   
 *   in Jmol script:
 *   
 *   string2.find("SMILES", string1)
 *   string2.find("SMARTS", string1)
 *   
 *   e.g.
 *   
 *     print "CCCC".find("SMILES", "C[C]")
 * 
 *   select search("smartsString")
 *   
 *   All bioSMARTS strings begin with ~ (tilde).
 *   
 * </code>
 * </pre>
 * 
 * @author Bob Hanson
 * 
 */
public class SmilesMatcher implements SmilesMatcherInterface {

  private final static int SMILES_MODE_BITSET       = 0x001000;
  private final static int SMILES_MODE_ARRAY        = 0x002000;
  private final static int SMILES_MODE_MAP          = 0x003000;
  private final static int SMILES_MODE_MASK         = 0x00F000;


  private boolean checkFlag(int flags, int flag) {
    return (flags & flag) == flag;
  }

  @Override
  public String getLastException() {
    return InvalidSmilesException.getLastError();
  }

  @Override
  public String getMolecularFormula(String pattern, boolean isSmarts)
      throws Exception {
    InvalidSmilesException.clear();
    // note: Jmol may undercount the number of hydrogen atoms
    // for aromatic amines where the ring bonding to N is 
    // not explicit. Each "n" will be assigned a bonding count
    // of two unless explicitly indicated as -n-.
    // Thus, we take the position that "n" is the 
    // N of pyridine unless otherwise indicated.
    //
    // For example:
    //   $ print "c1ncccc1C".find("SMILES","MF")
    //   H 7 C 5 N 1   (correct)
    //   $ print "c1nc-n-c1C".find("SMILES","MF")
    //   H 6 C 4 N 2   (correct)
    // but
    //   $ print "c1ncnc1C".find("SMILES","MF")
    //   H 5 C 4 N 2   (incorrect)
    SmilesSearch search = SmilesParser.getMolecule(pattern, isSmarts);
    search.createTopoMap(null);
    search.nodes = search.jmolAtoms;
    return search.getMolecularFormula(!isSmarts, null, false);
  }

  /**
   * internal to Jmol -- called by org.jmol.Viewer.getSmiles
   */
  
  @Override
  public String getSmiles(Node[] atoms, int ac, BS bsSelected,
                             String bioComment, int flags) throws Exception {
//  boolean asBioSmiles, boolean bioAllowUnmatchedRings,  boolean bioAddCrossLinks, boolean explicitH
     InvalidSmilesException.clear();
    if (checkFlag(flags, JC.SMILES_BIO)) {
      boolean bioAllowUnmatchedRings = checkFlag(flags,
          JC.SMILES_BIO_ALLOW_UNMACHED_RINGS);
      boolean bioAddCrossLinks = checkFlag(flags, JC.SMILES_BIO_CROSSLINK);
      return (new SmilesGenerator()).getBioSmiles((BNode[]) atoms, ac,
          bsSelected, bioAllowUnmatchedRings, bioAddCrossLinks, bioComment);
    }
    boolean explicitH = checkFlag(flags, JC.SMILES_EXPLICIT_H);
    boolean topologyOnly = checkFlag(flags, JC.SMILES_TOPOLOGY);
    boolean getAromatic = !checkFlag(flags, JC.SMILES_NOAROMATIC);
    
    return (new SmilesGenerator()).getSmiles(atoms, ac, bsSelected, explicitH,
        topologyOnly, getAromatic);
  }

  @Override
  public int areEqual(String smiles1, String smiles2) throws Exception {
    InvalidSmilesException.clear();
    BS[] result = (BS[]) findPriv(smiles1, SmilesParser.getMolecule(smiles2, false),
        (smiles1.indexOf("*") >= 0 ? JC.SMILES_TYPE_SMARTS
            : JC.SMILES_TYPE_SMILES | JC.SMILES_MATCH_ALL)
            | JC.SMILES_RETURN_FIRST | SMILES_MODE_ARRAY);
    return (result == null ? -1 : result.length);
  }

  /**
   * for JUnit test, mainly
   * 
   * @param smiles
   * @param molecule
   * @return true only if the SMILES strings match and there are no errors
   * @throws Exception
   */
  public boolean areEqualTest(String smiles, SmilesSearch molecule)
      throws Exception {
    //String pattern, String smiles, boolean isSmarts,    boolean firstMatchOnly    
    BS[] ret = (BS[]) findPriv(smiles, molecule, 
          JC.SMILES_TYPE_SMILES | JC.SMILES_MATCH_ALL 
        | JC.SMILES_RETURN_FIRST | SMILES_MODE_ARRAY);
    return (ret != null && ret.length == 1);
  }

  /**
   * 
   * Searches for all matches of a pattern within a SMILES string. If SMILES
   * (not isSmarts), requires that all atoms be part of the match.
   * 
   * 
   * @param pattern
   *        SMILES or SMARTS pattern.
   * @param smiles
   * @param isSmarts
   *        TRUE for SMARTS strings, FALSE for SMILES strings
   * @param firstMatchOnly
   * @return array of correlations of occurances of pattern within smiles
   * @throws Exception
   */
  @Override
  public int[][] find(String pattern, String smiles, boolean isSmarts,
                   boolean firstMatchOnly) throws Exception {

    InvalidSmilesException.clear();
    smiles = SmilesParser.cleanPattern(smiles);
    pattern = SmilesParser.cleanPattern(pattern);
    SmilesSearch search = SmilesParser.getMolecule(smiles, false);
    // boolean isSmarts,  boolean matchAllAtoms, boolean firstMatchOnly

    return (int[][]) findPriv(pattern, search, 
        (isSmarts? JC.SMILES_TYPE_SMARTS : JC.SMILES_TYPE_SMILES | JC.SMILES_MATCH_ALL)
        | (firstMatchOnly ? JC.SMILES_RETURN_FIRST : 0) | SMILES_MODE_MAP);
  }

  @Override
  public String getRelationship(String smiles1, String smiles2)
      throws Exception {
    if (smiles1 == null || smiles2 == null || smiles1.length() == 0
        || smiles2.length() == 0)
      return "";
    String mf1 = getMolecularFormula(smiles1, false);
    String mf2 = getMolecularFormula(smiles2, false);
    if (!mf1.equals(mf2))
      return "none";
    boolean check;
    // note: find smiles1 IN smiles2 here
    int n1 = countStereo(smiles1);
    int n2 = countStereo(smiles2);
    check = (n1 == n2 && areEqual(smiles2, smiles1) > 0);
    if (!check) {
      // MF matched, but didn't match SMILES
      String s = smiles1 + smiles2;
      if (s.indexOf("/") >= 0 || s.indexOf("\\") >= 0 || s.indexOf("@") >= 0) {
        if (n1 == n2 && n1 > 0) {
          // reverse chirality centers
          smiles1 = reverseChirality(smiles1);
          check = (areEqual(smiles1, smiles2) > 0);
          if (check)
            return "enantiomers";
        }
        // remove all stereochemistry from SMILES string
        check = (areEqual("/nostereo/" + smiles2, smiles1) > 0);
        if (check)
          return (n1 == n2 ? "diastereomers" : "ambiguous stereochemistry!");
      }
      // MF matches, but not enantiomers or diasteriomers
      return "constitutional isomers";
    }
    return "identical";
  }

  @Override
  public String reverseChirality(String smiles) {
    smiles = PT.rep(smiles, "@@", "!@");
    smiles = PT.rep(smiles, "@", "@@");
    smiles = PT.rep(smiles, "!@@", "@");
    smiles = PT.rep(smiles, "@@SP", "@SP");
    smiles = PT.rep(smiles, "@@OH", "@OH");
    smiles = PT.rep(smiles, "@@TB", "@TB");
    return smiles;
  }

  /**
   * Returns a bitset matching the pattern within atoms.
   * 
   * @param pattern
   *        SMILES or SMARTS pattern.
   * @param atoms
   * @param ac
   * @param bsSelected
   * @return BitSet indicating which atoms match the pattern.
   */

  @Override
  public BS getSubstructureSet(String pattern, Node[] atoms, int ac, BS bsSelected, int flags) throws Exception {
    return (BS) matchPriv(pattern, atoms, ac, bsSelected, null, 
        flags | JC.SMILES_MATCH_ONE | SMILES_MODE_BITSET);
  }

  /**
   * called by ForceFieldMMFF.setAtomTypes
   * 
   */
  @Override
  public void getSubstructureSets(String[] smarts, Node[] atoms, int ac,
                                  int flags, BS bsSelected, Lst<BS> ret,
                                  Lst<BS>[] vRings) throws Exception {
    InvalidSmilesException.clear();
    SmilesParser sp = new SmilesParser(true);
    SmilesSearch search = null;
    search = sp.parse("");
    search.firstMatchOnly = false;
    search.matchAllAtoms = false;
    search.jmolAtoms = atoms;
    search.jmolAtomCount = Math.abs(ac);
    search.setSelected(bsSelected);
    search.getRingData(true, flags, vRings);
    search.asVector = false;
    search.subSearches = new SmilesSearch[1];
    search.getSelections();
    BS bsDone = new BS();

    for (int i = 0; i < smarts.length; i++) {
      if (smarts[i] == null || smarts[i].length() == 0
          || smarts[i].startsWith("#")) {
        ret.addLast(null);
        continue;
      }
      search.clear();
      SmilesSearch ss = sp.getSearch(search,
          SmilesParser.cleanPattern(smarts[i]), flags);
      search.subSearches[0] = ss;
      BS bs = BSUtil.copy((BS) search.search(false));//.subsearch(ss, false, false));
      //System.out.println(i + " " + bs);
      ret.addLast(bs);
      bsDone.or(bs);
      if (bsDone.cardinality() == ac)
        return;
      //if (ret[i] != null && ret[i].nextSetBit(0) >= 0)
      //System.out.println(smarts[i] + "  "+ ret[i]);
    }
  }

  /**
   * Returns a vector of bitsets indicating which atoms match the pattern.
   * 
   * @param pattern
   *        SMILES or SMARTS pattern.
   * @param atoms
   * @param ac
   * @param bsSelected
   * @param bsAromatic
   * @return BitSet Array indicating which atoms match the pattern.
   * @throws Exception
   */
  @Override
  public BS[] getSubstructureSetArray(String pattern, Node[] atoms, int ac,
                                      BS bsSelected, BS bsAromatic, int flags)
      throws Exception {
    //boolean isSmarts, boolean matchAllAtoms,  boolean firstMatchOnly, int mode
    return (BS[]) matchPriv(pattern, atoms, ac, bsSelected, bsAromatic, 
        flags | JC.SMILES_MATCH_ONE | SMILES_MODE_ARRAY);
  }
  
  /**
   * Generate a topological SMILES string from a set of faces
   * @param faces
   * @param atomCount
   * @return topological SMILES string
   * @throws Exception 
   */
  @Override
  public String polyhedronToSmiles(int[][] faces, int atomCount, P3[] points) throws Exception {
    SmilesAtom[] atoms = new SmilesAtom[atomCount];
    for (int i = 0; i < atomCount; i++) {
      atoms[i] = new SmilesAtom();
      P3 pt = (points == null ? null : points[i]);
      atoms[i].elementNumber = (pt == null ? -2 : 
        pt instanceof Node ? ((Node) pt).getElementNumber() : 
          pt instanceof Point3fi ? ((Point3fi) pt).sD
              : -2);
      atoms[i].index = i;
    }
    int nBonds = 0;
    for (int i = faces.length; --i >= 0;) {
      int[] face = faces[i];
      int n = face.length;
      for (int j = n; --j >= 0;) {
        int iatom = face[j];
        if (iatom < 0 || iatom >= atomCount)
          continue;
        int iatom2 = face[(j + 1) %n];
        if (iatom2 < 0 || iatom2 >= atomCount)
          iatom2 = face[(j + 2) %n];
        if (iatom2 < 0 || iatom2 >= atomCount)
          continue;
        if (atoms[iatom].getBondTo(atoms[iatom2]) == null) {
          SmilesBond b = new SmilesBond(atoms[iatom], atoms[iatom2], SmilesBond.TYPE_SINGLE,  false);
          b.index = nBonds++;
        }
      }
    }
    for (int i = 0; i < atomCount; i++) {
      int n = atoms[i].bondCount; 
      if (n == 0 || n != atoms[i].bonds.length)
        atoms[i].bonds = (SmilesBond[]) AU.arrayCopyObject(atoms[i].bonds, n);
    }
    return getSmiles(atoms, atomCount, BSUtil.newBitSet2(0,  atomCount), null, 
        JC.SMILES_NOAROMATIC | (points == null ? JC.SMILES_TOPOLOGY : JC.SMILES_TYPE_SMILES));   
  }


  /**
   * Rather than returning bitsets, this method returns the sets of matching
   * atoms in array form so that a direct atom-atom correlation can be made.
   * 
   * @param pattern
   *        SMILES or SMARTS pattern.
   * @param atoms
   * @param bsSelected
   * @return a set of atom correlations
   * 
   */
  @Override
  public int[][] getCorrelationMaps(String pattern, Node[] atoms, int atomCount,
                                    BS bsSelected, int flags) throws Exception {
    //boolean isSmarts, boolean matchAllAtoms,  boolean firstMatchOnly
    //  isSmarts,       false, firstMatchOnly, MODE_MAP);

    return (int[][]) matchPriv(pattern, atoms, atomCount, bsSelected, null, 
        flags | JC.SMILES_MATCH_ONE | SMILES_MODE_MAP);
  }

  /////////////// private methods ////////////////

  // boolean isSmarts,  boolean matchAllAtoms, boolean firstMatchOnly
  private Object findPriv(String pattern, SmilesSearch search, int flags)
      throws Exception {
    // create a topological model set from smiles
    // do not worry about stereochemistry -- this
    // will be handled by SmilesSearch.setSmilesCoordinates
    BS bsAromatic = new BS();
    search.createTopoMap(bsAromatic);
    return matchPriv(pattern, search.jmolAtoms, -search.jmolAtoms.length,
        null, bsAromatic, flags);
  }

  @SuppressWarnings({ "unchecked" })
  //boolean isSmarts, boolean matchAllAtoms,  boolean firstMatchOnly, int mode
  private Object matchPriv(String pattern, Node[] atoms, int ac, BS bsSelected,
                       BS bsAromatic, int flags) throws Exception {
    InvalidSmilesException.clear();
    try {
      SmilesSearch search = SmilesParser.getMolecule(pattern, checkFlag(flags, JC.SMILES_TYPE_SMARTS));
      search.jmolAtoms = atoms;
      if (atoms instanceof BNode[])
        search.bioAtoms = (BNode[]) atoms;
      search.jmolAtomCount = Math.abs(ac);
      if (ac < 0)
        search.isSmilesFind = true;
      search.setSelected(bsSelected);
      search.getSelections();
      search.bsRequired = null;//(bsRequired != null && bsRequired.cardinality() > 0 ? bsRequired : null);
      search.setRingData(bsAromatic);
      search.firstMatchOnly = checkFlag(flags, JC.SMILES_RETURN_FIRST);
      search.matchAllAtoms = checkFlag(flags, JC.SMILES_MATCH_ALL);
      switch (flags & SMILES_MODE_MASK) {
      case SMILES_MODE_BITSET:
        search.asVector = false;
        return search.search(false);
      case SMILES_MODE_ARRAY:
        search.asVector = true;
        Lst<BS> vb = (Lst<BS>) search.search(false);
        return vb.toArray(new BS[vb.size()]);
      case SMILES_MODE_MAP:
        search.getMaps = true;
        Lst<int[]> vl = (Lst<int[]>) search.search(false);
        return vl.toArray(AU.newInt2(vl.size()));
      }
    } catch (Exception e) {
      e.printStackTrace();
      if (InvalidSmilesException.getLastError() == null)
        InvalidSmilesException.clear();
      throw new InvalidSmilesException(InvalidSmilesException.getLastError());
    }
    return null;
  }

  private static int countStereo(String s) {
    s = PT.rep(s, "@@", "@");
    int i = s.lastIndexOf('@') + 1;
    int n = 0;
    for (; --i >= 0;)
      if (s.charAt(i) == '@')
        n++;
    return n;
  }

  @Override
  public String cleanSmiles(String smiles) {
    return SmilesParser.cleanPattern(smiles);
  }

}