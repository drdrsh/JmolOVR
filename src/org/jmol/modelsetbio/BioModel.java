/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2011-08-05 21:10:46 -0500 (Fri, 05 Aug 2011) $
 * $Revision: 15943 $
 *
 * Copyright (C) 2002-2005  The Jmol Development Team
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
package org.jmol.modelsetbio;

import javajs.util.AU;
import javajs.util.OC;
import javajs.util.Lst;
import javajs.util.PT;
import javajs.util.SB;

import java.util.Hashtable;

import java.util.Map;
import java.util.Properties;


import org.jmol.api.DSSPInterface;
import org.jmol.api.Interface;
import org.jmol.api.JmolAnnotationParser;
import org.jmol.c.STR;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.Bond;
import org.jmol.modelset.Group;
import org.jmol.modelset.HBond;
import org.jmol.modelset.JmolBioModel;
import org.jmol.modelset.JmolBioModelSet;
import org.jmol.modelset.LabelToken;
import org.jmol.modelset.Model;
import org.jmol.modelset.ModelSet;
import org.jmol.modelset.Structure;
import org.jmol.script.SV;
import org.jmol.script.T;
import org.jmol.util.BSUtil;
import org.jmol.util.Escape;
import org.jmol.util.Edge;
import org.jmol.util.Logger;

import javajs.util.P3;


import org.jmol.viewer.JC;
import org.jmol.viewer.Viewer;


public final class BioModel extends Model implements JmolBioModelSet, JmolBioModel {

  /*
   *   
   * Note that "monomer" extends group. A group only becomes a 
   * monomer if it can be identified as one of the following 
   * PDB/mmCIF types:
   * 
   *   amino  -- has an N, a C, and a CA
   *   alpha  -- has just a CA
   *   nucleic -- has C1',C2',C3',C4',C5',O3', and O5'
   *   phosphorus -- has P
   *   
   * The term "conformation" is a bit loose. It means "what you get
   * when you go with one or another set of alternative locations.
   *
   *  
   */
  
  int bioPolymerCount = 0;
  public BioPolymer[] bioPolymers;
  boolean isMutated;

  private String defaultStructure;
  private Viewer vwr;


  //// effectively static methods, but called nonstatically because BioModel is hidden to JavaScript

  @Override
  public Map<String, String> getAllHeteroList(int modelIndex) {
    Map<String, String> htFull = new Hashtable<String, String>();
    boolean ok = false;
    for (int i = ms.mc; --i >= 0;)
      if (modelIndex < 0 || i == modelIndex) {
        @SuppressWarnings("unchecked")
        Map<String, String> ht = (Map<String, String>) ms.getInfo(i, "hetNames");
        if (ht == null)
          continue;
        ok = true;
        for (Map.Entry<String, String> entry : ht.entrySet()) {
          String key = entry.getKey();
          htFull.put(key, entry.getValue());
        }
      }
    return (ok ? htFull : null);
  }

  @Override
  public void setAllProteinType(BS bs, STR type) {
    int monomerIndexCurrent = -1;
    int iLast = -1;
    BS bsModels = ms.getModelBS(bs, false);
    setAllDefaultStructure(bsModels);
    Atom[] at = ms.at;
    Model[] am = ms.am;
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      if (iLast != i - 1)
        monomerIndexCurrent = -1;
      monomerIndexCurrent = at[i].group.setProteinStructureType(type,
          monomerIndexCurrent);
      int modelIndex = at[i].mi;
      ms.proteinStructureTainted = am[modelIndex].structureTainted = true;
      iLast = i = at[i].group.lastAtomIndex;
    }
    int[] lastStrucNo = new int[ms.mc];
    for (int i = 0; i < ms.ac;) {
      int modelIndex = at[i].mi;
      if (!bsModels.get(modelIndex)) {
        i = am[modelIndex].firstAtomIndex + am[modelIndex].act;
        continue;
      }
      iLast = at[i].group.getStrucNo();
      if (iLast < 1000 && iLast > lastStrucNo[modelIndex])
        lastStrucNo[modelIndex] = iLast;
      i = at[i].group.lastAtomIndex + 1;
    }
    for (int i = 0; i < ms.ac;) {
      int modelIndex = at[i].mi;
      if (!bsModels.get(modelIndex)) {
        i = am[modelIndex].firstAtomIndex + am[modelIndex].act;
        continue;
      }
      if (at[i].group.getStrucNo() > 1000)
        at[i].group.setStrucNo(++lastStrucNo[modelIndex]);
      i = at[i].group.lastAtomIndex + 1;
    }
  }


  /**
   * general purpose; return models associated with specific atoms
   * @param bsAtoms
   * @param bsAtomsRet all atoms associated with these models. 
   * @return bitset of base models
   */
  private BS modelsOf(BS bsAtoms, BS bsAtomsRet) {
    BS bsModels = BS.newN(ms.mc);
    boolean isAll = (bsAtoms == null);
    int i0 = (isAll ? ms.ac - 1 : bsAtoms.nextSetBit(0));
    for (int i = i0; i >= 0; i = (isAll ? i - 1 : bsAtoms.nextSetBit(i + 1))) {
      int modelIndex = ms.am[ms.at[i].mi].trajectoryBaseIndex;
      if (ms.isJmolDataFrameForModel(modelIndex))
        continue;
      bsModels.set(modelIndex);
      bsAtomsRet.set(i);
    }
    return bsModels;
  }

  @Override
  public String getAllDefaultStructures(BS bsAtoms, BS bsModified) {
    BS bsModels = modelsOf(bsAtoms, bsModified);
    SB ret = new SB();
    for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels.nextSetBit(i + 1)) 
      if (ms.am[i].isBioModel && ((BioModel) ms.am[i]).defaultStructure != null)
        ret.append(((BioModel) ms.am[i]).defaultStructure);
    return ret.toString();
  }

  @Override
  public String calculateAllStuctures(BS bsAtoms, boolean asDSSP,
                                        boolean doReport,
                                        boolean dsspIgnoreHydrogen,
                                        boolean setStructure) {
    BS bsAllAtoms = new BS();
    BS bsModelsExcluded = BSUtil.copyInvert(modelsOf(bsAtoms, bsAllAtoms),
        ms.mc);
    if (!setStructure)
      return ms.calculateStructuresAllExcept(bsModelsExcluded, asDSSP, doReport,
          dsspIgnoreHydrogen, false, false);
    ms.recalculatePolymers(bsModelsExcluded);
    String ret = ms.calculateStructuresAllExcept(bsModelsExcluded, asDSSP, doReport,
        dsspIgnoreHydrogen, true, false);
    vwr.shm.resetBioshapes(bsAllAtoms);
    ms.setStructureIndexes();
    return ret;
  }

  @Override
  public String calculateAllStructuresExcept(BS alreadyDefined, boolean asDSSP,
                                       boolean doReport,
                                       boolean dsspIgnoreHydrogen,
                                       boolean setStructure,
                                       boolean includeAlpha) {
    String ret = "";
    BS bsModels = BSUtil.copyInvert(alreadyDefined, ms.mc);
    //working here -- testing reset
    //TODO bsModels first for not setStructure, after that for setstructure....
    if (setStructure)
      setAllDefaultStructure(bsModels);
    for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels.nextSetBit(i + 1))
      if (ms.am[i].isBioModel)
        ret += ((BioModel) ms.am[i]).calculateStructures(asDSSP, doReport,
            dsspIgnoreHydrogen, setStructure, includeAlpha);
    if (setStructure)
      ms.setStructureIndexes();
    return ret;
  }

  public void setAllDefaultStructure(BS bsModels) {
    for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels.nextSetBit(i + 1))
      if (ms.am[i].isBioModel) {
        BioModel m = (BioModel) ms.am[i];
        if (m.defaultStructure == null)
          m.defaultStructure = getFullProteinStructureState(m.bsAtoms, T.state);
      }
  }

  @Override
  public void setAllStructureList(Map<STR, float[]> structureList) {
    for (int iModel = ms.mc; --iModel >= 0;)
      if (ms.am[iModel].isBioModel) {
        BioModel m = (BioModel) ms.am[iModel];
        m.bioPolymers = (BioPolymer[]) AU.arrayCopyObject(m.bioPolymers,
            m.bioPolymerCount);
        for (int i = m.bioPolymerCount; --i >= 0;) {
          BioPolymer bp = 
          m.bioPolymers[i];
          if (bp instanceof AminoPolymer)
            ((AminoPolymer) bp).setStructureList(structureList);
        }
      }
  }

  @Override
  public void setAllConformation(BS bsAtoms) {
    BS bsModels = ms.getModelBS(bsAtoms, false);
    for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels.nextSetBit(i + 1))
      if (ms.am[i].isBioModel) {
        BioModel m = (BioModel) ms.am[i];
        if (m.altLocCount > 0)
          for (int j = m.bioPolymerCount; --j >= 0;)
            m.bioPolymers[j].setConformation(bsAtoms);
      }
  }


  @Override
  public void getAllPolymerPointsAndVectors(BS bs, Lst<P3[]> vList,
                                         boolean isTraceAlpha,
                                         float sheetSmoothing) {
    for (int i = 0; i < ms.mc; ++i)
      if (ms.am[i].isBioModel) {
        BioModel m = (BioModel) ms.am[i];
        int last = Integer.MAX_VALUE - 1;
        for (int ip = 0; ip < m.bioPolymerCount; ip++)
          last = m.bioPolymers[ip].getPolymerPointsAndVectors(last, bs, vList,
              isTraceAlpha, sheetSmoothing);
      }
  }

  @Override
  public void calcSelectedMonomersCount() {
    BS bsSelected = vwr.bsA();
    for (int i = ms.mc; --i >= 0;)
      if (ms.am[i].isBioModel) {
        BioModel m = (BioModel) ms.am[i];
        for (int j = m.bioPolymerCount; --j >= 0;)
          m.bioPolymers[j].calcSelectedMonomersCount(bsSelected);
      }
  }

  /**
   * @param modelIndex
   * @return number of polymers
   */
  @Override
  public int getBioPolymerCountInModel(int modelIndex) {
    if (modelIndex < 0) {
      int polymerCount = 0;
      for (int i = ms.mc; --i >= 0;)
        if (!ms.isTrajectorySubFrame(i) && ms.am[i].isBioModel)
          polymerCount += ((BioModel) ms.am[i]).getBioPolymerCount();
      return polymerCount;
    }
    return (ms.isTrajectorySubFrame(modelIndex) || !ms.am[modelIndex].isBioModel ? 0
        : ((BioModel) ms.am[modelIndex]).getBioPolymerCount());
  }

  @Override
  public void calculateAllPolymers(Group[] groups, int groupCount,
                                   int baseGroupIndex, BS modelsExcluded) {
    boolean checkConnections = !vwr.getBoolean(T.pdbsequential);
    if (groupCount < 0)
      groupCount = groups.length;
    
    if (modelsExcluded != null)
      for (int j = 0; j < groupCount; ++j) {
        Group group = groups[j];
        if (group instanceof Monomer) {
          if (((Monomer) group).bioPolymer != null
              && (!modelsExcluded.get(group.chain.model.modelIndex)))
            ((Monomer) group).setBioPolymer(null, -1);
        }
      }
    for (int i = 0, mc = ms.mc; i < mc; i++)
      if ((modelsExcluded == null || !modelsExcluded.get(i))
          && ms.am[i].isBioModel) {
        for (int j = baseGroupIndex; j < groupCount; ++j) {
          Group g = groups[j];
          Model model = g.getModel();
          if (!model.isBioModel || !(g instanceof Monomer))
            continue;
          boolean doCheck = checkConnections
              && !ms.isJmolDataFrameForModel(ms.at[g.firstAtomIndex].mi);
          BioPolymer bp = (((Monomer) g).bioPolymer == null ? Resolver
              .allocateBioPolymer(groups, j, doCheck) : null);
          if (bp == null || bp.monomerCount == 0)
            continue;
          ((BioModel) model).addBioPolymer(bp);
          j += bp.monomerCount - 1;
        }
      }
  }
  
  @Override
  public void recalculateAllPolymers(BS bsModelsExcluded, Group[] groups) {
    for (int i = 0; i < ms.mc; i++)
      if (ms.am[i].isBioModel && !bsModelsExcluded.get(i))
        ((BioModel) ms.am[i]).clearBioPolymers();
    calculateAllPolymers(groups, -1, 0, bsModelsExcluded);
  }

  @Override
  public BS getGroupsWithinAll(int nResidues, BS bs) {
    BS bsResult = new BS();
    BS bsCheck = ms.getIterativeModels(false);
    for (int iModel = ms.mc; --iModel >= 0;)
      if (bsCheck.get(iModel) && ms.am[iModel].isBioModel) {
        BioModel m = (BioModel) ms.am[iModel];
        for (int i = m.bioPolymerCount; --i >= 0;)
          m.bioPolymers[i].getRangeGroups(nResidues, bs, bsResult);
      }
    return bsResult;
  }

  @Override
  public int calculateStruts(BS bs1, BS bs2) {
    return getBioExt().calculateAllstruts(vwr, ms, bs1, bs2);
  }

  @Override
  public void recalculatePoints(int modelIndex) {
    if (modelIndex < 0) {
      for (int i = ms.mc; --i >= 0;)
        if (!ms.isTrajectorySubFrame(i) && ms.am[i].isBioModel)
          ((BioModel) ms.am[i]).recalculateLeadMidpointsAndWingVectors();
      return;
    }
    if (!ms.isTrajectorySubFrame(modelIndex) && ms.am[modelIndex].isBioModel)
      ((BioModel) ms.am[modelIndex]).recalculateLeadMidpointsAndWingVectors();
  }

  @Override
  public String getFullProteinStructureState(BS bsAtoms, int mode) {
    boolean taintedOnly = (mode == T.all);
    if (taintedOnly && !ms.proteinStructureTainted)
      return "";
    boolean scriptMode = (mode == T.state || mode == T.all);
    Atom[] atoms = ms.at;
    int at0 = (bsAtoms == null ? 0 : bsAtoms.nextSetBit(0));
    if (at0 < 0)
      return "";
    if (bsAtoms != null && mode == T.ramachandran) {
      bsAtoms = BSUtil.copy(bsAtoms);
      for (int i = ms.ac; --i >= 0;)
        if (Float.isNaN(atoms[i].group.getGroupParameter(T.phi))
            || Float.isNaN(atoms[i].group.getGroupParameter(T.psi)))
          bsAtoms.clear(i);
    }
    int at1 = (bsAtoms == null ? ms.ac : bsAtoms.length()) - 1;
    int im0 = atoms[at0].mi;
    int im1 = atoms[at1].mi;
    Lst<ProteinStructure> lstStr = new Lst<ProteinStructure>();
    Map<ProteinStructure, Boolean> map = new Hashtable<ProteinStructure, Boolean>();
    SB cmd = new SB();
    for (int im = im0; im <= im1; im++) {
      if (!ms.am[im].isBioModel)
        continue;
      BioModel m = (BioModel) ms.am[im];
      if (taintedOnly && !m.structureTainted)
        continue;
      BS bsA = new BS();
      bsA.or(m.bsAtoms);
      bsA.andNot(m.bsAtomsDeleted);
      int i0 = bsA.nextSetBit(0);
      if (i0 < 0)
        continue;
      if (scriptMode) {
        cmd.append("  structure none ")
            .append(
                Escape.eBS(ms.getModelAtomBitSetIncludingDeleted(im, false)))
            .append("    \t# model=" + ms.getModelNumberDotted(im))
            .append(";\n");
      }
      ProteinStructure ps;
      for (int i = i0; i >= 0; i = bsA.nextSetBit(i + 1)) {
        Atom a = atoms[i];
        if (!(a.group instanceof AlphaMonomer)
            || (ps = ((AlphaMonomer) a.group).proteinStructure) == null
            || map.containsKey(ps))
          continue;
        lstStr.addLast(ps);
        map.put(ps, Boolean.TRUE);
      }
    }
    getStructureLines(bsAtoms, cmd, lstStr, STR.HELIX, scriptMode, mode);
    getStructureLines(bsAtoms, cmd, lstStr, STR.SHEET, scriptMode, mode);
    getStructureLines(bsAtoms, cmd, lstStr, STR.TURN, scriptMode, mode);
    return cmd.toString();
  }

  @SuppressWarnings("incomplete-switch")
  private int getStructureLines(BS bsAtoms, SB cmd, Lst<ProteinStructure> lstStr, STR type,
                                boolean scriptMode, int mode) {
    //boolean pdbFileMode = (mode == T.pdb || mode == T.ramachandran);
    boolean showMode = (mode == T.show);
    int nHelix = 0, nSheet = 0, nTurn = 0;
    String sid = null;
    BS bs = new BS();
    int n = 0;
    for (int i = 0, ns = lstStr.size(); i < ns; i++) {
      ProteinStructure ps = lstStr.get(i);
      if (ps.type != type)
        continue;
      // could be a subset of atoms, not just the ends
      Monomer m1 = ps.findMonomer(bsAtoms, true);
      Monomer m2 = ps.findMonomer(bsAtoms, false);
      if (m1 == null || m2 == null)
        continue;
      int iModel = ps.apolymer.model.modelIndex;
      String comment = (scriptMode ? "    \t# model="
          + ms.getModelNumberDotted(iModel) : null);
      int res1 = m1.getResno();
      int res2 = m2.getResno();
      STR subtype = ps.subtype;
      switch (type) {
      case HELIX:
      case TURN:
      case SHEET:
        n++;
        if (scriptMode) {
          bs.clearAll();
          ps.setAtomBits(bs);
          String stype = subtype.getBioStructureTypeName(false);
          cmd.append("  structure ").append(stype).append(" ")
              .append(Escape.eBS(bs)).append(comment)
              .append(" & (" + res1 + " - " + res2 + ")").append(";\n");
        } else {
          String str;
          int nx;
          // NNN III GGG C RRRR GGG C RRRR
          // HELIX 99 99 LYS F 281 LEU F 293 1
          // NNN III 2 GGG CRRRR GGG CRRRR
          // SHEET 1 A 8 ILE A 43 ASP A 45 0
          // NNN III GGG CRRRR GGG CRRRR
          // TURN 1 T1 PRO A 41 TYR A 44
          switch (type) {
          case HELIX:
            nx = ++nHelix;
            sid = PT.formatStringI("%3N %3N", "N", nx);
            str = "HELIX  %ID %3GROUPA %1CA %4RESA  %3GROUPB %1CB %4RESB";
            String stype = null;
            switch (subtype) {
            case HELIX:
            case HELIXALPHA:
              stype = "  1";
              break;
            case HELIX310:
              stype = "  5";
              break;
            case HELIXPI:
              stype = "  3";
              break;
            }
            if (stype != null)
              str += stype;
            break;
          case SHEET:
            nx = ++nSheet;
            sid = PT.formatStringI("%3N %3A 0", "N", nx);
            sid = PT.formatStringS(sid, "A", "S" + nx);
            str = "SHEET  %ID %3GROUPA %1CA%4RESA  %3GROUPB %1CB%4RESB";
            break;
          case TURN:
          default:
            nx = ++nTurn;
            sid = PT.formatStringI("%3N %3N", "N", nx);
            str = "TURN   %ID %3GROUPA %1CA%4RESA  %3GROUPB %1CB%4RESB";
            break;
          }
          str = PT.formatStringS(str, "ID", sid);
          str = PT.formatStringS(str, "GROUPA", m1.getGroup3());
          str = PT.formatStringS(str, "CA", m1.getLeadAtom().getChainIDStr());
          str = PT.formatStringI(str, "RESA", res1);
          str = PT.formatStringS(str, "GROUPB", m2.getGroup3());
          str = PT.formatStringS(str, "CB", m2.getLeadAtom().getChainIDStr());
          str = PT.formatStringI(str, "RESB", res2);
          cmd.append(str);
          if (showMode)
            cmd.append(" strucno= ").appendI(ps.strucNo);
          cmd.append("\n");

          /*
           * HELIX 1 1 ILE 7 PRO 19 1 3/10 CONFORMATION RES 17,19 1CRN 55
           * HELIX 2 2 GLU 23 THR 30 1 DISTORTED 3/10 AT RES 30 1CRN 56
           * SHEET 1 S1 2 THR 1 CYS 4 0 1CRNA 4 SHEET 2 S1 2 CYS 32 ILE 35
           */
        }
      }
    }
    if (n > 0)
      cmd.append("\n");
    return n;
  }

  @Override
  public BS getAllSequenceBits(String specInfo, BS bsAtoms, BS bsResult) {
    if (specInfo.length() > 0) {
      if (bsAtoms == null)
        bsAtoms = vwr.getAllAtoms();
      if (specInfo.indexOf('|') < specInfo.lastIndexOf('|'))
        return getAllUnitIds(specInfo, bsAtoms, bsResult);
      Model[] am = ms.am;
      for (int i = ms.mc; --i >= 0;)
        if (am[i].isBioModel) {
          BioModel m = (BioModel) am[i];
          int lenInfo = specInfo.length();
          for (int ip = 0; ip < m.bioPolymerCount; ip++) {
            String sequence = m.bioPolymers[ip].getSequence();
            int j = -1;
            while ((j = sequence.indexOf(specInfo, ++j)) >= 0)
              m.bioPolymers[ip].getPolymerSequenceAtoms(j, lenInfo, bsAtoms,
                  bsResult);
          }
        }
    }
    return bsResult;
  }
  
  //UnitIDs are based on http://rna.bgsu.edu/main/rna-3d-hub-help/unit-ids/
  //  
  //  Unit Identifier Specification
  //
  //  We describe the type and case sensitivity of each field in the list below. In addition, we list which item in the mmCIF the data for each field comes from. We also show several examples of the IDs and their interpretation at the end.
  //
  //  Unit ids can also be used to identify atoms. When identifying entire residues, the atom field is left blank.
  //
  //      PDB ID Code
  //          From PDBx/mmCIF item: _entry.id
  //          4 characters, case-insensitive
  //      Model Number
  //          From PDBx/mmCIF item: _atom_site.pdbx_PDB_model_num
  //          integer, range 1-99
  //      Chain ID
  //          From PDBx/mmCIF item: _atom_site.auth_asym_id
  //          <= 4 character, case-sensitive
  //      Residue/Nucleotide/Component Identifier
  //          From PDBx/mmCIF item: _atom_site.label_comp_id
  //          1-3 characters, case-insensitive
  //      Residue/Nucleotide/Component Number
  //          From PDBx/mmCIF item: _atom_site.auth_seq_id
  //          integer, range: -999..9999 (there are negative residue numbers)
  //      Atom Name (Optional, default: blank)
  //          From PDBx/mmCIF item: _atom_site.label_atom_id
  //          0-4 characters, case-insensitive
  //          blank means all atoms
  //      Alternate ID (Optional, default: blank)
  //          From PDBx/mmCIF item: _atom_site.label_alt_id
  //          Default value: blank
  //          One of ['A', 'B', '0'], case-insensitive
  //      Insertion Code (Optional, default: blank)
  //          From PDBx/mmCIF item: _atom_site.pdbx_PDB_ins_code
  //          1 character, case-insensitive
  //      Symmetry Operation (Optional, default: 1_555)
  //          As defined in PDBx/mmCIF item: _pdbx_struct_oper_list.name
  //          5-6 characters, case-insensitive
  //          For viral icosahedral structures, use “P_” + model number instead of symmetry operators. For example, 1A34|1|A|VAL|88|||P_1
  //
  //  Examples
  //
  //      Chain A in 1ABC = “1ABC|1|A”
  //      Nucleotide U(10) chain B of 1ABC = “1ABC|1|B|U|10”
  //      Nucleotide U(15A) chain B, default symmetry operator = “1ABC|1|B|U|15|||A”
  //      Nucleotide C(25) chain D subject to symmetry operation 2_655 = “1ABC|1|D|C|25||||2_655”
  //
  //  Unit ids for entire residues can contain 4, 7, or 8 string separators (|).

  private Map<String, BS>[] unitIdSets;
  
  @SuppressWarnings("unchecked")
  private BS getAllUnitIds(String specInfo, BS bsSelected, BS bsResult) {
    //  1ehz|1|A|U|7||||,1ehz|1|A|5MC|49|||| etc.
    // (pdbid)|model|chain|RESNAME|resno|ATOMNAME|altcode|inscode|(symmetry)
    //   0       1     2      3      4      5        6       7       8
    //   -------required--------------   ----------optional-----------
    // unitIdSets contains hashtables for each of these.
    Map<String, BS>[] maps = unitIdSets;
    if (maps == null) {
      maps = unitIdSets = new Map[7];
      for (int i = 0; i < 7; i++)
        maps[i] = new Hashtable<String, BS>();
      // set all model entries
      for (int i = ms.mc; --i >= 0;) {
        Model m = ms.am[i];
        if (!m.isBioModel)
          continue;
        if (ms.isTrajectory(i))
          m = ms.am[i = m.trajectoryBaseIndex];
        String num = "|" + ms.getInfo(i, "modelNumber");
        checkMap(maps[0], ms.getInfo(i, "modelName") + num, m.bsAtoms);
        checkMap(maps[0], num, m.bsAtoms);
      }
    }
    BS bsModelChain = null;
    String lastModelChain = null;
    BS bsTemp = new BS();
    String[] units = PT.getTokens(PT.replaceAllCharacters(specInfo,
        ", \t\n[]\"", " "));
    int[] ptrs = new int[8];
    for (int i = units.length; --i >= 0;) {
      String unit = units[i] + "|";
      if (unit.length() < 5)
        continue;
      int bsPtr = 0;
      for (int j = 0, n = 0, pt = unit.lastIndexOf('|') + 1; j < pt && n < 8; j++) {
        if (unit.charAt(j) == '|')
          ptrs[n++] = j;
        else
          bsPtr |= 1 << n;
      }
      // |1|A||45 minimally: 10110
      if ((bsPtr & 0x16) != 0x16)
        continue;
      bsTemp.clearAll();
      bsTemp.or(bsSelected);
      String mchain = unit.substring(0, ptrs[2]);
      if (lastModelChain != null && lastModelChain.equals(mchain)) {
        bsTemp.or(bsModelChain);
      } else {
        if (!addUnit(T.model, unit.substring(0, ptrs[1]).toUpperCase(), bsTemp,
            maps[0]) 
         || !addUnit(T.spec_chain, unit.substring(ptrs[1] + 1, ptrs[2]),
            bsTemp, maps[1]))
              continue;
        // faster to cache this and reuse it
        bsModelChain = BSUtil.copy(bsTemp);
        lastModelChain = mchain;
      }
      boolean haveAtom = ((bsPtr & (1 << 5)) != 0);
      boolean haveAlt =  ((bsPtr & (1 << 6)) != 0);
      // we do not check field 3 (resname); it is redundant
      // the altcode is considerd explicit if atomname is defined
      // it is considered "This option or unspecified" when the atom name is not specified.
      
      if (
          !addUnit(T.resno, unit.substring(ptrs[3] + 1, ptrs[4]), bsTemp, maps[2]) 
       || !addUnit(T.inscode, ((bsPtr & (1 << 7)) == 0 ? "\0" : unit.substring(ptrs[6] + 1, ptrs[7])), bsTemp, maps[3])
       || (haveAtom 
           ? !addUnit(T.atomname, unit.substring(ptrs[4] + 1, ptrs[5]).toUpperCase(), bsTemp, maps[4])
          || !addUnit(T.spec_alternate, unit.substring(ptrs[5] + 1, ptrs[6]), bsTemp, maps[5])
         : haveAlt && !addUnit(T.configuration, unit.substring(ptrs[5] + 1, ptrs[6]), bsTemp, maps[6])
          )
       )
            continue;
      bsResult.or(bsTemp);
    }
    return bsResult;
  }

  /**
   * Ensure that if two models have the same name or number, we 
   * appropriately OR their bitsets.
   *  
   * @param map
   * @param key
   * @param bsAtoms
   * @return current BS
   */
  private BS checkMap(Map<String, BS> map, String key, BS bsAtoms) {
    BS bs = BSUtil.copy(bsAtoms);
    BS bs0 = map.get(key);
    if (bs0 == null)
      map.put(key, bs0 = bs);
    else
      bs0.or(bs);
    return bs0;
  }

  /**
   * Repetitively AND unit components to get the final set of atoms 
   * 
   * @param tok
   * @param key
   * @param bsTemp
   * @param map
   * @return true if there are still atoms to consider
   */
  private boolean addUnit(int tok, String key, BS bsTemp, Map<String, BS> map) {
    BS bs = map.get(key);
    if (bs == null) {
      Object o;
      switch (tok) {
//      case T.model:
      default:
        return false;
      case T.spec_chain:
        o = Integer.valueOf(vwr.getChainID(key, false));
        break;
      case T.resno:
        o = Integer.valueOf(PT.parseInt(key));
        break;
      case T.inscode:
        o = Integer.valueOf(key.charAt(0));
        break;
      case T.configuration:
        // select all atoms with either no specified alt_id
        // or the specified id.
        // add in the atoms with no indication
        bs = ms.getAtomBitsMDa(tok = T.spec_alternate, null, new BS());
        // and then also those with the specified alt_id
        //$FALL-THROUGH$
      case T.atomname:
        o = key;
        break;
      case T.spec_alternate:
        o = (key.length() == 0 ? null : key);
        break;
      }
      map.put(key, bs = ms.getAtomBitsMDa(tok, o, (bs == null ? new BS() : bs)));
    }
    bsTemp.and(bs);
    return (bsTemp.nextSetBit(0) >= 0);
  }

  private BS getAllBasePairBits(String specInfo) {
    BS bsA = null;
    BS bsB = null;
    Lst<Bond> vHBonds = new Lst<Bond>();
    if (specInfo.length() == 0) {
      bsA = bsB = vwr.getAllAtoms();
      calcAllRasmolHydrogenBonds(bsA, bsB, vHBonds, true, 1, false, null);
    } else {
      for (int i = 0; i < specInfo.length();) {
        bsA = ms.getSequenceBits(specInfo.substring(i, ++i), null, new BS());
        if (bsA.nextSetBit(0) < 0)
          continue;
        bsB = ms.getSequenceBits(specInfo.substring(i, ++i), null, new BS());
        if (bsB.nextSetBit(0) < 0)
          continue;
        calcAllRasmolHydrogenBonds(bsA, bsB, vHBonds, true, 1, false, null);
      }
    }
    BS bsAtoms = new BS();
    for (int i = vHBonds.size(); --i >= 0;) {
      Bond b = vHBonds.get(i);
      bsAtoms.set(b.atom1.i);
      bsAtoms.set(b.atom2.i);
    }
    return bsAtoms;
  }

  /**
   *  only for base models, not trajectories
   * @param bsA 
   * @param bsB 
   * @param vHBonds will be null for autobonding
   * @param nucleicOnly 
   * @param nMax 
   * @param dsspIgnoreHydrogens 
   * @param bsHBonds 
   */
  @Override
  public void calcAllRasmolHydrogenBonds(BS bsA, BS bsB, Lst<Bond> vHBonds,
                                      boolean nucleicOnly, int nMax,
                                      boolean dsspIgnoreHydrogens, BS bsHBonds) {
    Model[] am = ms.am;
    if (vHBonds == null) {
      // autobond -- clear all hydrogen bonds
      BS bsAtoms = bsA;
      if (bsB != null && !bsA.equals(bsB))
        (bsAtoms = BSUtil.copy(bsA)).or(bsB);
      BS bsDelete = new BS();
      BS bsOK = new BS();
      Model[] models = ms.am;
      Bond[] bonds = ms.bo;
      for (int i = ms.bondCount; --i >= 0;) {
        Bond bond = bonds[i];
        if ((bond.order & Edge.BOND_H_CALC_MASK) == 0)
          continue;
        // trajectory atom .mi will be pointing to their trajectory;
        // here we check to see if their base model is this model
        if (bsAtoms.get(bond.atom1.i))
          bsDelete.set(i);
        else
          bsOK.set(models[bond.atom1.mi].trajectoryBaseIndex);
      }
      for (int i = ms.mc; --i >= 0;)
        if (models[i].isBioModel)
          ((BioModel) models[i]).hasRasmolHBonds = bsOK.get(i);
      if (bsDelete.nextSetBit(0) >= 0)
        ms.deleteBonds(bsDelete, false);
    }
    for (int i = ms.mc; --i >= 0;)
      if (am[i].isBioModel && !ms.isTrajectorySubFrame(i))
        ((BioModel) am[i]).getRasmolHydrogenBonds(bsA, bsB, vHBonds, nucleicOnly, nMax,
            dsspIgnoreHydrogens, bsHBonds);
  }

  private void getRasmolHydrogenBonds(BS bsA, BS bsB, Lst<Bond> vHBonds,
                                      boolean nucleicOnly, int nMax,
                                      boolean dsspIgnoreHydrogens, BS bsHBonds) {
    boolean doAdd = (vHBonds == null);
    if (doAdd)
      vHBonds = new Lst<Bond>();
    if (nMax < 0)
      nMax = Integer.MAX_VALUE;
    boolean asDSSX = (bsB == null);
    BioPolymer bp, bp1;
    if (asDSSX && bioPolymerCount > 0) {

      calculateDssx(vHBonds, false, dsspIgnoreHydrogens, false);

    } else {
      for (int i = bioPolymerCount; --i >= 0;) {
        bp = bioPolymers[i];
        if (bp.monomerCount == 0)
          continue;
        int type = bp.getType();
        boolean isRNA = false;
        switch  (type) {
        case BioPolymer.TYPE_AMINO:
          if (nucleicOnly)
            continue;
          bp.calcRasmolHydrogenBonds(null, bsA, bsB, vHBonds, nMax, null, true,
              false);
          break;
        case BioPolymer.TYPE_NUCLEIC:
          isRNA = bp.monomers[0].isRna();
          break;
        default:
          continue;
        }
        for (int j = bioPolymerCount; --j >= 0;) {
          if ((bp1 = bioPolymers[j]) != null && (isRNA || i != j)
              && type == bp1.getType()) {
            bp1.calcRasmolHydrogenBonds(bp, bsA, bsB, vHBonds, nMax, null,
                true, false);
          }
        }
      }
    }

    if (vHBonds.size() == 0 || !doAdd)
      return;
    hasRasmolHBonds = true;
    for (int i = 0; i < vHBonds.size(); i++) {
      HBond bond = (HBond) vHBonds.get(i);
      Atom atom1 = bond.atom1;
      Atom atom2 = bond.atom2;
      if (atom1.isBonded(atom2))
        continue;
      int index = ms.addHBond(atom1, atom2, bond.order, bond.getEnergy());
      if (bsHBonds != null)
        bsHBonds.set(index);
    }
  }

  @Override
  public void calculateStraightnessAll() {
    getBioExt().calculateStraightnessAll(vwr, ms);
  }

  @Override
  public boolean mutate(BS bs, String group, String[] sequence) {
    return getBioExt().mutate(vwr, bs, group, sequence);
  }

  /////////////////////////////////////////////////////////////////////////  

  BioModel(ModelSet modelSet, int modelIndex, int trajectoryBaseIndex, 
      String jmolData, Properties properties, Map<String, Object> auxiliaryInfo) {
    vwr = modelSet.vwr;
    set(modelSet, modelIndex, trajectoryBaseIndex, jmolData, properties, auxiliaryInfo);
    
    isBioModel = true;
    modelSet.bioModelset = this;
    clearBioPolymers();
    modelSet.am[modelIndex] = this;
    pdbID = (String) auxiliaryInfo.get("name");
  }

  private void clearBioPolymers() {
    bioPolymers = new BioPolymer[8];
    bioPolymerCount = 0;
  }

  @Override
  public int getBioPolymerCount() {
    return bioPolymerCount;
  }

  @Override
  public void fixIndices(int modelIndex, int nAtomsDeleted, BS bsDeleted) {
    fixIndicesM(modelIndex, nAtomsDeleted, bsDeleted);
    recalculateLeadMidpointsAndWingVectors();
    unitIdSets = null;
  }

  private void recalculateLeadMidpointsAndWingVectors() {
    for (int ip = 0; ip < bioPolymerCount; ip++)
      bioPolymers[ip].recalculateLeadMidpointsAndWingVectors();
  }

  
  @Override
  public boolean freeze() {
    freezeM();
    bioPolymers = (BioPolymer[])AU.arrayCopyObject(bioPolymers, bioPolymerCount);
    return true;
  }
  
  public void addSecondaryStructure(STR type, String structureID,
                                    int serialID, int strandCount,
                                    int startChainID, int startSeqcode,
                                    int endChainID, int endSeqcode, int istart,
                                    int iend, BS bsAssigned) {
    for (int i = bioPolymerCount; --i >= 0;)
      if (bioPolymers[i] instanceof AlphaPolymer)
        ((AlphaPolymer) bioPolymers[i]).addStructure(type, structureID,
            serialID, strandCount, startChainID, startSeqcode, endChainID,
            endSeqcode, istart, iend, bsAssigned);
  }

  private String calculateStructures(boolean asDSSP, boolean doReport,
                                    boolean dsspIgnoreHydrogen,
                                    boolean setStructure, boolean includeAlpha) {
    if (bioPolymerCount == 0 || !setStructure && !asDSSP)
      return "";
    ms.proteinStructureTainted = structureTainted = true;
    if (setStructure)
      for (int i = bioPolymerCount; --i >= 0;)
        if (!asDSSP || bioPolymers[i].monomers[0].getNitrogenAtom() != null)
          bioPolymers[i].clearStructures();
    if (!asDSSP || includeAlpha)
      for (int i = bioPolymerCount; --i >= 0;)
        if (bioPolymers[i] instanceof AlphaPolymer)
          ((AlphaPolymer) bioPolymers[i]).calculateStructures(includeAlpha);
    return (asDSSP ? calculateDssx(null, doReport, dsspIgnoreHydrogen, setStructure) : "");
  }
  
  private String calculateDssx(Lst<Bond> vHBonds, boolean doReport,
                               boolean dsspIgnoreHydrogen, boolean setStructure) {
    boolean haveProt = false;
    boolean haveNucl = false;
    for (int i = 0; i < bioPolymerCount && !(haveProt && haveNucl); i++) {
      if (bioPolymers[i].isNucleic())
        haveNucl = true;
      else if (bioPolymers[i] instanceof AminoPolymer)
        haveProt = true;
    }
    String s = "";
    if (haveProt)
      s += ((DSSPInterface) Interface.getOption("dssx.DSSP", vwr, "ms"))
        .calculateDssp(bioPolymers, bioPolymerCount, vHBonds, doReport,
            dsspIgnoreHydrogen, setStructure);
    if (haveNucl && auxiliaryInfo.containsKey("dssr") && vHBonds != null)
      s += vwr.getAnnotationParser(true).getHBonds(ms, modelIndex, vHBonds, doReport);
    return s;
  }

  /**
   * @param conformationIndex0
   * @param doSet 
   * @param bsAtoms
   * @param bsRet
   * @return true;
   */
  public boolean getConformation(int conformationIndex0, boolean doSet, BS bsAtoms, BS bsRet) {
    if (conformationIndex0 >= 0) {
      int nAltLocs = altLocCount;
      if (nAltLocs > 0) {
        Atom[] atoms = ms.at;
        Group g = null;
        char ch = '\0';
        int conformationIndex = conformationIndex0;
        BS bsFound = new BS();
        for (int i = bsAtoms.nextSetBit(0); i >= 0; i = bsAtoms.nextSetBit(i + 1)) {
            Atom atom = atoms[i];
            char altloc = atom.altloc;
            // ignore (include) atoms that have no designation
            if (altloc == '\0')
              continue;
            if (atom.group != g) {
              g = atom.group;
              ch = '\0';
              conformationIndex = conformationIndex0;
              bsFound.clearAll();
            }
            // count down until we get the desired index into the list
            if (conformationIndex >= 0 && altloc != ch && !bsFound.get(altloc)) {
              ch = altloc;
              conformationIndex--;
              bsFound.set(altloc);
            }
            if (conformationIndex >= 0 || altloc != ch)
              bsAtoms.clear(i);
          }
      }
    }
    if (bsAtoms.nextSetBit(0) >= 0) {
      bsRet.or(bsAtoms);      
      if (doSet)
        for (int j = bioPolymerCount; --j >= 0;)
          bioPolymers[j].setConformation(bsAtoms);
    }
    return true;
  }

  private void addBioPolymer(BioPolymer polymer) {
    if (bioPolymers.length == 0)
      clearBioPolymers();
    if (bioPolymerCount == bioPolymers.length)
      bioPolymers = (BioPolymer[])AU.doubleLength(bioPolymers);
    polymer.bioPolymerIndexInModel = bioPolymerCount;
    bioPolymers[bioPolymerCount++] = polymer;
  }

  @Override
  public Lst<BS> getBioBranches(Lst<BS> biobranches) {
    // scan through biopolymers quickly -- 
    BS bsBranch;
    for (int j = 0; j < bioPolymerCount; j++) {
      bsBranch = new BS();
      bioPolymers[j].getRange(bsBranch, isMutated);
      int iAtom = bsBranch.nextSetBit(0);
      if (iAtom >= 0) {
        if (biobranches == null)
          biobranches = new  Lst<BS>();
        biobranches.addLast(bsBranch);
      }
    }
    return biobranches;
  }

  @Override
  public void getAllPolymerInfo(BS bs, Map<String, Lst<Map<String, Object>>> info) {
    getBioExt().getAllPolymerInfo(ms, bs, info);
  }

  private BioExt bx;
  private BioExt getBioExt() {
    return (bx == null ? (bx = ((BioExt) Interface.getInterface("org.jmol.modelsetbio.BioExt", vwr, "script"))) : bx);
  }

  private final static String[] pdbRecords = { "ATOM  ", "MODEL ", "HETATM" };

  @Override
  public String getFullPDBHeader() {
    if (modelIndex < 0)
      return "";
    String info = (String) auxiliaryInfo.get("fileHeader");
    if (info != null)
      return info;
    info = vwr.getCurrentFileAsString("biomodel");
    int ichMin = info.length();
    for (int i = pdbRecords.length; --i >= 0;) {
      int ichFound;
      String strRecord = pdbRecords[i];
      switch (ichFound = (info.startsWith(strRecord) ? 0 : info.indexOf("\n"
          + strRecord))) {
      case -1:
        continue;
      case 0:
        auxiliaryInfo.put("fileHeader", "");
        return "";
      default:
        if (ichFound < ichMin)
          ichMin = ++ichFound;
      }
    }
    info = info.substring(0, ichMin);
    auxiliaryInfo.put("fileHeader", info);
    return info;
  }

  @Override
  public void getPdbData(String type, char ctype, boolean isDraw,
                         BS bsSelected, OC out,
                         LabelToken[] tokens, SB pdbCONECT, BS bsWritten) {
    getBioExt().getPdbDataM(this, vwr, type, ctype, isDraw, bsSelected, out, tokens, pdbCONECT, bsWritten);
  }
  
  /**
   * from ModelSet.setAtomPositions
   * 
   * base models only; not trajectories
   */
  @Override
  public void resetRasmolBonds(BS bs) {
    BS bsDelete = new BS();
    hasRasmolHBonds = false;
    Model[] am = ms.am;
    Bond[] bo = ms.bo;
    for (int i = ms.bondCount; --i >= 0;) {
      Bond bond = bo[i];
      // trajectory atom .mi will be pointing to the trajectory;
      // here we check to see if their base model is this model
      if ((bond.order & Edge.BOND_H_CALC_MASK) != 0
          && am[bond.atom1.mi].trajectoryBaseIndex == modelIndex)
        bsDelete.set(i);
    }
    if (bsDelete.nextSetBit(0) >= 0)
      ms.deleteBonds(bsDelete, false);
    getRasmolHydrogenBonds(bs, bs, null, false, Integer.MAX_VALUE, false, null);
  }

  @Override
  public void getDefaultLargePDBRendering(SB sb, int maxAtoms) {
    BS bs = new BS();
    if (getBondCount() == 0)
      bs = bsAtoms;
    // all biopolymer atoms...
    if (bs != bsAtoms)
      for (int i = 0; i < bioPolymerCount; i++)
        bioPolymers[i].getRange(bs, isMutated);
    if (bs.nextSetBit(0) < 0)
      return;
    // ...and not connected to backbone:
    BS bs2 = new BS();
    if (bs == bsAtoms) {
      bs2 = bs;
    } else {
      for (int i = 0; i < bioPolymerCount; i++)
        if (bioPolymers[i].getType() == BioPolymer.TYPE_NOBONDING)
          bioPolymers[i].getRange(bs2, isMutated);
    }
    if (bs2.nextSetBit(0) >= 0)
      sb.append("select ").append(Escape.eBS(bs2)).append(";backbone only;");
    if (act <= maxAtoms)
      return;
    // ...and it's a large model, to wireframe:
      sb.append("select ").append(Escape.eBS(bs)).append(" & connected; wireframe only;");
    // ... and all non-biopolymer and not connected to stars...
    if (bs != bsAtoms) {
      bs2.clearAll();
      bs2.or(bsAtoms);
      bs2.andNot(bs);
      if (bs2.nextSetBit(0) >= 0)
        sb.append("select " + Escape.eBS(bs2) + " & !connected;stars 0.5;spacefill off;");
    }
  }
  
  @Override
  public BS getAtomBitsStr(int tokType, String specInfo, BS bs) {
    switch (tokType) {
    default:
      return new BS();
    case T.domains:
      return getAnnotationBits("domains", T.domains, specInfo);
    case T.validation:
      return getAnnotationBits("validation", T.validation, specInfo);
      //    case T.annotations:
      //      TODO -- generalize this
    case T.rna3d:
      return getAnnotationBits("rna3d", T.rna3d, specInfo);
    case T.basepair:
      String s = specInfo;
      bs = new BS();
      return (s.length() % 2 != 0 ? bs 
          : ms.getAtomBitsMDa(T.group, getAllBasePairBits(s), bs));
    case T.dssr:
      return getAnnotationBits("dssr", T.dssr, specInfo);
    case T.sequence:
      return getAllSequenceBits(specInfo, null, bs);
    }
  }

  @Override
  public BS getAtomBitsBS(int tokType, BS bsInfo, BS bs) {

    // this first set does not assume sequential order in the file

    Atom[] at = ms.at;
    int ac = ms.ac;
    int i = 0;
    Group g;
    switch (tokType) {
    case T.helix: // WITHIN -- not ends
    case T.sheet: // WITHIN -- not ends
      STR type = (tokType == T.helix ? STR.HELIX : STR.SHEET);
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isWithinStructure(type))
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.carbohydrate:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isCarbohydrate())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.dna:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isDna())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.nucleic:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isNucleic())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.protein:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isProtein())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;

      }
      break;
    case T.purine:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isPurine())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.pyrimidine:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isPyrimidine())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    case T.rna:
      for (i = ac; --i >= 0;) {
        if ((g = at[i].group).isRna())
          g.setAtomBits(bs);
        i = g.firstAtomIndex;
      }
      break;
    }
    if (i < 0)
      return bs;

    // these next assume sequential position in the file
    // speeding delivery -- Jmol 11.9.24

    // TODO WHAT ABOUT MUTATED?

    int i0 = bsInfo.nextSetBit(0);
    if (i0 < 0)
      return bs;
    i = 0;
    switch (tokType) {
    case T.polymer:
      // within(polymer,...)
      for (i = i0; i >= 0; i = bsInfo.nextSetBit(i + 1)) {
        int iPolymer = at[i].group.getBioPolymerIndexInModel();
        if (iPolymer >= 0)
          ((Monomer) at[i].group).bioPolymer.setAtomBitsAndClear(bs, bsInfo);
      }
      break;
    case T.structure:
      // within(structure,...)
      for (i = i0; i >= 0; i = bsInfo.nextSetBit(i + 1)) {
        Structure structure = at[i].group.getStructure();
        if (structure != null)
          structure.setAtomBitsAndClear(bs, bsInfo);
      }
      break;
    }
    if (i == 0)
      Logger.error("MISSING getAtomBits entry for " + T.nameOf(tokType));
    return bs;
  }

  private BS getAnnotationBits(String name, int tok, String specInfo) {
    BS bs = new BS();
    JmolAnnotationParser pa = vwr.getAnnotationParser(name.equals("dssr"));
    Object ann;
    for (int i = ms.mc; --i >= 0;)
      if ((ann = ms.getInfo(i, name)) != null)
        bs.or(pa.getAtomBits(vwr, specInfo,
            ((BioModel) ms.am[i]).getCachedAnnotationMap(name + " V ", ann), ms.am[i].dssrCache, tok,
            i, ms.am[i].bsAtoms));
    return bs;
  }

  Object getCachedAnnotationMap(String key, Object ann) {
    Map<String, Object> cache = (dssrCache == null && ann != null ? dssrCache = new Hashtable<String, Object>()
        : dssrCache);
    if (cache == null)
      return null;
    Object annotv = cache.get(key);
    if (annotv == null && ann != null) {
      annotv = (ann instanceof SV || ann instanceof Hashtable ? ann
              : vwr.parseJSON((String) ann));
      cache.put(key, annotv);
    }
    return (annotv instanceof SV || annotv instanceof Hashtable ? annotv : null);
  }

  @Override
  public BS getIdentifierOrNull(String identifier) {
    int len = identifier.length();
    int pt = 0;
    while (pt < len && PT.isLetter(identifier.charAt(pt)))
      ++pt;
    BS bs = ms.getSpecNameOrNull(identifier.substring(0, pt), false);
    if (pt == len)
      return bs;
    if (bs == null)
      bs = new BS();
    //
    // look for a sequence number or sequence number ^ insertion code
    //
    int pt0 = pt;
    while (pt < len && PT.isDigit(identifier.charAt(pt)))
      ++pt;
    int seqNumber = 0;
    try {
      seqNumber = Integer.parseInt(identifier.substring(pt0, pt));
    } catch (NumberFormatException nfe) {
      return null;
    }
    char insertionCode = ' ';
    if (pt < len && identifier.charAt(pt) == '^')
      if (++pt < len)
        insertionCode = identifier.charAt(pt);
    int seqcode = Group.getSeqcodeFor(seqNumber, insertionCode);
    BS bsInsert = ms.getSeqcodeBits(seqcode, false);
    if (bsInsert == null) {
      if (insertionCode != ' ')
        bsInsert = ms.getSeqcodeBits(Character.toUpperCase(identifier.charAt(pt)),
            false);
      if (bsInsert == null)
        return null;
      pt++;
    }
    bs.and(bsInsert);
    if (pt >= len)
      return bs;
    if(pt != len - 1)
      return null;
    // ALA32B  (no colon; not ALA32:B)
    // old school; not supported for multi-character chains
    bs.and(ms.getChainBits(identifier.charAt(pt)));
    return bs;
  }

  

  /**
   * Get a unitID. Note that we MUST go through the | after InsCode, because
   * if we do not do that we cannot match residues only using string matching.
   * 
   * @param atom
   * @param flags 
   * @return a unitID
   */
  public String getUnitID(Atom atom, int flags) {
    
    //  type: (ID)|model|chain|resid|resno|(atomName)|(altID)|(InsCode)|(symmetry)
    //  res:  ID|model|chain|resid|resno|atomName|altID|InsCode|symmetry  
    SB sb = new SB();
    Group m = atom.group;
    boolean noTrim = !JC.checkFlag(flags, JC.UNITID_TRIM); 
    char ch = (JC.checkFlag(flags, JC.UNITID_INSCODE) ? m.getInsertionCode() : '\0');
    boolean isAll = (ch != '\0');
    if (JC.checkFlag(flags, JC.UNITID_MODEL) && (pdbID != null))
      sb.append(pdbID);      
    sb.append("|").appendO(ms.getInfo(modelIndex, "modelNumber"))
      .append("|").append(vwr.getChainIDStr(m.chain.chainID))
      .append("|").append(m.getGroup3())
      .append("|").appendI(m.getResno());
    if (JC.checkFlag(flags, JC.UNITID_ATOM)) {
      sb.append("|").append(atom.getAtomName());
      if (atom.altloc != '\0')
        sb.append("|").appendC(atom.altloc);
      else if (noTrim || isAll)
        sb.append("|");
    } else if (noTrim || isAll) {
      sb.append("||");
    }
    if (isAll)
      sb.append("|").appendC(ch);
    else if (noTrim)
      sb.append("|");
    if (noTrim)
      sb.append("|");
    return sb.toString();
  }

}
