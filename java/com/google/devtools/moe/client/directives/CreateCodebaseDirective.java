// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.directives;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner.CommandException;
import com.google.devtools.moe.client.MoeOptions;
import com.google.devtools.moe.client.Ui.Task;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.codebase.CodebaseCreationError;
import com.google.devtools.moe.client.parser.Parser;
import com.google.devtools.moe.client.parser.Parser.ParseError;
import com.google.devtools.moe.client.project.InvalidProject;
import com.google.devtools.moe.client.project.ProjectContext;

import org.kohsuke.args4j.Option;

/**
 * Print the head revision of a repository.
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class CreateCodebaseDirective implements Directive {

  private final CreateCodebaseOptions options = new CreateCodebaseOptions();

  public CreateCodebaseDirective() {}

  @Override
  public CreateCodebaseOptions getFlags() {
    return options;
  }

  @Override
  public int perform() {
    ProjectContext context;
    try {
      context = AppContext.RUN.contextFactory.makeProjectContext(options.configFilename);
    } catch (InvalidProject e) {
      AppContext.RUN.ui.error(e, "Error creating project");
      return 1;
    }

    Task createCodebaseTask =
        AppContext.RUN.ui.pushTask("create_codebase", "Creating codebase " + options.codebase);
    Codebase c;
    try {
      c = Parser.parseExpression(options.codebase).createCodebase(context);
    } catch (ParseError e) {
      AppContext.RUN.ui.error(e, "Error creating codebase");
      return 1;
    } catch (CodebaseCreationError e) {
      AppContext.RUN.ui.error(e, "Error creating codebase");
      return 1;
    }
    AppContext.RUN.ui.info(
        String.format("Codebase \"%s\" created at %s", c.toString(), c.getPath()));

    try {
      maybeWriteTar(c);
    } catch (CommandException e) {
      AppContext.RUN.ui.error(e, "Error creating codebase tarfile");
      return 1;
    }

    AppContext.RUN.ui.popTaskAndPersist(createCodebaseTask, c.getPath());
    return 0;
  }

  /**
   * If the user specified --tarfile, then tar up the codebase at the specified location.
   * @throws CommandException 
   */
  private void maybeWriteTar(Codebase codebase)
      throws CommandException {
    Preconditions.checkNotNull(codebase);
    String tarfilePath = options.tarfile;
    if (Strings.isNullOrEmpty(tarfilePath)) {
      return;
    }

    AppContext.RUN.cmd.runCommand(
        "tar",
        ImmutableList.of("-c", "-f", tarfilePath, "."),
        codebase.getPath().getAbsolutePath());
    AppContext.RUN.ui.info(
        String.format("tar of codebase \"%s\" created at %s", codebase.toString(), tarfilePath));
  }

  static class CreateCodebaseOptions extends MoeOptions {
    @Option(name = "--config_file", required = true,
            usage = "Location of MOE config file")
    String configFilename = "";
    @Option(name = "--codebase", required = true,
            usage = "Codebase expression to evaluate")
    String codebase = "";
    @Option(name = "--tarfile", required = false,
            usage = "Path where tarfile of the resulting codebase should be written")
    String tarfile = null;
  }

}
