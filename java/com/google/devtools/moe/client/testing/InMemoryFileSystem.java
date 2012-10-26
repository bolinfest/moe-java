// Copyright 2012 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.Lifetimes;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * An in-memory {@link FileSystem} for use in testing.
 *
 */
public class InMemoryFileSystem implements FileSystem {

  private static final Splitter SEP_SPLITTER = Splitter.on(File.separator).omitEmptyStrings();
  private static final String TEMP_DIR_PREFIX = "/tmp/moe_".replace('/', File.separatorChar);

  /**
   * Mutable Map of absolute paths to file contents.
   *
   * Files and their contents are represented as, respectively, keys of String absolute paths, and
   * values of String contents. Paths/keys ending in {@link File#separator} are directories, and
   * their contents/values are ignored.
   */
  // NOTE(user): If/when needed, make this a map of paths to some metadata object with contents,
  // type (file/directory), executable bit, and anything else needed.
  private final Map<String, String> files = Maps.newTreeMap();

  private int tempDirCounter = 0;

  private final Map<File, Lifetime> tempDirLifetimes = Maps.newHashMap();


  /**
   * Constructs an {@code InMemoryFileSystem} that is initially empty.
   */
  public InMemoryFileSystem() {}

  /**
   * Constructs an {@code InMemoryFileSystem} with the given initial contents. Keys are paths,
   * values are contents. Paths/keys ending in {@link File#separator} are directories, and their
   * contents/values are ignored. Parent directories are automatically inferred and created.
   */
  public InMemoryFileSystem(Map<String, String> startingFiles) {
    for (Entry<String, String> entry : startingFiles.entrySet()) {
      files.putAll(getParentDirEntries(entry.getKey()));

      // Add a regular file's contents.
      if (!entry.getKey().endsWith(File.separator)) {
        files.put(entry.getKey(), entry.getValue());
      }
    }
  }


  /**
   * Returns mappings for the dir parts of a path. Examples:
   * <ul>
   * <li>"/a/b/c": returns mappings for "/a/" and "/a/b/"
   * <li>"/a/b/c/": returns mappings for "/a/" and "/a/b/" and "/a/b/c/"
   * </ul>
   */
  private static Map<String, String> getParentDirEntries(String path) {
    Map<String, String> parentDirs = Maps.newHashMap();
    String dirPath = path.substring(0, path.lastIndexOf(File.separator));
    String dirPathParts = File.separator;
    for (String dirName : SEP_SPLITTER.split(dirPath)) {
      dirPathParts += dirName + File.separator;
      parentDirs.put(dirPathParts, null);
    }
    return parentDirs;
  }

  @Override
  public File getTemporaryDirectory(String prefix) {
    return getTemporaryDirectory(prefix, Lifetimes.currentTask());
  }

  @Override
  public File getTemporaryDirectory(String prefix, Lifetime lifetime) {
    File tempDir = new File(TEMP_DIR_PREFIX + prefix + "_" + tempDirCounter);
    ++tempDirCounter;
    tempDirLifetimes.put(tempDir, lifetime);
    return tempDir;
  }

  @Override
  public void cleanUpTempDirs() {
    Iterator<Entry<File, Lifetime>> tempDirIterator = tempDirLifetimes.entrySet().iterator();
    while (tempDirIterator.hasNext()) {
      Entry<File, Lifetime> entry = tempDirIterator.next();
      if (entry.getValue().shouldCleanUp()) {
        deleteRecursively(entry.getKey());
        tempDirIterator.remove();
      }
    }
  }

  @Override
  public void setLifetime(File path, Lifetime lifetime) {
    // Testing may use a DummyRepository, which doesn't use temp dirs. So don't be stringent about
    // calls to this method for unknown dirs.
    if (tempDirLifetimes.containsKey(path)) {
      tempDirLifetimes.put(path, lifetime);
    }
  }

  @Override
  public Set<File> findFiles(File path) {
    checkExistentDirectory(path);
    String dirPrefix = path.getAbsolutePath() + File.separator;
    Set<File> foundFiles = Sets.newHashSet();
    for (String absFilename : files.keySet()) {
      if (absFilename.startsWith(dirPrefix) && isFile(new File(absFilename))) {
        foundFiles.add(new File(absFilename));
      }
    }
    return foundFiles;
  }

  @Override
  public File[] listFiles(File path) {
    checkExistentDirectory(path);
    String dirPrefix = path.getAbsolutePath() + File.separator;
    List<File> foundFiles = Lists.newArrayList();
    for (String absFilename : files.keySet()) {
      if (!absFilename.startsWith(dirPrefix)) {
        continue;
      }
      String subPath = absFilename.substring(dirPrefix.length());
      List<String> dirParts = ImmutableList.copyOf(SEP_SPLITTER.split(subPath));
      if (dirParts.size() == 1) {
        foundFiles.add(new File(absFilename));
      }
    }
    return foundFiles.toArray(new File[0]);
  }

  @Override
  public boolean exists(File f) {
    checkAbsolute(f);
    String absPath = f.getAbsolutePath();
    return files.containsKey(absPath) || files.containsKey(absPath + File.separator);
  }

  @Override
  public String getName(File f) {
    return f.getName();
  }

  @Override
  public boolean isFile(File f) {
    checkAbsolute(f);
    return files.containsKey(f.getAbsolutePath());
  }

  @Override
  public boolean isDirectory(File f) {
    checkAbsolute(f);
    return files.containsKey(f.getAbsolutePath() + File.separator);
  }

  @Override
  public boolean isReadable(File f) {
    return exists(f);
  }

  @Override
  public boolean isExecutable(File f) {
    // Assume everything is executable.
    return true;
  }

  @Override
  public void setExecutable(File f) {
    // Assume everything is executable.
  }

  @Override
  public void setNonExecutable(File f) {
    // Assume everything is executable.
    throw new UnsupportedOperationException();
  }

  @Override
  public void makeDirsForFile(File f) {
    checkAbsolute(f);
    files.putAll(getParentDirEntries(f.getAbsolutePath()));
  }

  @Override
  public void makeDirs(File f) {
    checkAbsolute(f);
    files.putAll(getParentDirEntries(f.getAbsolutePath() + File.separator));
  }

  @Override
  public void copyFile(File src, File dest) {
    checkExistentFile(src);
    checkNotAnExistentDirectory(dest);
    files.put(dest.getAbsolutePath(), files.get(src.getAbsolutePath()));
  }

  @Override
  public void write(String contents, File f) {
    checkNotAnExistentDirectory(f);
    makeDirsForFile(f);
    files.put(f.getAbsolutePath(), contents);
  }

  @Override
  public void deleteRecursively(File file) {
    checkExistent(file);
    if (isFile(file)) {
      files.remove(file.getAbsolutePath());
    } else {
      String dirPrefix = file.getAbsolutePath() + File.separator;
      Iterator<Entry<String, String>> filesIterator = files.entrySet().iterator();
      while (filesIterator.hasNext()) {
        Entry<String, String> nextFile = filesIterator.next();
        if (nextFile.getKey().startsWith(dirPrefix)) {
          filesIterator.remove();
        }
      }
    }
  }

  @Override
  public File getResourceAsFile(String resource) {
    File outFile = new File(
        getTemporaryDirectory("resource_extraction_", Lifetimes.moeExecution()),
        new File(resource).getName());
    files.put(outFile.getAbsolutePath(), resource);
    return outFile;
  }

  @Override
  public String fileToString(File f) {
    checkExistentFile(f);
    return files.get(f.getAbsolutePath());
  }


  private static void checkAbsolute(File file) {
    Preconditions.checkArgument(
        file.isAbsolute(),
        "An absolute path was expected: " + file.getAbsolutePath());
  }

  private void checkNotAnExistentDirectory(File file) {
    Preconditions.checkArgument(
        !exists(file) || isFile(file),
        "A non-existent or file path was expected: " + file.getAbsolutePath());
  }

  private void checkExistent(File file) {
    Preconditions.checkArgument(
        exists(file),
        "An existent path was expected: " + file.getAbsolutePath() + "; current files: " + files);
  }

  private void checkExistentFile(File file) {
    Preconditions.checkArgument(
        isFile(file),
        "An existent file was expected: " + file.getAbsolutePath() + "; current files: " + files);
  }

  private void checkExistentDirectory(File file) {
    Preconditions.checkArgument(
        isDirectory(file),
        "An existent dir was expected: " + file.getAbsolutePath() + "; current files: " + files);
  }
}
