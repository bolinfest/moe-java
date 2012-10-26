// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.editors;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.Utils;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.project.EditorConfig;
import com.google.devtools.moe.client.project.ProjectContext;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A ScrubbingEditor invokes the MOE scrubber on a Codebase.
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class ScrubbingEditor implements Editor {

  private static final String ABSOLUTE_PATH_TO_SCRUBBER_DOT_PAR =
      "/devtools/moe/scrubber/scrubber.par";

  private static final boolean USE_DOT_SCRUBBER_PAR =
      new File(ABSOLUTE_PATH_TO_SCRUBBER_DOT_PAR).exists();

  /**
   * If set, must be an absolute path, and it must reference an executable.
   */
  @Nullable
  private static final String pathToScrubberDotPy = System.getProperty("moe.scrubber");

  /**
   * A {@code Supplier} that extracts the scrubber binary. We use a Supplier because we don't want
   * to extract the scrubber until it's needed. (A run of MOE may initialize a project context and
   * instantiate editors without actually editing.) It is memoized because we only need one copy of
   * the scrubber binary across MOE execution.
   */
  private static final Supplier<File> SCRUBBER_BINARY_SUPPLIER = Suppliers.memoize(
      new Supplier<File>() {
        @Override public File get() {
          try {
            // TODO(dbentley): what will this resource be under ant?
            File scrubberBinary =
                AppContext.RUN.fileSystem.getResourceAsFile(ABSOLUTE_PATH_TO_SCRUBBER_DOT_PAR);
            AppContext.RUN.fileSystem.setExecutable(scrubberBinary);
            return scrubberBinary;
          } catch (IOException ioEx) {
            AppContext.RUN.ui.error(ioEx, "Error extracting scrubber");
            throw new MoeProblem("Error extracting scrubber: " + ioEx.getMessage());
          }
        }
      });

  private String name;
  private JsonObject scrubberConfig;

  ScrubbingEditor(String editorName, JsonObject scrubberConfig) {
    name = editorName;
    this.scrubberConfig = scrubberConfig;
  }

  /**
   * Returns a description of what this editor will do.
   */
  @Override
  public String getDescription() {
    return name;
  }

  /**
   * Runs the Moe scrubber on the copied contents of the input Codebase and returns a new Codebase
   * with the results of the scrub.
   */
  @Override
  public Codebase edit(Codebase input, ProjectContext context, Map<String, String> options) {
    File tempDir = AppContext.RUN.fileSystem.getTemporaryDirectory("scrubber_run_");
    File outputTar = new File(tempDir, "scrubbed.tar");

    try {
      List<String> scrubberParams = ImmutableList.of(
          "--temp_dir", tempDir.getAbsolutePath(),
          "--output_tar", outputTar.getAbsolutePath(),
          // TODO(dbentley): allow configuring the scrubber config
          "--config_data", (scrubberConfig == null) ? "{}" : scrubberConfig.toString(),
          input.getPath().getAbsolutePath());
      if (USE_DOT_SCRUBBER_PAR) {
        AppContext.RUN.cmd.runCommand(
            ABSOLUTE_PATH_TO_SCRUBBER_DOT_PAR,
            scrubberParams,
            null /* workingDirectory */);
      } else {
        AppContext.RUN.cmd.runCommand(
            pathToScrubberDotPy,
            scrubberParams,
            null /* workingDirectory */);
      }
    } catch (CommandRunner.CommandException e) {
      throw new MoeProblem(e.getMessage());
    }
    File expandedDir = null;
    try {
      expandedDir = Utils.expandTar(outputTar);
    } catch (IOException e) {
      throw new MoeProblem(e.getMessage());
    } catch (CommandRunner.CommandException e) {
      throw new MoeProblem(e.getMessage());
    }
    return new Codebase(expandedDir, input.getProjectSpace(), input.getExpression());
  }

  public static ScrubbingEditor makeScrubbingEditor(String editorName, EditorConfig config) {
    return new ScrubbingEditor(editorName, config.getScrubberConfig());
  }
}
