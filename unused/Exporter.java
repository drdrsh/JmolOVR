/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-05-18 15:41:42 -0500 (Fri, 18 May 2007) $
 * $Revision: 7752 $

 *
 * Copyright (C) 2003-2005  The Jmol Development Team
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

package unused;

//import java.util.Map;
//
//import org.jmol.api.JmolRendererInterface;
//import org.jmol.java.BS;
//import org.jmol.modelset.Atom;
//import org.jmol.util.GData;
//import org.jmol.util.Logger;
//import org.jmol.util.MeshSurface;
//
//import javajs.awt.Font;
//import javajs.util.OC;
//import javajs.util.PT;
//import javajs.util.SB;
//import javajs.util.P3;
//import javajs.util.A4;
//import javajs.util.M3;
//import javajs.util.M4;
//import javajs.util.P3i;
//import javajs.util.T3;
//import javajs.util.V3;
//import org.jmol.viewer.StateManager;
//import org.jmol.viewer.Viewer;


public abstract class Exporter {

//  // The following fields and methods are required for instantiation or provide
//  // generally useful functionality:
//
//  protected Viewer vwr;
//  protected double privateKey;
//  protected JmolRendererInterface jmolRenderer;
//  protected SB output;
//  protected OC out;
//  protected String fileName;
//  protected String commandLineOptions;
//  
//  protected boolean isToFile;
//  protected GData g3d;
//
//  protected short backgroundColix;
//  protected int screenWidth;
//  protected int screenHeight;
//  protected int slabZ;
//  protected int depthZ;
//  protected P3 fixedRotationCenter;
//  protected P3 referenceCenter;
//  protected P3 cameraPosition;
//  protected float cameraDistance;
//  protected float aperatureAngle;
//  protected float scalePixelsPerAngstrom;
//
//
//
//  // Most exporters (Maya, X3D, VRML, IDTF) 
//  // can manipulate actual 3D data.
//  // exportType == Graphics3D.EXPORT_CARTESIAN indicates that and is used:
//  // a) to prevent export of the background image
//  // b) to prevent export of the backgrounds of labels
//  // c) to prevent clipping based on the window size
//  // d) for single bonds, just use the XYZ coordinates
//  
//  // POV-RAY is different -- as EXPORT_RAYTRACER, 
//  // it's taken to be a single view image
//  // with a limited, clipped window.
//  
//  int exportType;
//  
//  final protected static float degreesPerRadian = (float) (180 / Math.PI);
//
//  final protected P3 tempP1 = new P3();
//  final protected P3 tempP2 = new P3();
//  final protected P3 tempP3 = new P3();
//  final protected P3 center = new P3();
//  final protected V3 tempV1 = new V3();
//  final protected V3 tempV2 = new V3();
//  final protected V3 tempV3 = new V3();
//  final protected A4 tempA = new A4();
//  protected String appletName;
//  private boolean isWebGL;
//  private String nBytes;
//  
//  public Exporter() {
//  }
//
//  void setRenderer(JmolRendererInterface jmolRenderer) {
//    this.jmolRenderer = jmolRenderer;
//  }
//  
//  boolean initializeOutput(Viewer vwr, double privateKey, GData g3d, Map<String, Object> params) {
//
//       
//    isWebGL = params.get("type").equals("JS");
//    
//    
//    this.vwr = vwr;
//    appletName = PT.split(vwr.getHtmlName(), "_")[0];
//    this.g3d = g3d;
//    this.privateKey = privateKey;
//    backgroundColix = vwr.getObjectColix(StateManager.OBJ_BACKGROUND);
//    center.setT(vwr.getRotationCenter());
//    if ((screenWidth <= 0) || (screenHeight <= 0)) {
//      screenWidth = vwr.getScreenWidth();
//      screenHeight = vwr.getScreenHeight();
//    }
//    slabZ = g3d.getSlab();
//    depthZ = g3d.getDepth();
//    P3[] cameraFactors = vwr.getCameraFactors();
//    referenceCenter = cameraFactors[0];
//    cameraPosition = cameraFactors[1];
//    fixedRotationCenter = cameraFactors[2];
//    cameraDistance = cameraFactors[3].x;
//    aperatureAngle = cameraFactors[3].y;
//    scalePixelsPerAngstrom = cameraFactors[3].z;
////    isToFile = (output instanceof String);
////    if (isToFile) {
////      fileName = (String) output;
////      int pt = fileName.indexOf(":::"); 
////      if (pt > 0) {
////        commandLineOptions = fileName.substring(pt + 3);
////        fileName = fileName.substring(0, pt);
////      }
////      //vwr.writeTextFile(fileName + ".spt", vwr.getSavedState("_Export"));
////      try {
////        out = vwr.openExportChannel(privateKey, fileName, true);
////      } catch (IOException e) {
////        return false;
////      }
////    } else {
////      this.output = (SB) output;
////    }
//    outputHeader();
//    return true;
//  }
//
//  abstract protected void outputHeader();
//  
//  protected static void setTempVertex(P3 pt, P3 offset, P3 ptTemp) {
//    ptTemp.setT(pt);
//    if (offset != null)
//      ptTemp.add(offset);
//  }
//
//  protected void outputFooter() {
//    // implementation-specific
//  }
//
//  String finalizeOutput() {
//    outputFooter();
//    if (!isToFile)
//      return (output == null ? "" : output.toString());
//    String ret = out.closeChannel();
//    if (ret != null) {
//      Logger.info(ret);
//      return "ERROR EXPORTING FILE:"  + ret;
//    }
//    return "OK " + nBytes + " " + jmolRenderer.getExportName() + " " + fileName ;
//  }
//
//  protected String getTriad(T3 t) {
//    return round(t.x) + " " + round(t.y) + " " + round(t.z); 
//  }
//  
//  protected static String round(double number) { // AH
//    String s;
//    return (number == 0 ? "0" : number == 1 ? "1" : (s = ""
//        + (Math.round(number * 1000d) / 1000d)).startsWith("0.") ? s
//        .substring(1) : s.startsWith("-0.") ? "-" + s.substring(2) : 
//          s.endsWith(".0") ? s.substring(0, s.length() - 2) : s);
//  }
//
//  protected static String round(T3 pt) {
//    return round(pt.x) + " " + round(pt.y) + " " + round(pt.z);
//  }
//  
//  // The following methods are called by a variety of shape renderers and 
//  // Export3D, replacing methods in org.jmol.g3d. More will be added as needed. 
//
//  abstract void drawAtom(Atom atom);
//
//  abstract void drawCircle(int x, int y, int z,
//                                   int diameter, short colix, boolean doFill);  //draw circle 
//
//  abstract boolean drawEllipse(P3 ptAtom, P3 ptX, P3 ptY,
//                             short colix, boolean doFill);
//
//  void drawSurface(MeshSurface meshSurface, short colix) {
//    int nVertices = meshSurface.vertexCount;
//    if (nVertices == 0)
//      return;
//    int nFaces = 0;
//    int nPolygons = meshSurface.polygonCount;
//    BS bsPolygons = meshSurface.bsPolygons;
//    int faceVertexMax = (meshSurface.haveQuads ? 4 : 3);
//    int[][] indices = meshSurface.polygonIndexes;
//    boolean isAll = (bsPolygons == null);
//    if (isAll) {
//      for (int i = nPolygons; --i >= 0;)
//        nFaces += (faceVertexMax == 4 && indices[i].length == 4 ? 2 : 1);      
//    } else {
//      for (int i = bsPolygons.nextSetBit(0); i >= 0; i = bsPolygons.nextSetBit(i + 1))
//        nFaces += (faceVertexMax == 4 && indices[i].length == 4 ? 2 : 1);      
//    }
//    if (nFaces == 0)
//      return;
//
//    P3[] vertices = (P3[]) meshSurface.getVertices();
//    V3[] normals = (V3[]) meshSurface.normals;
//
//    boolean colorSolid = (colix != 0);
//    short[] colixes = (colorSolid ? null : meshSurface.vertexColixes);
//    short[] polygonColixes = (colorSolid ? meshSurface.polygonColixes : null);
//    outputSurface(vertices, normals, colixes, indices, polygonColixes,
//        nVertices, nPolygons, nFaces, bsPolygons, faceVertexMax, colix,
//        meshSurface.offset);
//  }
//
//  /**
//   * @param vertices      generally unique vertices [0:nVertices)
//   * @param normals       one per vertex
//   * @param colixes       one per vertex, or null
//   * @param indices       one per triangular or quad polygon;
//   *                      may have additional elements beyond vertex indices if faceVertexMax = 3
//   *                      triangular if faceVertexMax == 3; 3 or 4 if face VertexMax = 4
//   * @param polygonColixes face-based colixes
//   * @param nVertices      vertices[nVertices-1] is last vertex
//   * @param nPolygons     indices[nPolygons - 1] is last polygon
//   * @param nFaces        number of triangular faces required
//   * @param bsPolygons    number of polygons (triangles or quads)   
//   * @param faceVertexMax (3) triangles only, indices[][i] may have more elements
//   *                      (4) triangles and quads; indices[][i].length determines 
//   * @param colix         overall (solid) color index
//   * @param offset 
//   * 
//   */
//  protected void outputSurface(P3[] vertices, V3[] normals,
//                                short[] colixes, int[][] indices,
//                                short[] polygonColixes,
//                                int nVertices, int nPolygons, int nFaces, BS bsPolygons,
//                                int faceVertexMax, short colix, P3 offset) {
//    // not implemented in _ObjExporter
//  }
//
//  abstract void drawPixel(short colix, int x, int y, int z, int scale); //measures
//  
//  //rockets and dipoles
//  abstract void fillConeScreen(short colix, byte endcap, int screenDiameter, 
//                         P3 screenBase, P3 screenTip, boolean isBarb);
//  
//  abstract void drawCylinder(P3 atom1, P3 atom2, short colix1, short colix2,
//                             byte endcaps, int madBond, int bondOrder);
//
//  abstract void fillCylinderScreenMad(short colix, byte endcaps, int diameter, 
//                                        P3 screenA, P3 screenB);
//
//  abstract void fillCylinderScreen(short colix, byte endcaps, int screenDiameter, 
//                             P3 screenA, P3 screenB, P3 ptA, P3 ptB, float radius);
//
//  abstract void fillEllipsoid(P3 center, P3[] points, short colix, 
//                              int x, int y, int z, int diameter,
//                              M3 toEllipsoidal, double[] coef,
//                              M4 deriv, P3i[] octantPoints);
//
//  void drawFilledCircle(short colixRing, short colixFill, int diameter, int x, int y, int z) {
//    if (colixRing != 0)
//      drawCircle(x, y, z, diameter, colixRing, false);
//    if (colixFill != 0)
//      drawCircle(x, y, z, diameter, colixFill, true);
//  }
//
//  //rockets:
//  abstract void fillSphere(short colix, int diameter, P3 pt);
//  
//  //cartoons, rockets, polyhedra:
//  protected abstract void fillTriangle(short colix, P3 ptA0, P3 ptB0, P3 ptC0, boolean twoSided, boolean isCartesian);
//  
//  
//  public short lineWidthMad;
//  public boolean isCartesian = true;
//
//  /**
//   * @param x 
//   * @param y 
//   * @param z 
//   * @param colix 
//   * @param text 
//   * @param font3d  
//   */
//  void plotText(int x, int y, int z, short colix, String text, Font font3d) {
//    // TODO
//  }
//
//  void plotImage(int x, int y, int z, Object image, short bgcolix, int width,
//                 int height) {
//    // TODO
//    
//  }
//
//  public void drawTextPixel(int argb, int x, int y, int z) {
//    // TODO
//    
//  }
//
//  protected void output(T3 pt) {
//    // TODO
//    
//  }

}



