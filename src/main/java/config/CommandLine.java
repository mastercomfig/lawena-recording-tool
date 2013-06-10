
package config;

import java.nio.file.Path;
import java.util.List;

public interface CommandLine {

    public void generatePreview(String skyboxFilename);

    public boolean isTf2Running();

    public String getSystemDxLevel();

    public void setSystemDxLevel(String dxlevel);

    public String getSteamPath();

    public Process startTf(int width, int height, String dxlevel);

    public List<String> getVpkContents(Path tfpath, Path vpkpath);

}
