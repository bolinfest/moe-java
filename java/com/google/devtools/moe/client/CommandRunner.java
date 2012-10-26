// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public interface CommandRunner {

  public static class CommandException extends Exception {
    public final String cmd;
    public final List<String> args;
    public final String stdout;
    public final String stderr;
    public final int returnStatus;

    public CommandException(String cmd, List<String> args,
                            String stdout, String stderr, int returnStatus) {
      super(String.format(
          "Running %s with args %s returned %d with stdout %s and stderr %s",
          cmd, args, returnStatus, stdout, stderr));
      this.cmd = cmd;
      this.args = Collections.unmodifiableList(args);
      this.stdout = stdout;
      this.stderr = stderr;
      this.returnStatus = returnStatus;
    }
  }

  /**
   * Runs a command.
   *
   * @param cmd  the binary to invoke. If not a path, it will be resolved.
   * @param args  the arguments to pass to the binary
   * @param workingDirectory  the directory to run in
   *
   * @returns the output of the command
   * @throws CommandException
   *
   * TODO(dbentley): make it easier to do error-handling
   */
  String runCommand(String cmd, List<String> args, String workingDirectory)
      throws CommandException;

}
