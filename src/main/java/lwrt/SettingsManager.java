
package lwrt;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsManager {

    private static final Logger log = Logger.getLogger("lawena");

    public enum Key {
        TfDir(""),
        MovieDir(""),
        Width(1280, 640, Integer.MAX_VALUE),
        Height(720, 360, Integer.MAX_VALUE),
        Framerate(120, 30, Integer.MAX_VALUE),
        DxLevel("98", "80", "81", "90", "95", "98"),
        Hud("medic", "killnotices", "medic", "full", "custom"),
        Skybox(""),
        ViewmodelSwitch("on", "on", "off", "default"),
        ViewmodelFov(70, 55, 90),
        MotionBlur(true),
        Crosshair(false),
        CrosshairSwitch(false),
        CombatText(false),
        Hitsounds(false),
        Voice(false),
        SteamCloud(false);

        private Object value;
        private List<String> allowedValues;
        private int min = Integer.MIN_VALUE;
        private int max = Integer.MAX_VALUE;

        private Key(String value) {
            this.value = value;
        }

        private Key(boolean value) {
            this.value = value;
        }

        private Key(int value, int min, int max) {
            this.value = value;
            this.min = min;
            this.max = max;
        }

        private Key(String value, String... validValues) {
            this(value);
            if (validValues != null) {
                this.allowedValues = Arrays.asList(validValues);
            }
        }

        @Override
        public String toString() {
            return name();
        }

        public boolean isValid(String str) {
            return allowedValues == null || allowedValues.contains(str);
        }

        public List<String> getAllowedValues() {
            return allowedValues;
        }

    }

    private String filename;
    private Properties properties;

    public SettingsManager(String settingsFile) {
        filename = settingsFile;
        properties = new Properties() {

            private static final long serialVersionUID = 1L;

            @Override
            public Set<Object> keySet()
            {
                return Collections.unmodifiableSet(new TreeSet<Object>(super.keySet()));
            }

        };
        for (Key key : Key.values()) {
            properties.setProperty(key.toString(), key.value + "");
        }
        try {
            properties.load(new FileReader(filename));
        } catch (FileNotFoundException e) {
            // do nothing, will load defaults
        } catch (IOException e) {
            log.log(Level.INFO, "Problem while loading settings, reverting to defaults", e);
        }
    }

    public void save() {
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(filename));
            properties.store(pw, "lawena settings");
        } catch (IOException e) {
            log.log(Level.INFO, "Settings could not be saved", e);
        }
    }

    public void saveToCfg() throws IOException {
        PrintWriter settings = new PrintWriter(new FileWriter("cfg/settings.cfg"));
        int framerate = getFramerate();
        boolean motionblur = getMotionBlur();
        boolean steamcloud = getSteamCloud();
        int viewmodelfov = getViewmodelFov();
        String viewmodelswitch = getViewmodelSwitch();
        boolean crosshairswitch = getCrosshairSwitch();
        boolean crosshair = getCrosshair();
        boolean combattext = getCombattext();
        boolean hitsounds = getHitsounds();
        boolean voice = getVoice();
        settings.println("alias recframerate host_framerate " + framerate);
        if (framerate < 60) {
            settings.println("alias currentfpsup 60fps");
            settings.println("alias currentfpsdn 3840fps");
        } else if (framerate == 60) {
            settings.println("alias currentfpsup 120fps");
            settings.println("alias currentfpsdn 3840fps");
        } else if (framerate < 120) {
            settings.println("alias currentfpsup 120fps");
            settings.println("alias currentfpsdn 60fps");
        } else if (framerate == 120) {
            settings.println("alias currentfpsup 240fps");
            settings.println("alias currentfpsdn 60fps");
        } else if (framerate < 240) {
            settings.println("alias currentfpsup 240fps");
            settings.println("alias currentfpsdn 120fps");
        } else if (framerate == 240) {
            settings.println("alias currentfpsup 480fps");
            settings.println("alias currentfpsdn 120fps");
        } else if (framerate < 480) {
            settings.println("alias currentfpsup 480fps");
            settings.println("alias currentfpsdn 240fps");
        } else if (framerate == 480) {
            settings.println("alias currentfpsup 960fps");
            settings.println("alias currentfpsdn 240fps");
        } else if (framerate < 960) {
            settings.println("alias currentfpsup 960fps");
            settings.println("alias currentfpsdn 480fps");
        } else if (framerate == 960) {
            settings.println("alias currentfpsup 1920fps");
            settings.println("alias currentfpsdn 480fps");
        } else if (framerate < 1920) {
            settings.println("alias currentfpsup 1920fps");
            settings.println("alias currentfpsdn 960fps");
        } else if (framerate == 1920) {
            settings.println("alias currentfpsup 3840fps");
            settings.println("alias currentfpsdn 960fps");
        } else if (framerate < 3840) {
            settings.println("alias currentfpsup 3840fps");
            settings.println("alias currentfpsdn 1920fps");
        } else if (framerate == 3840) {
            settings.println("alias currentfpsup 60fps");
            settings.println("alias currentfpsdn 1920fps");
        } else {
            settings.println("alias currentfpsup 60fps");
            settings.println("alias currentfpsdn 3840fps");
        }

        settings.println("mat_motion_blur_enabled " + (motionblur ? "1" : "0"));
        settings.println("mat_motion_blur_forward_enabled " + (motionblur ? "1" : "0"));
        settings.println("mat_motion_blur_strength " + (motionblur ? "1" : "0"));
        settings.println((steamcloud ? "//" : "") + "cl_cloud_settings 0");
        settings.println("viewmodel_fov_demo " + viewmodelfov);
        settings.println((viewmodelswitch.equals("off") ? "//" : "") + "lockviewmodelson");
        settings.println((viewmodelswitch.equals("on") ? "//" : "") + "lockviewmodelsoff");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshair_file \"\"");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshair_red 200");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshair_green 200");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshair_blue 200");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshair_scale 32");
        settings.println((crosshairswitch ? "//" : "") + "cl_crosshairalpha 200");
        settings.println((crosshairswitch ? "//" : "") + "lockcrosshair");
        settings.println("crosshair " + (crosshair ? "1" : "0"));
        settings.println("hud_combattext " + (combattext ? "1" : "0"));
        settings.println("hud_combattext_healing " + (combattext ? "1" : "0"));
        settings.println("tf_dingalingaling " + (hitsounds ? "1" : "0"));
        settings.println("voice_enable " + (voice ? "1" : "0"));
        settings.println("alias voice_enable \"\"");
        settings.println("cl_autorezoom 0");
        settings.println("hud_saytext_time 0");
        settings.println("net_graph 0");
        settings.println("alias net_graph \"\"");
        settings.println("alias voice_menu_1 \"\"");
        settings.println("alias voice_menu_2 \"\"");
        settings.println("alias voice_menu_3 \"\"");
        settings.println("cl_showfps 0");
        settings.println("alias cl_showfps \"\"");
        settings.println("volume 0.5");
        settings.println("hud_fastswitch 1");
        settings.println("cl_hud_minmode 1");
        settings.close();
    }

    private void setString(Key key, String value) {
        if (key.isValid(value)) {
            properties.setProperty(key.toString(), value);
        } else {
            throw new IllegalArgumentException(key + " does not allow value: " + value);
        }
    }

    private String getString(Key key) {
        return properties.getProperty(key.toString());
    }

    private void setInt(Key key, int value) {
        if (value >= key.min && value <= key.max) {
            properties.setProperty(key.toString(), value + "");
        } else {
            throw new IllegalArgumentException(key + " does not allow value: " + value);
        }
    }

    private int getInt(Key key) {
        String value = properties.getProperty(key.toString());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.info("Invalid numeric format in " + key + ": " + value + ", loading default value");
            return Integer.parseInt(key.value.toString());
        }
    }

    private void setBoolean(Key key, boolean value) {
        properties.setProperty(key.toString(), value + "");
    }

    private boolean getBoolean(Key key) {
        return Boolean.parseBoolean(properties.getProperty(key.toString()));
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setHeight(int value) {
        setInt(Key.Height, value);
    }

    public void setWidth(int value) {
        setInt(Key.Width, value);
    }

    public void setFramerate(int value) {
        setInt(Key.Framerate, value);
    }

    public void setHud(String value) {
        setString(Key.Hud, value);
    }

    public void setViewmodelFov(int value) {
        setInt(Key.ViewmodelFov, value);
    }

    public void setMotionBlur(boolean value) {
        setBoolean(Key.MotionBlur, value);
    }

    public void setViewmodelSwitch(String value) {
        setString(Key.ViewmodelSwitch, value);
    }

    public void setCrosshairSwitch(boolean value) {
        setBoolean(Key.CrosshairSwitch, value);
    }

    public void setCrosshair(boolean value) {
        setBoolean(Key.Crosshair, value);
    }

    public void setCombattext(boolean value) {
        setBoolean(Key.CombatText, value);
    }

    public void setHitsounds(boolean value) {
        setBoolean(Key.Hitsounds, value);
    }

    public void setVoice(boolean value) {
        setBoolean(Key.Voice, value);
    }

    public void setSteamCloud(boolean value) {
        setBoolean(Key.SteamCloud, value);
    }

    public void setDxlevel(String value) {
        setString(Key.DxLevel, value);
    }

    public void setTfPath(Path value) {
        setString(Key.TfDir, value.toString());
    }

    public void setMoviePath(Path value) {
        setString(Key.MovieDir, value.toString());
    }

    public void setSkybox(String value) {
        setString(Key.Skybox, value);
    }

    public int getHeight() {
        return getInt(Key.Height);
    }

    public int getWidth() {
        return getInt(Key.Width);
    }

    public int getFramerate() {
        return getInt(Key.Framerate);
    }

    public String getHud() {
        return getString(Key.Hud);
    }

    public int getViewmodelFov() {
        return getInt(Key.ViewmodelFov);
    }

    public boolean getMotionBlur() {
        return getBoolean(Key.MotionBlur);
    }

    public String getViewmodelSwitch() {
        return getString(Key.ViewmodelSwitch);
    }

    public boolean getCrosshairSwitch() {
        return getBoolean(Key.CrosshairSwitch);
    }

    public boolean getCrosshair() {
        return getBoolean(Key.Crosshair);
    }

    public boolean getCombattext() {
        return getBoolean(Key.CombatText);
    }

    public boolean getHitsounds() {
        return getBoolean(Key.Hitsounds);
    }

    public boolean getVoice() {
        return getBoolean(Key.Voice);
    }

    public boolean getSteamCloud() {
        return getBoolean(Key.SteamCloud);
    }

    public String getDxlevel() {
        return getString(Key.DxLevel);
    }

    public Path getTfPath() {
        String value = getString(Key.TfDir);
        return (value == null ? null : Paths.get(value));
    }

    public Path getMoviePath() {
        String value = getString(Key.MovieDir);
        return (value == null ? null : Paths.get(value));
    }

    public String getSkybox() {
        return getString(Key.Skybox);
    }
}