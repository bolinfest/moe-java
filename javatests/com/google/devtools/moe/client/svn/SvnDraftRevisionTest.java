// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.svn;

import java.io.File;

import junit.framework.TestCase;

/**
 * @author dbentley@google.com (Daniel Bentley)
 */
public class SvnDraftRevisionTest extends TestCase {

  public void testGetLocation() throws Exception {
    SvnDraftRevision r = new SvnDraftRevision(new File("/dummy/path"));
    assertEquals("/dummy/path", r.getLocation());
  }
}
