// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.devtools.moe.client.codebase;

import static org.easymock.EasyMock.expect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.AppContext;
import com.google.devtools.moe.client.CommandRunner;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.testing.AppContextForTesting;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import java.io.File;
import java.util.List;

/**
 * Unit tests for the CodebaseMerger class.
 *
 * Here is a diagram illustrating a merge situation. The test cases below will refer to this
 * diagram when explaining what type of case they are testing.
 *
 *                                                   _____
 *                                                  |     |
 *                                                  |  7  | (mod)
 *                                                  |_____|
 *                                                     |
 *                                                     |
 *                                                     |
 *                                                     |
 *                        ____                       __|__
 *                       |    |                     |     |
 *                (dest) |1006|=====================|  6  | (orig)
 *                       |____|                     |_____|
 *
 *                    internalrepo                 publicrepo
 *
 *
 */
public class CodebaseMergerTest extends TestCase {

  private IMocksControl control;
  private FileSystem fileSystem;
  private CommandRunner cmd;
  private Codebase orig, dest, mod;

  @Override
  public void setUp() {
    AppContextForTesting.initForTest();
    control = EasyMock.createControl();
    fileSystem = control.createMock(FileSystem.class);
    cmd = control.createMock(CommandRunner.class);
    AppContext.RUN.cmd = cmd;
    AppContext.RUN.fileSystem = fileSystem;

    orig = control.createMock(Codebase.class);
    dest = control.createMock(Codebase.class);
    mod = control.createMock(Codebase.class);
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in orig and mod but not dest.
   * In this case, the file is unchanged in orig and mod, so the file is not placed in the
   * merged codebase.
   */
  public void testGenerateMergedFileDestDelete() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true).times(2);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(false);
    destFile = new File("/dev/null");

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true).times(2);

    expect(fileSystem.isExecutable(origFile)).andReturn(false);
    expect(fileSystem.isExecutable(modFile)).andReturn(false);

    expect(cmd.runCommand("diff", ImmutableList.of("-N", origFile.getAbsolutePath(),
      modFile.getAbsolutePath()), "", "")).andReturn(null);

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in orig and dest but not mod.
   * The orig version and the dest version of the file differ. This should be treated as a conflict
   * for the user to resolve.
   */
  public void testGenerateMergedFileModDeleteConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true).times(2);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true).times(2);


    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(false);
    modFile = new File("/dev/null");

    expect(fileSystem.isExecutable(origFile)).andReturn(false);
    expect(fileSystem.isExecutable(destFile)).andReturn(false);

    List<String> diffArgs = ImmutableList.of("-N", origFile.getAbsolutePath(),
      destFile.getAbsolutePath());

    expect(cmd.runCommand("diff", diffArgs, "", "")).andThrow(
        new CommandRunner.CommandException("diff", diffArgs, "DIFFERENCE", "", 1));

    File failedToMergeFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(failedToMergeFile);
    fileSystem.copyFile(destFile, failedToMergeFile);

    List<String>mergeArgs = ImmutableList.of(failedToMergeFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andThrow(
        new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assert(merger.getFailedToMergeFiles().contains(failedToMergeFile.getAbsolutePath().toString()));
    assertEquals(0, merger.getMergedFiles().size());
  }

  /**
   * Test generateMergedFile(...) in the case when the file exists only in mod.
   * In this case, the file should simply be added to the merged codebase.
   */
  public void testGenerateMergedFileAddFile() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(false);
    destFile = new File("/dev/null");

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    expect(cmd.runCommand("merge", ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath()), "",
        mergedCodebaseLocation.getAbsolutePath())).andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assert(merger.getMergedFiles().contains(mergedFile.getAbsolutePath().toString()));
    assertEquals(0, merger.getFailedToMergeFiles().size());
  }

  /**
   * Test generateMergedFile(...) in the most ideal case where the file exists in all three
   * codebases and there is no conflict.
   */
  public void testGenerateMergedFileClean() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getFailedToMergeFiles().size());
    assert(merger.getMergedFiles().contains(mergedFile.getAbsolutePath().toString()));
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in all three codebases but
   * there is a conflict when merging.
   */
  public void testGenerateMergedFileConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andThrow(
        new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getMergedFiles().size());
    assert(merger.getFailedToMergeFiles().contains(mergedFile.getAbsolutePath().toString()));
  }

  /**
   * Test generateMergedFile(...) in the case where the file only exists in dest. The file should
   * appear in the merged codebase unchanged.
   */
  public void testGenerateMergedFileDestOnly() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(false);
    modFile = new File("/dev/null");

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getFailedToMergeFiles().size());
    assert(merger.getMergedFiles().contains(mergedFile.getAbsolutePath().toString()));
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in mod and dest but not orig.
   * In this case, the mod version and dest version are the same so there should be no conflict.
   * The file should remain unchanged in the merged codebase.
   */
  public void testGenerateMergedFileNoOrig() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getFailedToMergeFiles().size());
    assert(merger.getMergedFiles().contains(mergedFile.getAbsolutePath().toString()));
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in mod and dest but not orig.
   * In this case, the mod version and dest version are different so a conflict should occur when
   * merging so that the user can resolved the discrepancy.
   */
  public void testGenerateMergedFileNoOrigConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andThrow(
        new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getMergedFiles().size());
    assert(merger.getFailedToMergeFiles().contains(mergedFile.getAbsolutePath().toString()));
  }

  /**
   * Test for merge()
   */
  public void testMerge() throws Exception {
    Ui ui = control.createMock(Ui.class);
    AppContext.RUN.ui = ui;

    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    expect(dest.getRelativeFilenames()).andReturn(ImmutableSet.of("foo"));
    expect(mod.getRelativeFilenames()).andReturn(ImmutableSet.of("foo", "bar"));

    // generateMergedFile(...) on foo
    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs = ImmutableList.of(mergedFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andReturn("");

    // generateMergedFile(...) on bar
    origFile = new File("orig/bar");
    expect(orig.getFile("bar")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true).times(2);

    destFile = new File("dest/bar");
    expect(dest.getFile("bar")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true).times(2);

    modFile = new File("mod/bar");
    expect(mod.getFile("bar")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(false);
    modFile = new File("/dev/null");

    expect(fileSystem.isExecutable(origFile)).andReturn(true);
    expect(fileSystem.isExecutable(destFile)).andReturn(true);

    List<String> diffArgs = ImmutableList.of("-N", origFile.getAbsolutePath(),
        destFile.getAbsolutePath());

    expect(cmd.runCommand("diff", diffArgs, "", "")).andThrow(
        new CommandRunner.CommandException("diff", diffArgs, "DIFFERENCE", "", 1));

    File failedToMergeFile = new File("merged_codebase_7/bar");
    fileSystem.makeDirsForFile(failedToMergeFile);
    fileSystem.copyFile(destFile, failedToMergeFile);

    mergeArgs = ImmutableList.of(failedToMergeFile.getAbsolutePath(),
        origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, "",
        mergedCodebaseLocation.getAbsolutePath())).andThrow(
        new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    // Expect in call to report()
    ui.info(String.format("Merged codebase generated at: %s",
        mergedCodebaseLocation.getAbsolutePath()));
    ui.info(String.format("%d files merged successfully\n%d files have merge "
      + "conflicts. Edit the following files to resolve conflicts:\n%s", 1,
      1, ImmutableSet.of(failedToMergeFile.getAbsolutePath().toString()).toString()));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(orig, mod, dest);
    merger.merge();

    control.verify();

    assert(merger.getMergedFiles().contains(mergedFile.getAbsolutePath().toString()));
    assert(merger.getFailedToMergeFiles().contains(failedToMergeFile.getAbsolutePath().toString()));
  }
}
