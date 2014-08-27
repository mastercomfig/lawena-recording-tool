package com.github.lawena.os;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.lawena.model.LwrtSettings;

/**
 * This class lets you access to all values and actions that are OS-dependent, aiming to simplify
 * the porting of some features to all systems where TF2 can run (Windows, Linux and OSX)
 */
public abstract class OSInterface {

  private static final Logger log = LoggerFactory.getLogger(OSInterface.class);

  /**
   * Returns the necessary {@link ProcessBuilder} to launch TF2. It will be used when
   * {@link #startTf(int, int, String)} is called.
   * 
   * @return The <code>ProcessBuilder</code> used to create a {@link Process} and launch TF2 with it
   *         or <code>null</code> if it couldn't be created.
   */
  public abstract ProcessBuilder getBuilderStartTF2();

  /**
   * Returns the necessary {@link ProcessBuilder} to stop or kill the TF2 process, to abort its
   * execution. It will be used when {@link #killTf2Process()} is called.
   * 
   * @return The <code>ProcessBuilder</code> used to create a {@link Process} and kill the TF2
   *         process or <code>null</code> if it couldn't be created.
   */
  public abstract ProcessBuilder getBuilderTF2ProcessKiller();

  /**
   * Returns the necessary {@link ProcessBuilder} to generate a preview of a VTF file, in particular
   * in this tool, to generate a skybox preview. It will be used when
   * {@link #generatePreview(String)} is called.
   * 
   * @param skyboxFilename the filename of the skybox file to generate the preview
   * @return The <code>ProcessBuilder</code> used to create a {@link Process} and generate the
   *         skybox preview or <code>null</code> if it couldn't be created.
   */
  public abstract ProcessBuilder getBuilderVTFCmd(String skyboxFilename);

  /**
   * Returns the {@link Path} of the Steam installation (where Steam.exe, steam.sh or equivalent is
   * located)
   * 
   * @return The <code>Path</code> where Steam and TF2 main executable, can be located.
   */
  public abstract Path getSteamPath();

  /**
   * Returns the system DirectX level if it's stored somewhere in the filesystem.
   * 
   * @return a <code>String</code> representing the dxlevel value to use when launching TF2 or
   *         <code>null</code> if it couldn't be retrieved.
   */
  public abstract String getSystemDxLevel();

  /**
   * Checks if the TF2 process is running.
   * 
   * @return <code>true</code> if TF2 process still runs in the system or <code>false</code> if it
   *         does not.
   */
  public abstract boolean isRunningTF2();

  /**
   * Returns the {@link Path} where the VPK included with TF2 is located. This will be used to
   * extract skyboxes for preview generation and loading, and could be used for other features like
   * packing, extracting, listing, etc.
   * 
   * @param tfpath the path where the HL2 executable for TF2 is located
   * @return The <code>Path</code> to the VPK tool resolved from the tfpath.
   */
  public abstract Path resolveVpkToolPath(Path tfpath);

  /**
   * Store the set DirectX level in the filesystem for future use, like with the
   * {@link #getSystemDxLevel()} method. If the operation is not supported this should be
   * implemented as a no-op method or with a simple log message.
   * 
   * @param dxlevel the user-specified DirectX level
   */
  public abstract void setSystemDxLevel(String dxlevel);

  /**
   * Closes all open handles matching this path. This is useful to unlock files that some process
   * can hold preventing the restore/replace file mechanism to work correctly.
   * 
   * @param path the directory where handles are being closed
   */
  public abstract void closeHandles(Path path);

  /**
   * Deletes a file using OS-level commands.
   * 
   * @param path the path to delete
   */
  public abstract void delete(Path path);

  /**
   * Extracts VPK-packed files to a specified path only if they not exist in the destination path.
   * 
   * @param tfpath The {@link Path} where HL2 main executable for TF2 is located
   * @param vpkname The name of the VPK file to possibly extract the files
   * @param dest The <code>Path</code> where the files will be extracted to
   * @param files The filenames included in the VPK that might be extracted
   * @throws IOException If an error occurred while creating the destination folder
   * @deprecated VPK utility might crash so this method is not completely reliable.
   */
  @Deprecated
  public void extractIfNeeded(Path tfpath, String vpkname, Path dest, Iterable<String> files)
      throws IOException {
    List<String> fileList = new ArrayList<>();
    for (String file : files) {
      if (!Files.exists(dest.resolve(file))) {
        if (Files.exists(Paths.get(vpkname))) {
          if (!Files.exists(dest) || !Files.isDirectory(dest)) {
            Files.createDirectory(dest);
          }
          fileList.add(file);
        } else {
          log.info("Required file was not found: " + file);
        }
      }
    }
    if (!fileList.isEmpty()) {
      extractVpkFile(tfpath, vpkname, dest, fileList);
    }
  }

  private void extractVpkFile(Path tfpath, String vpkname, Path dest, List<String> files) {
    List<String> cmds = new ArrayList<>();
    try {
      Path vpktool = resolveVpkToolPath(tfpath);
      cmds.add(vpktool.toString());
      cmds.add("x");
      cmds.add(Paths.get(vpkname).toAbsolutePath().toString());
      cmds.addAll(files);
      ProcessBuilder pb = new ProcessBuilder(cmds);
      pb.directory(dest.toFile());
      Process pr = pb.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        log.debug("[vpk] " + line);
      }
      pr.waitFor();
    } catch (InterruptedException | IOException e) {
      log.warn("Problem extracting contents from VPK file", e);
    }
  }

  /**
   * Generate an preview image representing a specified skybox.
   * 
   * @param skyboxFilename The filename of the skybox to generate the preview
   * @see #getBuilderVTFCmd(String)
   */
  public void generatePreview(String skyboxFilename) {
    try {
      ProcessBuilder pb = getBuilderVTFCmd(skyboxFilename);
      log.debug("generatePreview: " + pb.command());
      Process pr = pb.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        log.debug("[vtfcmd] " + line);
      }
      pr.waitFor();
    } catch (InterruptedException | IOException e) {
      log.warn("Problem while generating png from vtf file", e);
    }
  }

  /**
   * List the files of a specified VPK file.
   * 
   * @param tfpath The {@link Path} where HL2 main executable for TF2 is located
   * @param vpkpath The <code>Path</code> where the VPK file to search is located
   * @return A {@link List} of <code>String</code>s of all the files inside the specified VPK file.
   */
  public List<String> getVpkContents(Path tfpath, Path vpkpath) {
    List<String> files = new ArrayList<>();
    try {
      Path vpktool = resolveVpkToolPath(tfpath);
      ProcessBuilder pb = new ProcessBuilder(vpktool.toString(), "l", vpkpath.toString());
      Process pr = pb.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        files.add(line);
      }
      pr.waitFor();
      log.trace("[" + vpkpath.getFileName() + "] Contents scanned: " + files.size() + " file(s)");
    } catch (InterruptedException | IOException e) {
      log.warn("Problem retrieving contents of VPK file", e);
    }
    return files;
  }

  /**
   * Stop or kill the TF2 process, whether it's being run from the tool or not.
   * 
   * @see #getBuilderTF2ProcessKiller()
   */
  public void killTf2Process() {
    try {
      ProcessBuilder pb = getBuilderTF2ProcessKiller();
      Process pr = pb.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        log.trace("[taskkill] " + line);
      }
      pr.waitFor();
    } catch (InterruptedException | IOException e) {
      log.warn("Problem stopping TF2 process", e);
    }
  }

  /**
   * Launch TF2 with some user-specified parameters.
   * 
   * @param cfg the program settings from it will retrieve values like the dxlevel and the
   *        resolution
   * @see #getBuilderStartTF2()
   */
  public void startTf(LwrtSettings cfg) {
    String dxlevel = cfg.getDxlevel();
    String width = cfg.getWidth() + "";
    String height = cfg.getHeight() + "";
    try {
      log.debug("Starting TF2 in " + width + "x" + height + " with dxlevel " + dxlevel);
      ProcessBuilder pb = getBuilderStartTF2();
      pb.command().addAll(
          Arrays.asList("-applaunch", "440", "-dxlevel", dxlevel, "-novid", "-noborder",
              "-noforcedmparms", "-noforcemaccel", "-noforcemspd", "-console", "-high", "-noipx",
              "-nojoy", "-sw", "-w", width, "-h", height));
      if (cfg.getInsecure()) {
        log.debug("Using -insecure in launch options");
        pb.command().add("-insecure");
      }
      if (cfg.getCondebug()) {
        pb.command().add("-condebug");
        Path logpath = cfg.getTfPath().resolve("console.log");
        log.info("TF2 console output saved to: " + logpath);
        try {
          Files.deleteIfExists(logpath);
        } catch (IOException e) {
          log.warn("Could not delete previous console.log file", e);
        }
      }
      Process pr = pb.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        log.trace("[steam] " + line);
      }
      pr.waitFor();
    } catch (InterruptedException | IOException e) {
      log.warn("Process was interrupted", e);
    }
  }

  /**
   * Set the Look & Feel of the Graphical User Interface of the tool. By default it uses the system
   * L&F.
   */
  public void setLookAndFeel() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      log.warn("Could not set the look and feel", e);
    }
  }

  /**
   * Use the Java Desktop API to open a folder of the given path.
   * 
   * @param path The folder that will be opened with the system file manager. If this is not a
   *        directory, it will open the file's parent.
   * @see Desktop#open(java.io.File)
   */
  public void openFolder(Path path) {
    Path parent = path.getParent();
    Path dir = Files.isDirectory(path) ? path : (parent != null ? parent : path);
    try {
      Desktop.getDesktop().open(dir.toFile());
    } catch (IOException e) {
      log.warn("Could not open directory: " + dir, e);
    }
  }
}