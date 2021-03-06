// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client.directives;

import com.google.devtools.moe.client.MoeOptions;

/**
 * A Directive is what MOE should do in this run.
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public interface Directive {

  /**
   * Performs the Directive's work.
   *
   * @return the status of performing the Directive, suitable for returning from this process.
   */
  public int perform();

  /**
   * Gets the flags for this directive.
   * Will be auto-populated by args4j reflection.
   */
  public MoeOptions getFlags();
}
