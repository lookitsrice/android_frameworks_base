/*
 * Copyright (C) 2012 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

import android.app.*;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.CompatibilityInfo;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import java.io.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Math;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ExtendedPropertiesUtils {
 
    private static final String TAG = "paranoid";

    /**
     * Public variables
     */
    public static final String PARANOID_PROPERTIES = "/system/etc/paranoid/properties.conf";
    public static final String PARANOID_DIR = "/system/etc/paranoid/";
    public static final String PARANOID_MAINCONF = "properties.conf";
    public static final String PARANOID_BACKUPCONF = "backup.conf";
    public static final String PARANOID_PREFIX_0 = "mod";
    public static final String PARANOID_PREFIX_1 = "preferences";
    public static final String PARANOID_PREFIX_2 = "ro";
    public static final String PARANOID_PREFIX_3 = "com";
    public static final String PARANOID_PREFIX_4 = "version";
    public static final String PARANOID_PREFIX = "%";
    public static final String PARANOID_SEPARATOR = ".";
    public static final String PARANOID_STRING_DELIMITER = "\\|";
    public static final String PARANOID_DPI_SUFFIX = ".dpi";
    public static final String PARANOID_LAYOUT_SUFFIX = ".layout";
    public static final String PARANOID_FORCE_SUFFIX = ".force";
    public static final String PARANOID_LARGE_SUFFIX = ".large";
    public static final String PARANOID_CHECK_SUFFIX = ".version";
    public static final String PARANOID_DENSITY_SUFFIX = ".den";
    public static final String PARANOID_SCALEDDENSITY_SUFFIX = ".sden";

    // Color definitions
    public static final String PARANOID_COLORS_SUFFIX = ".colors";
    public static final int PARANOID_COLORS_COUNT = 4;
    public static final String[] PARANOID_COLORS_SETTINGS = {Settings.System.NAV_BAR_COLOR,
            Settings.System.NAV_BUTTON_COLOR, Settings.System.NAV_GLOW_COLOR,
            Settings.System.STATUS_BAR_COLOR};
    public static final String[] PARANOID_COLORS_DEFAULTS = {"FF000000|FF000000|0", "B2FFFFFF|B2FFFFFF|0",
            "FFFFFFFF|FFFFFFFF|0", "FF000000|FF000000|0"};
    public static final int[] PARANOID_COLORCODES_DEFAULTS = {0xFF000000, 0xB2FFFFFF, 0xFFFFFFFF, 0xFF000000};
    public static final int PARANOID_COLORS_NAVBAR = 0;
    public static final int PARANOID_COLORS_NAVBUTTON = 1;
    public static final int PARANOID_COLORS_NAVGLOW = 2;
    public static final int PARANOID_COLORS_STATBAR = 3;

    public static HashMap<String, String> mPropertyMap = new HashMap<String, String>();
    public static ActivityThread mMainThread;
    public static Context mContext;
    public static PackageManager mPackageManager;    
    public static Display mDisplay;
    public static List<PackageInfo> mPackageList;

    public static ParanoidAppInfo mGlobalHook = new ParanoidAppInfo();
    public ParanoidAppInfo mLocalHook = new ParanoidAppInfo();
    public static boolean mIsHybridModeEnabled;

    public static boolean mIsTablet;
    public static int mRomLcdDensity = DisplayMetrics.DENSITY_DEFAULT;

    public static native String readFile(String s);
    
    /**
     * Contains all the details for an application
     */
    public static class ParanoidAppInfo {
        public String name = "";
        public String path = "";
        public boolean active;
        public int pid;
        public ApplicationInfo info;
        public int dpi;
        public int layout;
        public int force;
        public int large;
        public float scaledDensity;
        public float density;
        public String[] colors = new String[4];
    }

    /**
     * Enum interface to allow different override modes
     */
    public static enum OverrideMode {
        ExtendedProperties, AppInfo, FullName, FullNameExclude, PackageName
    }

    /**
     * Set app configuration for the input argument <code>info</code>.
     * This is done by fetching properties.conf or our stored {@link HashMap}.
     *
     * @param  info  instance containing app details
     */
    public static void setAppConfiguration(ParanoidAppInfo info) {
        if(mIsHybridModeEnabled){
//        if(mIsHybridModeEnabled && isEnvironmentSane()){
            // Load default values to be used in case that property is 
            // missing from configuration.
            boolean isSystemApp = info.path.contains("system/app");
            int defaultDpi = Integer.parseInt(getProperty(PARANOID_PREFIX + (isSystemApp ? 
                "system_default_dpi" : (info.path.length() == 0 ? "0" : "user_default_dpi"))));
            int defaultLayout = Integer.parseInt(getProperty(PARANOID_PREFIX + (isSystemApp ? 
                "system_default_layout" : (info.path.length() == 0 ? "0" : "user_default_layout"))));

            // Layout fetching.
            info.layout = Integer.parseInt(getProperty(info.name + PARANOID_LAYOUT_SUFFIX, String.valueOf(defaultLayout)));

            // DPI fetching.
            info.dpi = Integer.parseInt(getProperty(info.name + PARANOID_DPI_SUFFIX, String.valueOf(defaultDpi)));

            // Extra density fetching.
            info.density = Float.parseFloat(getProperty(info.name + PARANOID_DENSITY_SUFFIX));
            info.scaledDensity = Float.parseFloat(getProperty(info.name + PARANOID_SCALEDDENSITY_SUFFIX));

            // In case that densities aren't determined in previous step
            // we calculate it by dividing DPI by default density (160).
            if (info.dpi != 0) {			
                info.density = info.density == 0 ? info.dpi / (float) DisplayMetrics.DENSITY_DEFAULT : info.density;
                info.scaledDensity = info.scaledDensity == 0 ? info.dpi / (float) DisplayMetrics.DENSITY_DEFAULT : info.scaledDensity;
            }

            // Extra parameters. Force allows apps to penetrate their hosts, 
            // while large appends SCREENLAYOUT_SIZE_XLARGE mask that makes 
            // layout matching to assign bigger containers.
            info.force = Integer.parseInt(getProperty(info.name + PARANOID_FORCE_SUFFIX));
            info.large = Integer.parseInt(getProperty(info.name + PARANOID_LARGE_SUFFIX));

            // Color parameters
            String[] colors = getProperty(info.name +
                    PARANOID_COLORS_SUFFIX).split(PARANOID_STRING_DELIMITER);
            if (colors.length == PARANOID_COLORS_COUNT) {
                for(int i=0; i < colors.length; i++) {
                    info.colors[i] = colors[i].toUpperCase();
                }
            }

            // If everything went nice, stop parsing.
            info.active = true;
        }
    }

    /**
     * Overrides current hook with input parameter <code>mode</code>, wich
     * is an enum interface that stores basic override possibilities.
     *
     * @param  input  object to be overriden
     * @param  mode  enum interface
     */
    public void overrideHook(Object input, OverrideMode mode) {
        if (isInitialized() && input != null) {

            ApplicationInfo tempInfo;
            ExtendedPropertiesUtils tempProps;

            switch (mode) {
                case ExtendedProperties:
                    tempProps = (ExtendedPropertiesUtils) input;
                    if (tempProps.mLocalHook.active) {
                        mLocalHook.active = tempProps.mLocalHook.active;
                        mLocalHook.pid = tempProps.mLocalHook.pid;
                        mLocalHook.info = tempProps.mLocalHook.info;
                        mLocalHook.name = tempProps.mLocalHook.name;
                        mLocalHook.path = tempProps.mLocalHook.path;
                        mLocalHook.layout = tempProps.mLocalHook.layout;
                        mLocalHook.dpi = tempProps.mLocalHook.dpi;
                        mLocalHook.force = tempProps.mLocalHook.force;
                        mLocalHook.large = tempProps.mLocalHook.large;
                        mLocalHook.scaledDensity = tempProps.mLocalHook.scaledDensity;
                        mLocalHook.density = tempProps.mLocalHook.density;                        
                    }
                    return;
                case AppInfo:
                    mLocalHook.info = (ApplicationInfo)input;
                    break;
                case FullName:
                    mLocalHook.info = getAppInfoFromPath((String) input);
                    break;
                case FullNameExclude:
                    tempInfo = getAppInfoFromPath((String) input);
                    if (tempInfo != null && (!isHooked() || getProperty(tempInfo.packageName + PARANOID_FORCE_SUFFIX).equals("1"))) {
                        mLocalHook.info = tempInfo;
                    }
                    break;
                case PackageName:
                    mLocalHook.info = getAppInfoFromPackageName((String) input);
                    break;
            }

            if (mLocalHook.info != null) {
                mLocalHook.pid = android.os.Process.myPid();
                mLocalHook.name = mLocalHook.info.packageName;
                mLocalHook.path = mLocalHook.info.sourceDir.substring(0, 
                        mLocalHook.info.sourceDir.lastIndexOf("/"));

                setAppConfiguration(mLocalHook);
            }
        }
    }

    /**
     * This methods are used to retrieve specific information for a hook. 
     */
    public static boolean isInitialized() {
        return (mContext != null);
    }
    public static boolean isHooked() {
        return (isInitialized() && !mGlobalHook.name.equals("android") && !mGlobalHook.name.equals(""));
    }
    public boolean getActive() {
        return mLocalHook.active ? mLocalHook.active : mGlobalHook.active;
    }
    public int getPid() {
        return mLocalHook.active ? mLocalHook.pid : mGlobalHook.pid;
    }
    public ApplicationInfo getInfo() {
        return mLocalHook.active ? mLocalHook.info : mGlobalHook.info;
    }
    public String getName() {
        return mLocalHook.active ? mLocalHook.name : mGlobalHook.name;
    }
    public String getPath() {
        return mLocalHook.active ? mLocalHook.path : mGlobalHook.path;
    }
    public int getLayout() {
        return mLocalHook.active ? mLocalHook.layout : mGlobalHook.layout;
    }
    public int getDpi() {
        return mLocalHook.active ? mLocalHook.dpi : mGlobalHook.dpi;
    }
    public float getScaledDensity() { 
        return mLocalHook.active ? mLocalHook.scaledDensity : mGlobalHook.scaledDensity;
    }
    public boolean getForce() {
        return (mLocalHook.active ? mLocalHook.force : mGlobalHook.force) == 1;
    }
    public boolean getLarge() {
        return (mLocalHook.active ? mLocalHook.large : mGlobalHook.large) == 1;
    }
    public float getDensity() {
        return mLocalHook.active ? mLocalHook.density : mGlobalHook.density;
    }

    
    /**
     * Returns an {@link ApplicationInfo}, with the given path.
     *
     * @param  path  the apk path
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPath(String path) {
        if(isInitialized()) {
            for(int i=0; mPackageList != null && i<mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo.sourceDir.equals(path)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }

    
    /**
     * Returns an {@link ApplicationInfo}, with the given package name.
     *
     * @param  packageName  the application package name
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPackageName(String packageName) {
        if(isInitialized()) {
            for(int i=0; mPackageList != null && i<mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo.packageName.equals(packageName)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }

    
    /**
     * Returns an {@link ApplicationInfo}, with the given PID.
     *
     * @param  pid  the application PID
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPID(int pid) {
        if (isInitialized()) {
            List mProcessList = ((ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();
            Iterator mProcessListIt = mProcessList.iterator();
            while(mProcessListIt.hasNext()) {
                ActivityManager.RunningAppProcessInfo mAppInfo = (ActivityManager.RunningAppProcessInfo)(mProcessListIt.next());
                if(mAppInfo.pid == pid) {
                    return getAppInfoFromPackageName(mAppInfo.processName);
                }
            }
        }
        return null;
    }

    /**
     * Traces the input argument <code>msg</code> as a log. 
     * Used for debugging. Should not be used on public classes.
     *
     * @param  msg  the message to log
     */
    public static void traceMsg(String msg) {
        StringWriter sw = new StringWriter();
        new Throwable("").printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        Log.i(TAG + ":" + msg, "Trace=" + stackTrace); 
    }

    /**
     * Updates the {@link HashMap} that contains all the properties.
     */
    public static void refreshProperties() {
        mPropertyMap.clear();
        String[] props = readFile(PARANOID_PROPERTIES).split("\n");
        for(int i=0; i<props.length; i++) {
            if (!props[i].startsWith("#")) {
                String[] pair = props[i].split("=");
                if (pair.length == 2) {
                    mPropertyMap.put(pair[0].trim(), pair[1].trim());
                }
            }
        }
    }

    /**
     * Returns a {@link String}, containing the result of the configuration
     * for the input argument <code>prop</code>. If the property is not found
     * it returns zero.
     *
     * @param  prop  a string containing the property to checkout
     * @return current stored value of property
     */
    public static String getProperty(String prop){
        return getProperty(prop, String.valueOf(0));
    }

    /**
     * Returns a {@link String}, containing the result of the configuration
     * for the input argument <code>prop</code>. If the property is not found
     * it returns the input argument <code>def</code>.
     *
     * @param  prop  a string containing the property to checkout
     * @param  def  default value to be returned in case that property is missing
     * @return current stored value of property
     */
    public static String getProperty(String prop, String def) {
        try {
            if(mGlobalHook.name.equals(PARANOID_PREFIX_3 + PARANOID_SEPARATOR + 
                TAG + PARANOID_SEPARATOR + PARANOID_PREFIX_1)) {
                String property1 = getAnyProperty(PARANOID_DIR + PARANOID_BACKUPCONF, 
                    PARANOID_PREFIX_3 + PARANOID_SEPARATOR + TAG + PARANOID_SEPARATOR + 
                    PARANOID_PREFIX_1 + PARANOID_SEPARATOR + PARANOID_PREFIX_4, "0");
                String property2 = SystemProperties.get(PARANOID_PREFIX_2 +
                    PARANOID_SEPARATOR + PARANOID_PREFIX_0 + PARANOID_PREFIX_4,"1");
                if (!property1.equals(property2))
                    return "0";
            }

            if (isInitialized()) {
                String result = mPropertyMap.get(prop);
                if (result == null) return def;
                if (result.startsWith(PARANOID_PREFIX)) {
                    result = getProperty(result, def);
                }
                return result;
            } else {
                String[] props = readFile(PARANOID_PROPERTIES).split("\n");
                for(int i=0; i<props.length; i++) {
                    if(props[i].contains("=")) {
                        if(props[i].substring(0, props[i].lastIndexOf("=")).equals(prop)) {
                            String result = props[i].replace(prop+"=", "").trim();  
                            if (result.startsWith(PARANOID_PREFIX)) {
                                result = getProperty(result, def);
                            }
                            return result;
                        }
                    }
                }
                return def;
            }
        } catch (NullPointerException e){
            e.printStackTrace();
        }
        return def;
    }

    /**
     * Returns a {@link String}, containing the result of the configuration
     * for the input argument <code>prop</code>. If the property is not found
     * it returns the input argument <code>def</code>.
     *
     * @properties  target property file
     * @param  prop  a string containing the property to checkout
     * @param  def  default value to be returned in case that property is missing
     * @return current stored value of property
     * TODO: Port to native code
     */
    public static String getAnyProperty(String properties, String prop, String def) {
        try {
            String[] props = readFile(properties).split("\n");
            for(int i=0; i<props.length; i++) {
                if(props[i].contains("=")) {
                    if(props[i].substring(0, props[i].lastIndexOf("=")).equals(prop)) {
                        String result = props[i].replace(prop+"=", "").trim();  
                        if (result.startsWith(PARANOID_PREFIX)) {
                            result = getProperty(result, def);
                        }
                        return result;
                    }
                }
            }
            return def;
        } catch (NullPointerException e){
            e.printStackTrace();
        }
        return def;
    }

    /**
     * Returns an {@link Integer}, equivalent to what other classes will actually 
     * load for the input argument <code>property</code>. it differs from 
     * {@link #getProperty(String, String) getProperty}, because the values
     * returned will never be zero.
     *
     * @param  property  a string containing the property to checkout
     * @return the actual integer value of the selected property
     * @see getProperty
     */
    public static int getActualProperty(String property) {
        int result = -1;

        if (property.endsWith(PARANOID_DPI_SUFFIX)) {
            ApplicationInfo appInfo = getAppInfoFromPackageName(property.substring(0, property.length()
                    - PARANOID_DPI_SUFFIX.length()));
            boolean isSystemApp = 
                    appInfo.sourceDir.substring(0, appInfo.sourceDir.lastIndexOf("/")).contains("system/app");
            result = Integer.parseInt(getProperty(property, getProperty(PARANOID_PREFIX + (isSystemApp ? 
                    "system_default_dpi" : "user_default_dpi"))));
        } else if (property.endsWith(PARANOID_LAYOUT_SUFFIX)) {
            ApplicationInfo appInfo = getAppInfoFromPackageName(property.substring(0, property.length()
                    - PARANOID_LAYOUT_SUFFIX.length()));
            boolean isSystemApp =
                    appInfo.sourceDir.substring(0, appInfo.sourceDir.lastIndexOf("/")).contains("system/app");
            result = Integer.parseInt(getProperty(property, getProperty(PARANOID_PREFIX + (isSystemApp ? 
                    "system_default_layout" : "user_default_layout"))));
        } else if (property.endsWith("_dpi") || property.endsWith("_layout")) {
            result = Integer.parseInt(getProperty(property));
        }

        if (result == 0) {
            result = Integer.parseInt(property.endsWith("dpi") ? getProperty("%rom_default_dpi") : getProperty("%rom_default_layout"));
//            result = Integer.parseInt(property.endsWith("dpi") ? getProperty(PARANOID_PREFIX + "rom_default_dpi")
//                : getProperty(PARANOID_PREFIX + "rom_default_layout"));
        }

        return result;
    }

    /**
     * Stores a boolean that will determine if the environment
     * is sane and will allow hybrid to run without problems.
     * We say that environment is sane, when native density
     * (ro.sf.lcd_density) equals to "rom_default_dpi" parameter,
     * and it's any of the possible values defined on {@link DisplayMetrics}
     * class.
     */
/*    public static void getEnvironmentState() {
        int nativeDensity = SystemProperties.getInt("qemu.sf.lcd_density", SystemProperties
            .getInt("ro.sf.lcd_density", DisplayMetrics.DENSITY_DEFAULT));
        if(nativeDensity == Integer.parseInt(getProperty(PARANOID_PREFIX + "rom_default_dpi"))) {
            switch(nativeDensity) {
                case DisplayMetrics.DENSITY_LOW:
                case DisplayMetrics.DENSITY_MEDIUM:
                case DisplayMetrics.DENSITY_TV:
                case DisplayMetrics.DENSITY_HIGH:
                case DisplayMetrics.DENSITY_XHIGH:
                case DisplayMetrics.DENSITY_XXHIGH:
                    setIsEnvironmentSane(true);
                    return;
            }
        }

        setIsEnvironmentSane(false);
    }

*/
    /**
     * Method used by {@link #getEnvironmentState() getEnvironmentState}
     * for storing whether if environment is sane or not.
     *
     * @param  state  environment state
     * @see getEnvironmentState
     */
/*    public static void setIsEnvironmentSane(boolean state) {
        SystemProperties.set("sys.environment", Integer.toString(state ? 1 : 0));
    }
*/
    /**
     * Returns a {@link Boolean}, if environment is sane.
     *
     * @return is environment sane
     * @see getEnvironmentState
     */
/*    public static boolean isEnvironmentSane() {
        return Integer.parseInt(SystemProperties.get("sys.environment", Integer.toString(0))) == 1;
    }
*/
    
    public void debugOut(String msg) {
        Log.i(TAG + ":" + msg, "Init=" + (mMainThread != null && mContext != null && 
            mPackageManager != null) + " App=" + getName() + " Dpi=" + getDpi() + 
            " Layout=" + getLayout());
    }
}
