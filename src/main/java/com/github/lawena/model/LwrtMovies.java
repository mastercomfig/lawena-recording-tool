package com.github.lawena.model;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.github.lawena.model.LwrtSettings.Key;

public class LwrtMovies {

  private LwrtSettings cfg;

  public LwrtMovies(LwrtSettings cfg) {
    this.cfg = cfg;
  }

  public void movieOffset() throws IOException {
    String lastmovie = "";
    String alias = "alias namescroll stmov1";
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(cfg.getMoviePath(), "*.tga")) {
      for (Path moviefile : stream) {
        String filename = moviefile.getFileName().toString();
        lastmovie = (lastmovie.compareTo(filename) > 0 ? lastmovie : filename);
      }
    }
    if (!lastmovie.equals("")) {
      int idx = "abcdefghijklmno".indexOf(lastmovie.charAt(0));
      if (idx >= 0) {
        alias = "alias namescroll stmov" + (idx + 2);
      } else if (lastmovie.charAt(0) == 'p') {
        alias = "alias namescroll noslots";
      }
    }
    Files.write(Paths.get("lwrt/tf/config/cfg/namescroll.cfg"), Arrays.asList(alias),
        Charset.forName("UTF-8"));
  }

  public void createMovienameCfgs() throws IOException {
    String[] prefixes =
        {"a1", "b2", "c3", "d4", "e5", "f6", "g7", "h8", "i9", "j10", "k11", "l12", "m13", "n14",
            "o15", "p16"};
    String video = cfg.getString(Key.SourceRecorderVideoFormat);
    String audio = cfg.getString(Key.SourceRecorderAudioFormat);
    int quality = cfg.getInt(Key.SourceRecorderJpegQuality);
    for (String prefix : prefixes) {
      String line =
          "startmovie \"" + cfg.getMoviePath() + "/" + prefix + "_\" " + video + " " + audio
              + (video.equals("jpg") ? " jpeg_quality " + quality : "");
      Files.write(Paths.get("lwrt/tf/config/cfg/mov/" + prefix + ".cfg"), Arrays.asList(line),
          Charset.forName("UTF-8"));
    }
  }
}