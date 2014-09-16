package com.github.lawena.app;

import static com.github.lawena.util.Util.toPath;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import com.github.lawena.app.model.MainModel;
import com.github.lawena.app.model.Resource;
import com.github.lawena.app.model.Resources;
import com.github.lawena.app.model.Settings;
import com.github.lawena.app.task.FileOpener;
import com.github.lawena.profile.Key;
import com.github.lawena.profile.ValuesValidator;
import com.github.lawena.ui.AboutDialog;
import com.github.lawena.ui.LaunchOptionsDialog;
import com.github.lawena.ui.LawenaView;
import com.github.lawena.ui.LogView;
import com.github.lawena.ui.SkyboxListRenderer;
import com.github.lawena.ui.TooltipRenderer;
import com.github.lawena.update.Build;
import com.github.lawena.update.Updater;
import com.github.lawena.util.LoggingAppender;
import com.github.lawena.util.StatusAppender;
import com.github.lawena.util.Util;
import com.github.lawena.vdm.DemoEditor;

public class Lawena {

  private static final Logger log = LoggerFactory.getLogger(Lawena.class);

  private MainModel model;
  private LawenaView view;

  private Settings settings;
  private DemoEditor demos;
  private Resources resources;

  private Tasks tasks;
  private Segments segments;
  private Branches branches;

  private LogView logView;

  private AboutDialog dialog;
  private JTextArea customSettingsTextArea;
  private JScrollPane customSettingsScrollPane;
  private Object lastHud;
  private LaunchOptionsDialog launchOptionsDialog;
  private LoggingAppender appender;

  public Lawena(MainModel mainModel) {
    model = mainModel;
  }

  public void start() {
    view = new LawenaView();
    logView = new LogView();
    tasks = new Tasks(this);
    segments = new Segments(this);
    branches = new Branches(this);

    settings = model.getSettings();
    demos = model.getDemos();
    resources = model.getResources();

    // setup ui loggers: log tab and status bar
    appender.setScroll(logView.getLogScroll());
    appender.setPane(logView.getLogPane());
    ch.qos.logback.classic.Logger statusLog =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("status");
    statusLog.setAdditive(false);
    statusLog.addAppender(new StatusAppender(view.getLblStatus(), statusLog.getLoggerContext()));

    model.getUpdater().fileCleanup();
    tasks.checkForUpdates();

    view.setTitle("Lawena Recording Tool " + model.getShortVersion());
    try {
      view.setIconImage(new ImageIcon(getClass().getResource("tf2.png")).getImage());
    } catch (Exception e) {
      log.warn("Window icon missing / could not be set");
    }
    view.addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        saveAndExit();
      }

    });
    view.getMntmAbout().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (dialog == null) {
          dialog = new AboutDialog(model.getFullVersion(), model.getBuildTime());
          dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
          dialog.setModalityType(ModalityType.APPLICATION_MODAL);
        }
        dialog.setVisible(true);
      }
    });
    view.getMntmAddCustomSettings().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        JTextArea custom = getCustomSettingsTextArea();
        String previous = custom.getText();
        int result =
            JOptionPane.showConfirmDialog(view, getCustomSettingsScrollPane(),
                "Configure Custom Settings", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
          log.info("Saving custom settings change: " + custom.getText());
          saveSettings();
        } else {
          custom.setText(previous);
        }
      }
    });
    view.getCheckForUpdatesMenuItem().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        tasks.checkForUpdates();
      }
    });
    view.getSwitchUpdaterBranchMenuItem().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        startBranchesDialog();
      }
    });
    view.getShowLogMenuItem().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        logView.setLocation(view.getX() + view.getWidth() + 5, view.getY());
        logView.setVisible(true);
      }
    });
    logView.getOpenLogButton().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        new FileOpener(Paths.get("logs", "lawena.log")).execute();
      }
    });
    logView.getCopyLogButton().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        logView.getLogPane().selectAll();
        logView.getLogPane().copy();
        logView.getLogPane().select(0, 0);
        log.info("Log contents copied to clipboard");
      }
    });
    logView.getLevelComboBox().setSelectedItem(appender.getLevel().toString());
    logView.getLevelComboBox().addItemListener(new ItemListener() {

      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          ch.qos.logback.classic.Logger rootLog =
              (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("root");
          Level level = Level.valueOf((String) e.getItem());
          log.info("Changing log level to {}", level);
          appender.setLevel(level);
          if (!Level.INFO.isGreaterOrEqual(level)) {
            rootLog.setLevel(Level.INFO);
          } else if (rootLog.getLevel().isGreaterOrEqual(level)) {
            rootLog.setLevel(level);
          }
        }
      }
    });

    final JTable table = view.getTableCustomContent();
    table.setModel(resources);
    table.getColumnModel().getColumn(0).setMaxWidth(20);
    table.getColumnModel().getColumn(2).setMaxWidth(50);
    table.setDefaultRenderer(Resource.class, new TooltipRenderer(settings));
    table.getModel().addTableModelListener(new TableModelListener() {

      @Override
      public void tableChanged(TableModelEvent e) {
        if (e.getColumn() == Resources.Column.ENABLED.ordinal()) {
          int row = e.getFirstRow();
          TableModel model = (TableModel) e.getSource();
          Resource cp = (Resource) model.getValueAt(row, Resources.Column.NAME.ordinal());
          checkCustomHud(cp);
        }
      }
    });
    TableRowSorter<Resources> sorter = new TableRowSorter<>(resources);
    table.setRowSorter(sorter);
    RowFilter<Resources, Object> filter = new RowFilter<Resources, Object>() {
      public boolean include(Entry<? extends Resources, ? extends Object> entry) {
        Resource resource = (Resource) entry.getValue(Resources.Column.NAME.ordinal());
        return !resources.isParentForcefullyLoaded(resource);
      }
    };
    sorter.setRowFilter(filter);
    tasks.scanResources();
    tasks.loadSkyboxes();

    loadSettings();

    view.getMntmChangeTfDirectory().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (tasks.getCurrentLaunchTask() == null) {
          Path newpath = validateGamePath(null);
          if (newpath != null) {
            Key.gamePath.setValueEx(settings, newpath.toString());
            tasks.scanResources();
          }
        } else {
          JOptionPane.showMessageDialog(view, "Please wait until TF2 has stopped running");
        }
      }
    });
    view.getMntmChangeMovieDirectory().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (tasks.getCurrentLaunchTask() == null) {
          Path newpath = validateRecordingPath(null);
          if (newpath != null) {
            Key.recordingPath.setValueEx(settings, newpath.toString());
          }
        } else {
          JOptionPane.showMessageDialog(view, "Please wait until TF2 has stopped running");
        }
      }
    });
    view.getMntmRevertToDefault().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        Path movies = toPath(Key.recordingPath.getValue(settings));
        settings.loadDefaultValues();
        Key.recordingPath.setValueEx(settings, movies.toString());
        loadSettings();
        resources.enableFromList(Key.resources.getValue(settings));
        loadHudComboState();
        saveSettings();
      }
    });
    view.getMntmExit().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        saveAndExit();
      }
    });
    view.getMntmSaveSettings().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        saveSettings();
      }
    });
    view.getBtnStartTf().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        tasks.launch();
      }
    });
    view.getBtnClearMovieFolder().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        startSegmentsDialog();
      }
    });
    view.getMntmOpenMovieFolder().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        tasks.openFile(new File(Key.recordingPath.getValue(settings)).getAbsoluteFile());
      }
    });
    view.getMntmOpenCustomFolder().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        Path data = settings.getParentDataPath();
        tasks.openFile(data.resolve("custom").toFile());
      }
    });
    view.getChckbxmntmInsecure().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        Key.insecure.setValueEx(settings, view.getChckbxmntmInsecure().isSelected());
      }
    });
    view.getMntmLaunchTimeout().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        Object answer =
            JOptionPane.showInputDialog(view, "Enter the number of seconds to wait\n"
                + "before interrupting TF2 launch.\n" + "Enter 0 to disable timeout.",
                "Launch Timeout", JOptionPane.PLAIN_MESSAGE, null, null,
                Key.launchTimeout.getValue(settings));
        if (answer != null) {
          try {
            int value = Integer.parseInt(answer.toString());
            Key.launchTimeout.setValueEx(settings, value);
          } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(view, "Invalid value, must be 0 or higher integer.",
                "Launch Options", JOptionPane.WARNING_MESSAGE);
          }
        }
      }
    });
    view.getCustomLaunchOptionsMenuItem().addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (launchOptionsDialog == null) {
          launchOptionsDialog = new LaunchOptionsDialog();
        }
        launchOptionsDialog.getOptionsTextField().setText(Key.launchOptions.getValue(settings));
        int result = launchOptionsDialog.showDialog();
        if (result == JOptionPane.YES_OPTION) {
          String launchOptions = launchOptionsDialog.getOptionsTextField().getText();
          Key.launchOptions.setValueEx(settings, launchOptions);
        } else if (result == 1) {
          Key.launchOptions.setDefaultValue(settings);
        }
      }
    });
    view.getCmbViewmodel().addItemListener(new ItemListener() {

      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          checkViewmodelState();
        }
      }
    });
    view.getCmbSourceVideoFormat().addItemListener(new ItemListener() {

      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          checkFrameFormatState();
        }
      }
    });

    view.getTabbedPane().addTab("VDM", null, demos.start());
    view.setVisible(true);

    new Timer().schedule(new TimerTask() {

      @Override
      public void run() {
        Path newGamePath = validateGamePath(toPath(Key.gamePath.getValue(settings)));
        Key.gamePath.setValue(settings, newGamePath.toString());
        Path newRecPath = validateRecordingPath(toPath(Key.recordingPath.getValue(settings)));
        Key.recordingPath.setValue(settings, newRecPath.toString());
        settings.save();
      }
    }, 1000);
  }

  private void checkViewmodelState() {
    boolean e = view.getCmbViewmodel().getSelectedIndex() != 1;
    view.getLblViewmodelFov().setEnabled(e);
    view.getSpinnerViewmodelFov().setEnabled(e);
  }

  private void checkFrameFormatState() {
    boolean e = view.getCmbSourceVideoFormat().getSelectedIndex() != 0;
    view.getLblJpegQuality().setEnabled(e);
    view.getSpinnerJpegQuality().setEnabled(e);
  }

  private JTextArea getCustomSettingsTextArea() {
    if (customSettingsTextArea == null) {
      customSettingsTextArea = new JTextArea(10, 40);
      customSettingsTextArea.setEditable(true);
      customSettingsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    }
    return customSettingsTextArea;
  }

  private JScrollPane getCustomSettingsScrollPane() {
    if (customSettingsScrollPane == null) {
      customSettingsScrollPane = new JScrollPane(getCustomSettingsTextArea());
    }
    return customSettingsScrollPane;
  }

  private void startSegmentsDialog() {
    segments.start();
  }

  private void startBranchesDialog() {
    branches.start();
  }

  private boolean checkCustomHud(Resource resource) {
    Set<String> set = resource.getTags();
    if (resource.isEnabled()) {
      if (set.contains(Resource.HUD)) {
        lastHud = view.getCmbHud().getSelectedItem();
        view.getCmbHud().setSelectedItem("Custom");
        view.getCmbHud().setEnabled(false);
        return true;
      }
    } else {
      if (set.contains(Resource.HUD)) {
        if (lastHud != null) {
          view.getCmbHud().setSelectedItem(lastHud);
        }
        view.getCmbHud().setEnabled(true);
        return false;
      }
    }
    return false;
  }

  void loadHudComboState() {
    boolean detected = false;
    for (Resource cp : resources.getResourceList()) {
      if (detected) {
        break;
      }
      detected = checkCustomHud(cp);
    }
  }

  private void loadSettings() {
    Util.registerValidation(view.getCmbResolution(), "[1-9][0-9]*x[1-9][0-9]*",
        view.getLblResolution());
    Util.registerValidation(view.getCmbFramerate(), "[1-9][0-9]*", view.getLblFrameRate());
    Util.selectComboItem(view.getCmbHud(), Key.hud.getValue(settings),
        ((ValuesValidator) Key.hud.getValidator()).getAllowedValues());
    Util.selectComboItem(view.getCmbQuality(), Key.dxlevel.getValue(settings),
        ((ValuesValidator) Key.dxlevel.getValidator()).getAllowedValues());
    Util.selectComboItem(view.getCmbViewmodel(), Key.viewmodelSwitch.getValue(settings),
        ((ValuesValidator) Key.viewmodelSwitch.getValidator()).getAllowedValues());
    selectSkyboxFromSettings();
    view.getCmbResolution().setSelectedItem(
        Key.width.getValue(settings).intValue() + "x" + Key.height.getValue(settings).intValue());
    view.getCmbFramerate().setSelectedItem(Key.framerate.getValue(settings).intValue() + "");
    try {
      view.getSpinnerViewmodelFov().setValue(Key.viewmodelFov.getValue(settings));
    } catch (IllegalArgumentException e) {
    }
    view.getEnableMotionBlur().setSelected(Key.motionBlur.getValue(settings));
    view.getDisableCombatText().setSelected(Key.noDamageNumbers.getValue(settings));
    view.getDisableCrosshair().setSelected(Key.noCrosshair.getValue(settings));
    view.getDisableCrosshairSwitch().setSelected(Key.noCrosshairSwitch.getValue(settings));
    view.getDisableHitSounds().setSelected(Key.noHitsounds.getValue(settings));
    view.getDisableVoiceChat().setSelected(Key.noVoice.getValue(settings));
    view.getUseHudMinmode().setSelected(Key.hudMinmode.getValue(settings));
    view.getChckbxmntmInsecure().setSelected(Key.insecure.getValue(settings));
    view.getChckbxmntmBackupMode().setSelected(Key.deleteUnneededBackups.getValue(settings));
    view.getUsePlayerModel().setSelected(Key.hudPlayerModel.getValue(settings));
    getCustomSettingsTextArea().setText(Key.extConVars.getValue(settings));
    view.getCmbSourceVideoFormat().setSelectedItem(Key.recorderVideoFormat.getValue(settings));
    view.getSpinnerJpegQuality().setValue(Key.recorderJpegQuality.getValue(settings));
    logView.getLevelComboBox().setSelectedItem(Key.loglevel.getValue(settings));
    checkViewmodelState();
    checkFrameFormatState();
  }

  public void saveSettings() {
    String[] resolution = ((String) view.getCmbResolution().getSelectedItem()).split("x");
    if (resolution.length == 2) {
      Key.width.setValueEx(settings, Integer.parseInt(resolution[0]));
      Key.height.setValueEx(settings, Integer.parseInt(resolution[1]));
    } else {
      log.warn("Bad resolution format, reverting to previously saved");
      view.getCmbResolution().setSelectedItem(
          Key.width.getValue(settings) + "x" + Key.height.getValue(settings));
    }
    Key.hud.setValueEx(
        settings,
        ((ValuesValidator) Key.hud.getValidator()).getAllowedValues().get(
            view.getCmbHud().getSelectedIndex()));
    Key.viewmodelSwitch.setValueEx(settings, ((ValuesValidator) Key.viewmodelSwitch.getValidator())
        .getAllowedValues().get(view.getCmbViewmodel().getSelectedIndex()));
    Key.dxlevel.setValueEx(settings, ((ValuesValidator) Key.dxlevel.getValidator())
        .getAllowedValues().get(view.getCmbQuality().getSelectedIndex()));
    Key.viewmodelFov.setValueEx(settings, (int) view.getSpinnerViewmodelFov().getValue());
    Key.motionBlur.setValueEx(settings, view.getEnableMotionBlur().isSelected());
    Key.noDamageNumbers.setValueEx(settings, view.getDisableCombatText().isSelected());
    Key.noCrosshair.setValueEx(settings, view.getDisableCrosshair().isSelected());
    Key.noCrosshairSwitch.setValueEx(settings, view.getDisableCrosshairSwitch().isSelected());
    Key.noHitsounds.setValueEx(settings, view.getDisableHitSounds().isSelected());
    Key.noVoice.setValueEx(settings, view.getDisableVoiceChat().isSelected());
    Key.skybox.setValueEx(settings, (String) view.getCmbSkybox().getSelectedItem());
    Key.resources.setValueEx(settings, resources.getEnabledStringList());
    Key.hudMinmode.setValueEx(settings, view.getUseHudMinmode().isSelected());
    Key.insecure.setValueEx(settings, view.getChckbxmntmInsecure().isSelected());
    Key.deleteUnneededBackups.setValueEx(settings, view.getChckbxmntmBackupMode().isSelected());
    Key.hudPlayerModel.setValueEx(settings, view.getUsePlayerModel().isSelected());
    Key.extConVars.setValueEx(settings, getCustomSettingsTextArea().getText());
    Key.recorderVideoFormat.setValueEx(settings, view.getCmbSourceVideoFormat().getSelectedItem()
        .toString());
    Key.recorderJpegQuality.setValueEx(settings, (int) view.getSpinnerJpegQuality().getValue());
    Key.loglevel.setValueEx(settings, (String) logView.getLevelComboBox().getSelectedItem());
    settings.save();
    log.info("Settings saved");
  }

  void saveAndExit() {
    saveSettings();
    view.setVisible(false);
    if (!model.getOsInterface().isRunningTF2()) {
      model.getLinker().unlink();
    }
    System.exit(0);
  }

  void configureSkyboxes(final JComboBox<String> combo) {
    final Vector<String> data = new Vector<>();
    Path dir = Paths.get("lwrt/tf/skybox/vtf");
    if (Files.exists(dir)) {
      log.info("Loading skybox folder");
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*up.vtf")) {
        for (Path path : stream) {
          log.trace("Skybox found at: " + path);
          String skybox = path.toFile().getName();
          skybox = skybox.substring(0, skybox.indexOf("up.vtf"));
          data.add(skybox);
        }
      } catch (IOException e) {
        log.warn("Problem while loading skybox folder", e);
      }
    }
    tasks.generateSkyboxPreviews(new ArrayList<>(data));
    data.add(0, Key.skybox.getDefaultValue());
    combo.setModel(new DefaultComboBoxModel<String>(data));
    combo.setRenderer(new SkyboxListRenderer(model.getSkyboxPreviewStore().getMap()));
  }

  public void selectSkyboxFromSettings() {
    view.getCmbSkybox().setSelectedItem(Key.skybox.getValue(settings));
  }

  MainModel getModel() {
    return model;
  }

  LawenaView getView() {
    return view;
  }

  void clearSegmentFiles(List<String> selected) {
    tasks.cleanSegments(selected);
  }

  public void upgrade(Build details) {
    if (details.equals(Build.LATEST)) {
      String notice = "Update is ready. Please restart Lawena to use it";
      log.info(notice);
      JOptionPane.showMessageDialog(view, notice, "Update Ready", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Updater updater = model.getUpdater();
    if (updater.upgradeApplication(details)) {
      log.info("Upgrade in progress..");
      saveAndExit();
    } else {
      log.debug("Attempting to update version marker file again");
      if (updater.createVersionFile(details.getName())) {
        String notice = "Update is ready. Please restart Lawena to use it";
        log.info(notice);
        JOptionPane
            .showMessageDialog(view, notice, "Update Ready", JOptionPane.INFORMATION_MESSAGE);
      } else {
        String notice = "Update could not be completed, please report this issue";
        log.info(notice);
        JOptionPane.showMessageDialog(view, notice, "Updater Error", JOptionPane.WARNING_MESSAGE);
      }
    }
  }

  public LoggingAppender getAppender() {
    return appender;
  }

  public void setAppender(LoggingAppender appender) {
    this.appender = appender;
  }

  public Path validateRecordingPath(Path initial) {
    Path steamPath = model.getOsInterface().getSteamPath();
    Path selected = initial;
    int ret = 0;
    while ((selected == null && ret == 0)
        || (selected != null && (!Files.exists(selected) || Paths.get("").equals(selected)))) {
      log.debug("Validating current recording folder: {}",
          (selected != null ? selected.toAbsolutePath() : "<None>"));
      String dir =
          model.getOsInterface()
              .chooseSingleFolder(view, "Choose a directory to store your movie files",
                  steamPath.toAbsolutePath().toString());
      if (dir != null) {
        selected = Paths.get(dir);
      } else {
        ret = 1;
        selected = null;
      }
      log.debug("Selected recording folder: " + selected);
    }
    return selected;
  }

  public Path validateGamePath(Path initial) {
    Path steamPath = model.getOsInterface().getSteamPath();
    Path selected = initial;
    int ret = 0;
    String dirName = Key.gameFolderName.getValue(settings);
    while ((selected == null && ret == 0)
        || (selected != null && (!Files.exists(selected) || !selected.toAbsolutePath().toFile()
            .getName().toString().equals(dirName)))) {
      log.debug("Validating current game folder: {}", (selected != null ? selected.toAbsolutePath()
          : "<None>"));
      String dir =
          model.getOsInterface().chooseSingleFolder(view,
              "Choose your \"" + dirName + "\" directory", steamPath.toAbsolutePath().toString());
      if (dir != null) {
        selected = Paths.get(dir);
      } else {
        ret = 1;
        selected = null;
      }
      log.debug("Selected game folder: " + selected);
    }
    return selected;
  }
}
