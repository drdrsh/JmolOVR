/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-09-25 15:33:17 -0500 (Wed, 25 Sep 2013) $
 * $Revision: 18695 $
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
 *  Lesser General License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.awt;

import java.awt.Component;


import com.oculusvr.capi.Hmd;
import com.sun.org.apache.xml.internal.security.Init;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import javajs.api.EventManager;
import javajs.api.GenericMouseInterface;
import javajs.api.PlatformViewer;
import javajs.awt.event.Event;
import javajs.util.PT;

import org.jmol.script.T;
import org.jmol.util.Logger;
import org.jmol.viewer.Viewer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;
import org.mosmar.ovrui.OculusWS;

import static org.lwjgl.opengl.GL11.*;

/**
 * formerly org.jmol.viewer.MouseManager14
 *
 * methods required by Jmol that access java.awt.event
 *
 * private to org.jmol.awt
 *
 */

public class Mouse implements MouseWheelListener, MouseListener,
        MouseMotionListener, KeyListener, GenericMouseInterface {

  private Viewer vwr;
  private EventManager manager;

  //double privateKey;
  /**
   * Mouse is the listener for all events.
   *
   * @param privateKey
   * @param vwr
   * @param odisplay
   */
  Mouse(double privateKey, PlatformViewer vwr, Object odisplay) {
    //this.privateKey = privateKey;
    this.vwr = (Viewer) vwr;
    manager = this.vwr.acm;
    Component display = (Component) odisplay;
    display.addKeyListener(this);
    display.addMouseListener(this);
    display.addMouseMotionListener(this);
    display.addMouseWheelListener(this);
  }

  @Override
  public void clear() {
    // nothing to do here now -- see ActionManager
  }

  @Override
  public void dispose() {
    Component display = (Component) vwr.display;
    display.removeMouseListener(this);
    display.removeMouseMotionListener(this);
    display.removeMouseWheelListener(this);
    display.removeKeyListener(this);
  }

  @Override
  public boolean processEvent(int id, int x, int y, int modifiers, long time) {
    modifiers = applyLeftMouse(modifiers);
    switch (id) {
      case Event.MOUSE_DOWN:
        xWhenPressed = x;
        yWhenPressed = y;
        modifiersWhenPressed10 = modifiers;
        mousePressed(time, x, y, modifiers, false);
        break;
      case Event.MOUSE_DRAG:
        mouseDragged(time, x, y);
        break;
      case Event.MOUSE_ENTER:
        mouseEntered(time, x, y);
        break;
      case Event.MOUSE_EXIT:
        mouseExited(time, x, y);
        break;
      case Event.MOUSE_MOVE:
        mouseMoved(time, x, y, modifiers);
        break;
      case Event.MOUSE_UP:
        mouseReleased(time, x, y, modifiers);
        // simulate a mouseClicked event for us
        if (x == xWhenPressed && y == yWhenPressed
                && modifiers == modifiersWhenPressed10) {
          // the underlying code will turn this into dbl clicks for us
          mouseClicked(time, x, y, modifiers, 1);
        }
        break;
      default:
        return false;
    }
    return true;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    mouseClicked(e.getWhen(), e.getX(), e.getY(), e.getModifiers(), e
            .getClickCount());
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    mouseEntered(e.getWhen(), e.getX(), e.getY());
  }

  @Override
  public void mouseExited(MouseEvent e) {
    mouseExited(e.getWhen(), e.getX(), e.getY());
  }

  @Override
  public void mousePressed(MouseEvent e) {
    mousePressed(e.getWhen(), e.getX(), e.getY(), e.getModifiers(), e
            .isPopupTrigger());
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    mouseReleased(e.getWhen(), e.getX(), e.getY(), e.getModifiers());
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    mouseDragged(e.getWhen(), e.getX(), e.getY());
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    mouseMoved(e.getWhen(), e.getX(), e.getY(), e.getModifiers());
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    e.consume();
    mouseWheel(e.getWhen(), e.getWheelRotation(), e.getModifiers());
  }

  @Override
  public void keyTyped(KeyEvent ke) {
    ke.consume();
    char ch = Character.toUpperCase(ke.getKeyChar());

    if(ch == KeyEvent.VK_BACK_SPACE) {
      createWindow();
    }
    //If oculus mode is enabled use Space to toggle between mouse and vr mode
    if(OculusWS.isEnabled()){
      if(ch == KeyEvent.VK_BACK_SPACE){
        OculusWS.toggleMode();
      }
    }

    if (!vwr.menuEnabled())
      return;
    int modifiers = ke.getModifiers();
    // for whatever reason, CTRL may also drop the 6- and 7-bits,
    // so we are in the ASCII non-printable region 1-31
    if (Logger.debuggingHigh)
      Logger.debug("MouseManager keyTyped: " + ch + " " + (0+ch) + " " + modifiers);
    if (modifiers != 0 && modifiers != Event.SHIFT_MASK) {
      switch (ch) {


        case (char) 11:
        case 'k': // keystrokes on/off
          boolean isON = !vwr.getBooleanProperty("allowKeyStrokes");
          switch (modifiers) {
            case Event.CTRL_MASK:
              vwr.setBooleanProperty("allowKeyStrokes", isON);
              vwr.setBooleanProperty("showKeyStrokes", true);
              break;
            case Event.CTRL_ALT:
            case Event.ALT_MASK:
              vwr.setBooleanProperty("allowKeyStrokes", isON);
              vwr.setBooleanProperty("showKeyStrokes", false);
              break;
          }
          clearKeyBuffer();
          vwr.refresh(3, "showkey");
          break;
        case 22:
        case 'v': // paste
          switch (modifiers) {
            case Event.CTRL_MASK:
              String ret = vwr.getClipboardText();
              if (ret == null)
                break;
              if (ret.startsWith("http://") && ret.indexOf("\n") < 0)
                ret = "LoAd " + PT.esc(ret);
              if (ret.startsWith("LoAd ")) {
                vwr.evalStringQuietSync(ret, false, true);
                break;
              }
              ret = vwr.loadInlineAppend(ret, false);
              if (ret != null)
                Logger.error(ret);
          }
          break;
        case 26:
        case 'z': // undo
          switch (modifiers) {
            case Event.CTRL_MASK:
              vwr.undoMoveAction(T.undomove, 1);
              break;
            case Event.CTRL_SHIFT:
              vwr.undoMoveAction(T.redomove, 1);
              break;
          }
          break;
        case 25:
        case 'y': // redo
          switch (modifiers) {
            case Event.CTRL_MASK:
              vwr.undoMoveAction(T.redomove, 1);
              break;
          }
          break;
      }
      return;
    }
    if (!vwr.getBooleanProperty("allowKeyStrokes"))
      return;
    addKeyBuffer(ke.getModifiers() == Event.SHIFT_MASK ? Character.toUpperCase(ch) : ch);
  }

  @Override
  public void keyPressed(KeyEvent ke) {
    if (vwr.isApplet)
      ke.consume();
    manager.keyPressed(ke.getKeyCode(), ke.getModifiers());
  }

  @Override
  public void keyReleased(KeyEvent ke) {
    ke.consume();
    manager.keyReleased(ke.getKeyCode());
  }

  private String keyBuffer = "";

  private void clearKeyBuffer() {
    if (keyBuffer.length() == 0)
      return;
    keyBuffer = "";
    if (vwr.getBooleanProperty("showKeyStrokes"))
      vwr
              .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo \"\"", true, true);
  }

  private void addKeyBuffer(char ch) {
    if (ch == 10) {
      sendKeyBuffer();
      return;
    }
    if (ch == 8) {
      if (keyBuffer.length() > 0)
        keyBuffer = keyBuffer.substring(0, keyBuffer.length() - 1);
    } else {
      keyBuffer += ch;
    }
    if (vwr.getBooleanProperty("showKeyStrokes"))
      vwr
              .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo "
                      + PT.esc("\1" + keyBuffer), true, true);
  }

  private void sendKeyBuffer() {
    String kb = keyBuffer;
    if (vwr.getBooleanProperty("showKeyStrokes"))
      vwr
              .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo "
                      + PT.esc(keyBuffer), true, true);
    clearKeyBuffer();
    vwr.evalStringQuietSync(kb, false, true);
  }

  private void mouseEntered(long time, int x, int y) {
    wheeling = false;
    manager.mouseEnterExit(time, x, y, false);
  }

  private void mouseExited(long time, int x, int y) {
    wheeling = false;
    manager.mouseEnterExit(time, x, y, true);
  }

  /**
   *
   * @param time
   * @param x
   * @param y
   * @param modifiers
   * @param clickCount
   */
  private void mouseClicked(long time, int x, int y, int modifiers, int clickCount) {
    clearKeyBuffer();
    // clickedCount is not reliable on some platforms
    // so we will just deal with it ourselves
    manager.mouseAction(Event.CLICKED, time, x, y, 1, modifiers);
  }

  private boolean isMouseDown; // Macintosh may not recognize CTRL-SHIFT-LEFT as drag, only move
  private boolean wheeling;
  private int modifiersDown;

  private void mouseMoved(long time, int x, int y, int modifiers) {
    clearKeyBuffer();
    if (isMouseDown)
      manager.mouseAction(Event.DRAGGED, time, x, y, 0, modifiersDown);
    else
      manager.mouseAction(Event.MOVED, time, x, y, 0, modifiers);
  }

  private void mouseWheel(long time, int rotation, int modifiers) {
    clearKeyBuffer();
    wheeling = true;
    manager.mouseAction(Event.WHEELED, time, 0, rotation, 0,
            modifiers & ~Event.MOUSE_MIDDLE | Event.MOUSE_WHEEL);
  }

  /**
   *
   * @param time
   * @param x
   * @param y
   * @param modifiers
   * @param isPopupTrigger
   */
  private void mousePressed(long time, int x, int y, int modifiers,
                            boolean isPopupTrigger) {
    clearKeyBuffer();
    isMouseDown = true;
    modifiersDown = modifiers; // Mac does not transmit these during drag
    wheeling = false;
    manager.mouseAction(Event.PRESSED, time, x, y, 0, modifiers);
  }

  private void mouseReleased(long time, int x, int y, int modifiers) {
    isMouseDown = false;
    modifiersDown = 0;
    wheeling = false;
    manager.mouseAction(Event.RELEASED, time, x, y, 0, modifiers);
  }

  private void mouseDragged(long time, int x, int y) {
    if (wheeling)
      return;
    if ((modifiersDown & Event.MAC_COMMAND) == Event.MAC_COMMAND)
      modifiersDown = modifiersDown & ~Event.MOUSE_RIGHT | Event.CTRL_MASK;
    manager.mouseAction(Event.DRAGGED, time, x, y, 0, modifiersDown);
  }

  private static int applyLeftMouse(int modifiers) {
    // if neither BUTTON2 or BUTTON3 then it must be BUTTON1
    return ((modifiers & Event.BUTTON_MASK) == 0) ? (modifiers | Event.MOUSE_LEFT)
            : modifiers;
  }

  private int xWhenPressed, yWhenPressed, modifiersWhenPressed10;

  @Override
  public void processTwoPointGesture(float[][][] touches) {
    // n/a

  }

  private static Long window;
  private static int textureId = -1;
  private static boolean textureChanged = false;

  public static void DrawGL(BufferedImage image){
    if(textureChanged){
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

      if(textureId == -1) {
        textureId = GL11.glGenTextures();
      }
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

      // You now have a ByteBuffer filled with the color data of each pixel.
      // Now just create a texture ID and bind it. Then you can load it using
      // whatever OpenGL method you want, for example:
      GLU.gluBuild2DMipmaps(GL_TEXTURE_2D, GL_RGB8, textureWidth, textureHeight, GL_RGB, GL_UNSIGNED_BYTE, textureBuffer);
      //glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, image.getWidth(), image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
      //  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, 128, 128, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

      textureChanged = false;
    }
    // Clear the screen and depth buffer
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    // set the color of the quad (R,G,B,A)
    GL11.glColor3f(1.0f, 1.0f, 1.0f);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

    //Draw quad
    GL11.glBegin(GL11.GL_QUADS);
      GL11.glTexCoord2d(0,1);GL11.glVertex2f(0, 0);
      GL11.glTexCoord2d(1,1);GL11.glVertex2f(textureWidth, 0);
      GL11.glTexCoord2d(1,0);GL11.glVertex2f(textureWidth, textureHeight);
      GL11.glTexCoord2d(0,0);GL11.glVertex2f(0, textureHeight);
    GL11.glEnd();
  }

  public static void UpdateImage(BufferedImage img){

  }

  public static void RenderGL(){

    BufferedImage image = (BufferedImage)OculusWS.getInstance().mViewer.getScreenImageBuffer(null, false);
    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

    ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 3); //4 for RGBA, 3 for RGB

    for(int y = 0; y < image.getHeight(); y++){
      for(int x = 0; x < image.getWidth(); x++){
        int pixel = pixels[y * image.getWidth() + x];
        buffer.put((byte) ((pixel >> 16) & 0xFF));     // Red component
        buffer.put((byte) ((pixel >> 8) & 0xFF));      // Green component
        buffer.put((byte) (pixel & 0xFF));               // Blue component
      }
    }
    buffer.flip();
    GL11.glDrawPixels(image.getWidth(), image.getHeight(), GL_RGB, GL_UNSIGNED_BYTE, buffer);
  }

  private static  int textureWidth;
  private static  int textureHeight;
  private static  ByteBuffer textureBuffer;

  public static void LoadTex(BufferedImage image) {
    if (!isOpenGLActive) {
      return;
    }

//    BufferedImage image = (BufferedImage)OculusWS.getInstance().mViewer.getScreenImageBuffer(null, false);

    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

    textureBuffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 3); //4 for RGBA, 3 for RGB

    /*
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = pixels[y * image.getWidth() + x];
        */
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        int pixel = pixels[x * image.getHeight() + y];
        textureBuffer.put((byte) ((pixel >> 16) & 0xFF));     // Red component
        textureBuffer.put((byte) ((pixel >> 8) & 0xFF));      // Green component
        textureBuffer.put((byte) (pixel & 0xFF));               // Blue component
        //buffer.put((byte) ((pixel >> 24) & 0xFF));    // Alpha component. Only for RGBA
      }
    }

    textureBuffer.flip(); //FOR THE LOVE OF GOD DO NOT FORGET THIS
    textureWidth=  image.getWidth();
    textureHeight = image.getHeight();

    textureChanged = true;

  }

  public static void InitGL(BufferedImage image){

    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadIdentity();
    GL11.glOrtho(0, image.getWidth(), 0, image.getHeight(), 1, -1);
    GL11.glMatrixMode(GL11.GL_MODELVIEW);

  }
  private static Thread openGLThread;
  private static boolean isOpenGLActive = false;
  public static String createWindow() {
      openGLThread = new Thread(new Runnable() {
      @Override
      public void run() {
        BufferedImage initialImage = (BufferedImage)OculusWS.getInstance().mViewer.getScreenImageBuffer(null, false);
        try {
          Display.setDisplayMode(new DisplayMode(initialImage.getWidth(), initialImage.getHeight()));
          Display.create();
        } catch (LWJGLException e) {
          e.printStackTrace();
//      System.exit(0);
        }

        // init OpenGL here
        InitGL(initialImage);
        isOpenGLActive = true;
        while (!Display.isCloseRequested()) {

          DrawGL(initialImage);
          // render OpenGL here
          //RenderGL();
          Display.update();
        }

        Display.destroy();
      }
    });
    openGLThread.start();





    return "";
  }
}
