// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.moe.client.CommandRunner.CommandException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilties (all pure functions!) to make writing MOE easier.
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class Utils {

  /**
   * Returns a Set that excludes strings matching any of excludeRes.
   */
  public static Set<String> filterByRegEx(Set<String> c, List<String> excludeRes) {
    return ImmutableSet.copyOf(Sets.filter(c, nonMatchingPredicateFromRes(excludeRes)));
  }

  /** @return a Predicate that's true iff a CharSequence doesn't match any of the given regexes */
  public static Predicate<CharSequence> nonMatchingPredicateFromRes(List<String> excludeRes) {
    ImmutableList.Builder<Predicate<CharSequence>> rePredicateBuilder = ImmutableList.builder();
    for (String excludeRe : excludeRes) {
      rePredicateBuilder.add(Predicates.not(Predicates.containsPattern(excludeRe)));
    }
    return Predicates.and(rePredicateBuilder.build());
  }

  public static void checkKeys(Map<String, String> options, Set<String> allowedOptions) {
    if (!allowedOptions.containsAll(options.keySet())) {
      throw new MoeProblem(
          String.format(
              "Options contains invalid keys:%nOptions: %s%nAllowed keys: %s",
              options, allowedOptions));
    }
  }

  public static Set<String> makeFilenamesRelative(Set<File> files, File basePath) {
    Set<String> result = Sets.newLinkedHashSet();
    for (File f : files) {
      if (!f.getAbsolutePath().startsWith(basePath.getAbsolutePath())) {
        throw new MoeProblem(
            String.format("File %s is under %s but does not begin with it", f, basePath));
      }
      result.add(f.getAbsolutePath().substring(basePath.getAbsolutePath().length() + 1));
    }
    return ImmutableSet.copyOf(result);
  }

  /** Applies the given Function to all files under baseDir. */
  public static void doToFiles(File baseDir, Function<File, Void> doFunction) {
    for (File file : AppContext.RUN.fileSystem.findFiles(baseDir)) {
      doFunction.apply(file);
    }
  }

  /** Delete files under baseDir whose paths relative to baseDir don't match the given Predicate. */
  public static void filterFiles(File baseDir, final Predicate<CharSequence> positiveFilter) {
    final URI baseUri = baseDir.toURI();
    Utils.doToFiles(baseDir, new Function<File, Void>() {
      @Override public Void apply(File file) {
        if (!positiveFilter.apply(baseUri.relativize(file.toURI()).getPath())) {
          try {
            AppContext.RUN.fileSystem.deleteRecursively(file);
          } catch (IOException e) {
            throw new MoeProblem("Error deleting file: " + file);
          }
        }
        return null;
      }
    });
  }

  /**
   * Expands the specified File to a new temporary directory, or returns null if the file
   * type is unsupported.
   * @param inputFile The File to be extracted.
   * @return File pointing to a directory, or null.
   * @throws CommandException
   * @throws IOException
   */
  public static File expandToDirectory(File inputFile) throws IOException, CommandException {
    // If the specified path already is a directory, return it without modification.
    if (inputFile.isDirectory()) {
      return inputFile;
    }

    // Determine the file type by looking at the file extension.
    String lowerName = inputFile.getName().toLowerCase();
    if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tar")) {
      return Utils.expandTar(inputFile);
    }

    // If this file extension is unknown, return null.
    return null;
  }

  public static File expandTar(File tar) throws IOException, CommandException {
    File expandedDir = AppContext.RUN.fileSystem.getTemporaryDirectory("expanded_tar_");
    AppContext.RUN.fileSystem.makeDirs(expandedDir);
    try {
      AppContext.RUN.cmd.runCommand(
          "tar",
          ImmutableList.of("-xf", tar.getAbsolutePath()),
          expandedDir.getAbsolutePath());
    } catch (CommandRunner.CommandException e) {
      AppContext.RUN.fileSystem.deleteRecursively(expandedDir);
      throw e;
    }
    return expandedDir;
  }

  public static void copyDirectory(File src, File dest)
      throws IOException, CommandException {
    if (src == null) {
      return;
    }
    AppContext.RUN.fileSystem.makeDirsForFile(dest);

    final Path fromPath = src.toPath();
    final Path toPath = dest.toPath();
    SimpleFileVisitor<Path> copyDirVisitor = new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
          throws IOException {
        Path targetPath = toPath.resolve(fromPath.relativize(dir));
        if (!java.nio.file.Files.exists(targetPath)) {
          java.nio.file.Files.createDirectory(targetPath);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path destPath = toPath.resolve(fromPath.relativize(file));
        java.nio.file.Files.copy(file, destPath, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    };
    java.nio.file.Files.walkFileTree(src.toPath(), copyDirVisitor);
  }

  /**
   * Generates a shell script with contents content
   *
   * @param content contents of the script
   * @param name  path for the script
   */
  public static void makeShellScript(String content, String name) {
    try {
      File script = new File(name);
      AppContext.RUN.fileSystem.write("#!/bin/sh -e\n" + content, script);
      AppContext.RUN.fileSystem.setExecutable(script);
    } catch (IOException e) {
      throw new MoeProblem("Could not generate shell script: " + e);
    }
  }
}
