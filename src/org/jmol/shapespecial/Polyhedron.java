package org.jmol.shapespecial;

import java.util.Hashtable;
import java.util.Map;

import javajs.util.AU;
import javajs.util.Lst;
import javajs.util.M4;
import javajs.util.Measure;
import javajs.util.P3;
import javajs.util.V3;

import org.jmol.api.SmilesMatcherInterface;
import org.jmol.api.SymmetryInterface;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.ModelSet;
import org.jmol.script.SV;
import org.jmol.script.T;
import org.jmol.util.C;
import org.jmol.util.Escape;
import org.jmol.util.Node;
import org.jmol.util.Normix;
import org.jmol.util.Point3fi;
import org.jmol.viewer.Viewer;

public class Polyhedron {

  int modelIndex;
  public Atom centralAtom;
  public P3[] vertices;
  public int[][] faces;
  int nVertices;
  boolean collapsed = false;
  private BS bsFlat;
  
  private V3[] normals;
  private short[] normixes;
  public String smiles, smarts;
  private SymmetryInterface pointGroup;
  private Float volume;

  boolean visible = true;
  boolean isFullyLit;
  public boolean isValid = true;
  public short colixEdge = C.INHERIT_ALL;
  public int visibilityFlags = 0;

  Polyhedron() {  
  }
  
  Polyhedron set(Atom centralAtom, int nVertices, int nPoints, int planeCount,
      P3[] otherAtoms, V3[] normals, BS bsFlat, int[][] planes, boolean collapsed) {
    this.centralAtom = centralAtom;
    modelIndex = centralAtom.mi;
    this.nVertices = nVertices;
    this.vertices = new P3[nPoints + 1];
    this.normals = new V3[planeCount];
    this.bsFlat = bsFlat;
    this.faces = AU.newInt2(planeCount);
    for (int i = nPoints + 1; --i >= 0;)
      // includes central atom as last atom or possibly reference point
      vertices[i] = otherAtoms[i];
    for (int i = planeCount; --i >= 0;)
      this.normals[i] = V3.newV(normals[i]);
    for (int i = planeCount; --i >= 0;)
      this.faces[i] = planes[i];
    this.collapsed = collapsed;
    return this;
  }

  Map<String, Object> info;
  
  Map<String, Object> getInfo(Viewer vwr, boolean isAll) {
    if (isAll && this.info != null)
      return this.info;
    Map<String, Object> info = new Hashtable<String, Object>();
    if (isAll) {
      this.info = info;
      info.put("modelIndex", Integer.valueOf(centralAtom.mi));
      info.put("modelNumber", Integer.valueOf(centralAtom.getModelNumber()));
      info.put("center", P3.newP(centralAtom));
      info.put("atomNumber", Integer.valueOf(centralAtom.getAtomNumber()));
      info.put("atomName", centralAtom.getInfo());
      info.put("element", centralAtom.getElementSymbol());
      info.put("vertexCount", Integer.valueOf(nVertices));
      info.put("faceCount", Integer.valueOf(faces.length));
      info.put("volume", getVolume());
      if (smarts != null)
        info.put("smarts", smarts);
      if (smiles != null)
        info.put("smiles", smiles);
      if (pointGroup != null)
        info.put("pointGroup", pointGroup.getPointGroupName());
      Object energy = vwr.ms.getInfo(centralAtom.mi, "Energy");
      if (energy != null)
        info.put("energy", energy);
    } else {
      info.put("bsFlat", bsFlat);
      if (collapsed)
        info.put("collapsed", Boolean.valueOf(collapsed));
      info.put("ptRef", vertices[nVertices]);
    }
    info.put("atomIndex", Integer.valueOf(centralAtom.i));
    info.put("vertices", AU.arrayCopyPt(vertices, nVertices));
    info.put("faces", AU.arrayCopyII(faces, faces.length));
    int[] elemNos = new int[nVertices];
    for (int i = 0; i < nVertices; i++) {
      P3 pt = vertices[i];
      elemNos[i] = (pt instanceof Node ? ((Node) pt).getElementNumber()
          : pt instanceof Point3fi ? ((Point3fi) pt).sD : -2);
    }
    info.put("elemNos", elemNos);
    return info;
  }

  Polyhedron setInfo(Map<String, SV> info, Atom[] at) {
    try {
      centralAtom = at[info.get("atomIndex").intValue];
      modelIndex = centralAtom.mi;
      Lst<SV> lst = info.get("vertices").getList();
      vertices = new P3[lst.size() + 1];
      nVertices = vertices.length - 1;
      for (int i = nVertices; --i >= 0;)
        vertices[i] = SV.ptValue(lst.get(i));
      lst = info.get("elemNos").getList();
      for (int i = nVertices; --i >= 0;) {
        int n = lst.get(i).intValue;
        if (n > 0) {
          Point3fi p = new Point3fi();
          p.setT(vertices[i]);
          p.sD = (short) n;
          vertices[i] = p;
        }
      }
      vertices[nVertices] = SV.ptValue(info.get("ptRef"));
      lst = info.get("faces").getList();
      faces = AU.newInt2(lst.size());
      normals = new V3[faces.length];
      V3 vAB = new V3();
      for (int i = faces.length; --i >= 0;) {
        Lst<SV> lst2 = lst.get(i).getList();
        int[] a = new int[lst2.size()];
        for (int j = a.length; --j >= 0;)
          a[j] = lst2.get(j).intValue;
        faces[i] = a;
        normals[i] = new V3();
        Measure.getNormalThroughPoints(vertices[a[0]], vertices[a[1]],
            vertices[a[2]], normals[i], vAB);
      }
      bsFlat = SV.getBitSet(info.get("bsFlat"), false);
      collapsed = info.containsKey("collapsed");
    } catch (Exception e) {
      return null;
    }
    return this;
  }

  void getSymmetry(Viewer vwr, boolean withPointGroup) {
    info = null;
    SmilesMatcherInterface sm = vwr.getSmilesMatcher();
    try {
      if (smarts == null) {
        smarts = sm.polyhedronToSmiles(faces, nVertices, null);
        smiles = sm.polyhedronToSmiles(faces, nVertices, vertices);
      }
    } catch (Exception e) {
    }
    if (pointGroup == null && withPointGroup)
      pointGroup = vwr.ms.getSymTemp(true).setPointGroup(null, vertices, null,
          false, vwr.getFloat(T.pointgroupdistancetolerance),
          vwr.getFloat(T.pointgrouplineartolerance), true);

  }

  /**
   * allows for n-gon, not just triangle; if last component index is negative,
   * then that's a mesh code
   * 
   * @return volume
   */
  private Float getVolume() {
    // this will give spurious results for overlapping faces triangles
    if (volume != null)
      return volume;
    V3 vAB = new V3();
    V3 vAC = new V3();
    V3 vTemp = new V3();
    float v = 0;
    for (int i = faces.length; --i >= 0;) {
      int[] face = faces[i];
      for (int j = face.length - 2; --j >= 0;)
        if (face[j + 2] >= 0)
          v += triangleVolume(face[j], face[j + 1], face[j + 2], vAB, vAC,
              vTemp);
    }
    return Float.valueOf(v / 6);
  }

  private float triangleVolume(int i, int j, int k, V3 vAB, V3 vAC, V3 vTemp) {
    // volume
    vAB.setT(vertices[i]);
    vAC.setT(vertices[j]);
    vTemp.cross(vAB, vAC);
    vAC.setT(vertices[k]);
    return vAC.dot(vTemp);
  }

  String getState(Viewer vwr) {
    return "  var p = " + Escape.e(getInfo(vwr, false)) + ";polyhedron @p" 
        + (isFullyLit ? " fullyLit" : "") + ";"
        + (visible ? "" : "polyhedra ({"+centralAtom.i+"}) off;") + "\n";
  }

  public void move(M4 mat) {
    info = null;
    for (int i = 0; i < nVertices; i++) {
      P3 p = vertices[i];
      if (p instanceof Atom)
        p = vertices[i] = P3.newP(p);
      mat.rotTrans(p);
    }
    for (int i = normals.length; --i >= 0;)
      mat.rotate(normals[i]);
    normixes = null;
  }

  public short[] getNormixes() {
    if (normixes == null) {
      normixes = new short[normals.length];
      BS bsTemp = new BS();
      for (int i = normals.length; --i >= 0;)
        normixes[i] = (bsFlat.get(i) ? Normix.get2SidedNormix(normals[i],
            bsTemp) : Normix.getNormixV(normals[i], bsTemp));
    }
    return normixes;
  }
}