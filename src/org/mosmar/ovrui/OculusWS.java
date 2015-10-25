package org.mosmar.ovrui;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.oculusvr.capi.Hmd;

import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.*;

import com.oculusvr.capi.EyeRenderDesc;
import com.oculusvr.capi.FovPort;
import com.oculusvr.capi.OvrLibrary;
import com.oculusvr.capi.OvrQuaternionf;
import com.oculusvr.capi.OvrVector3f;
import com.oculusvr.capi.RenderAPIConfig;
import com.oculusvr.capi.TrackingState;
import com.oculusvr.capi.TrackingState.ByValue;

import java.awt.Frame;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javajs.util.M3;
import javajs.util.Quat;

import org.jmol.viewer.TransformManager;
import org.jmol.viewer.Viewer;

import javax.swing.*;

/**
 * WebSocket server that provides tracking info Dependencies are: JOVR, GSON and
 * Java_WebSockets
 *
 * @author Lars Ivar Hatledal
 * Modified by Mostafa Abdelraouf
 */
public class OculusWS {

    public static final int MODE_MOUSE = 0;
    public static final int MODE_VR = 1;

    private static JFrame mWindow = null;
    private static int mMode = MODE_MOUSE;
    private static boolean mIsEnabled = false;

    private static int mSelectedAtom = -1;
    private static M3 mOrientation = null;
    private static OculusWS instance = null;

    protected OculusWS() {
        // Exists only to defeat instantiation.
    }

    public static OculusWS getInstance() {
        if (instance == null) {
            instance = new OculusWS();
        }
        return instance;
    }

    public static void toggleMode(){
        if(isMouseMode()){
            activateVRMode();
        } else {
            activateMouseMode();
        }
    }

    
    public static void centerOnAtom(int index){
        mSelectedAtom = index;
        /*If an atom is clicked and we are in VR mode
        * zoom to that atom at 8000 and hide it to
        * create the illusion that the observer is inside that
        * atom
        */
        String script = null;
        if(OculusWS.isVRMode()){
            script =
                "display (all);"
                + "zoomTo (atomindex=" + Integer.toString(index) + ") 8000;"
                + "hide (atomindex=" + Integer.toString(index) + ");";

        } else {
            script =
                "display (all);"
                + "zoomTo (atomindex=" + Integer.toString(index) + ") 100;";
        }

        instance.mViewer.script(script);

        instance.mViewer.setStatusAtomPicked(index, null, null);
    }

    public static boolean isMouseMode(){
        return mMode == MODE_MOUSE;
    }

    public static boolean isVRMode(){
        return mMode == MODE_VR;
    }

    public static void activateMouseMode() {
      /*
       * We show all atoms, recall that in OVR mode we hide the central atom, 
       * We'll want to show that back again, We zoom back to 100
       */
      
        String script =
            "display (all);"
            + "zoomTo (atomindex=" + Integer.toString(mSelectedAtom) + ") 100;"
            + "set picking center;";
        instance.mViewer.script(script);
        //instance.mViewer.refresh(1,"");
        mMode = MODE_MOUSE;
    }

    public static void activateVRMode(){
      //No atoms selected? Select the first atom in the molecule
        if(mSelectedAtom == -1){
            mSelectedAtom = 0;
        }
        /*
         * What this mode does is
         * 1) Show all atoms
         * 2) Zoom to a value of 8000 on the central atom
         * 3) Hide that atom to remove it from the view and produce the illusion that 
         *    the observer is inside the atom
         *    
         *    I probably don't need all these repetitive calls to "set picking center"
         *    
         */
        String script =
            "set picking center;"
            +"display (all);"
            + "zoomTo (atomindex=" + Integer.toString(mSelectedAtom) + ") 8000;"
            + "hide (atomindex=" + Integer.toString(mSelectedAtom) + ");"
            + "set picking center;";
        instance.mViewer.script(script);
        mMode = MODE_VR;
    }

    public static boolean isEnabled(){
        return mIsEnabled;
    }

    private long id = 0;
    private SensorData latestData = new SensorData(0, 0, 0, 0, 0, 0, 0, 0);
    private boolean run = true;
    private TransformManager mTM;
    private Viewer mViewer;
    protected Hmd mHMD;

    public M3 getOrientation(){
        return mOrientation;
    }

    /**
     * Program starting point
     * 
     * @param v JMol Viewer Object
     * @param tm JMol Transform Manager Object
     *
     * @throws java.net.UnknownHostException
     */
    public void init(Viewer v, TransformManager tm) {

        mTM = tm;
        mViewer = v;
        new Thread(new Runnable() {
            @Override
            public void run() {
              //Hmd stands for Head Mounted Display, That is our oculus :)
                Hmd.initialize();
                mHMD = Hmd.create(0);

                EyeRenderDesc[] configureResult;

                FovPort fovPorts[] = (FovPort[]) new FovPort().toArray(2);

                fovPorts[0] = mHMD.DefaultEyeFov[0];
                fovPorts[1] = mHMD.DefaultEyeFov[1];

                RenderAPIConfig rc = new RenderAPIConfig();
                rc.Header.API = OvrLibrary.ovrRenderAPIType.ovrRenderAPI_None;
                rc.Header.BackBufferSize.w = 1920;
                rc.Header.BackBufferSize.h = 1080;
                rc.Header.Multisample = 1;

                int distortionCaps;

                distortionCaps = OvrLibrary.ovrDistortionCaps.ovrDistortionCap_Chromatic
                            | OvrLibrary.ovrDistortionCaps.ovrDistortionCap_TimeWarp
                            | OvrLibrary.ovrDistortionCaps.ovrDistortionCap_Vignette
                            | OvrLibrary.ovrDistortionCaps.ovrDistortionCap_Overdrive;

//                configureResult = mHMD.configureRendering(rc, distortionCaps, fovPorts);

                HWND hwnd = User32.INSTANCE.FindWindow(null, mWindow.getTitle());
                
                byte result = OvrLibrary.INSTANCE.ovrHmd_AttachToWindow(mHMD, hwnd.getPointer(), null, null);
                ByValue x = OvrLibrary.INSTANCE.ovrHmd_GetTrackingState(mHMD, 6.0);
                mHMD.configureTracking(
                    ovrTrackingCap_Orientation
                    | ovrTrackingCap_MagYawCorrection
                    | ovrTrackingCap_Position, 
                0);

                if (mHMD == null) {
                    org.jmol.util.Logger.error(
                            "Unable to initialize oculus, verify that the HMD is connected and the service is up");
                    throw new IllegalStateException("Unable to initialize HMD");
                }


                
                mIsEnabled = true;
                
                //The thread that gets data from the sensor
                Thread t1 = new Thread(new SensorFetcher(mHMD));
                t1.start();

                try {
                    t1.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
                }

                mHMD.destroy();
                Hmd.shutdown();
            }
        }).start();

    }

    //This methods is called whenever a new molecule is loaded
    public void resetViewer(){

        mWindow.toFront();
        mWindow.setVisible(true);
        mWindow.setState(Frame.NORMAL);
        mWindow.repaint();
        

      /*
         * ENables steroscopic mode and sets the size of atoms to be 15% of vander walls width
         */
        instance.mViewer.runScript("wireframe 0.15;spacefill reset;spacefill 15%;");
        instance.mViewer.runScript("stereo 0");
        activateMouseMode();
        instance.mViewer.refresh(1, "");

        HWND hwnd = User32.INSTANCE.FindWindow(null, mWindow.getTitle());
        byte result = OvrLibrary.INSTANCE.ovrHmd_AttachToWindow(mHMD, hwnd.getPointer(), null, null);
        OvrLibrary.INSTANCE.ovrHmd_GetTrackingState(mHMD, 0.0);

    }

    public static void setFrame(JFrame frame) {
        mWindow = frame;
    }

    private static class SensorFetcher implements Runnable {

        private final Hmd hmd;

        public SensorFetcher(Hmd hmd) {
            this.hmd = hmd;
        }

        @Override
        public void run() {
            while (instance.run) {
                //Ignore VR input if mouse mode is enabled
                //TODO: Stop the thread instead
                OvrLibrary.INSTANCE.ovrHmd_GetTrackingState(hmd, Hmd.getTimeInSeconds());

              TrackingState sensorState = hmd.getSensorState(Hmd.getTimeInSeconds());
              if(mMode == MODE_MOUSE){
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    continue;
                }
                

                OvrVector3f pos = sensorState.HeadPose.Pose.Position;
                OvrQuaternionf quat = sensorState.HeadPose.Pose.Orientation;

                double px = pos.x;
                double py = pos.y;
                double pz = pos.z;

                /*
                 * Some Axis reversal
                 * Note that to produce realism we need to have the model 
                 * rotate in opposite directon of the HMD
                 * The following values are the one that work
                 * I think more research needs to go into finding exactly why
                 * do these work and not some other values , this might have something
                 * to do with the configureTracking() call in the init method
                 */
                
                double qx = quat.z;
                double qy = -quat.y;
                double qz = quat.x;
                double qw = quat.w;

                instance.latestData = new SensorData(instance.id++, px, py, pz, qx, qy, qz, qw);
                System.out.println(instance.latestData);
                Quat q = new Quat();
                q.q0 = (float)qx;
                q.q1 = (float)qy;
                q.q2 = (float)qz;
                q.q3 = (float)qw;

                //q = q.inv();

                mOrientation = q.getMatrix();
                instance.mTM.setRotation(mOrientation);
                instance.mViewer.refresh(1,"");

                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private static class SensorData {

        private final long id;
        private final double px, py, pz, qx, qy, qz, qw;

        public SensorData(long id, double px, double py, double pz, double qx, double qy, double qz, double qw) {
            this.id = id;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
        }

        public long getId() {
            return id;
        }

        public double[] asArray() {
            return new double[]{id, px, py, pz, qx, qy, qz, qw};
        }

        @Override
        public String toString() {
            return String.format("Position: %.3f  %.3f  %.3f | Quat:  %.3f  %.3f  %.3f  %.3f", px, py, pz, qx, qy, qz, qw);
        }
    }
}