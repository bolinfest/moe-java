// Copyright 2011 The MOE Authors All Rights Reserved.

package com.google.devtools.moe.client;

/**
 * A problem that we do not expect to routinely happen. They should end execution of MOE and require
 * intervention by moe-team.
 *
 * @author dbentley@google.com (Daniel Bentley)
 */
public class MoeProblem extends RuntimeException {

  public String explanation;

  public MoeProblem(String explanation) {
    super(explanation);
  }

  public MoeProblem(Throwable cause, String explanation) {
    super(explanation, cause);
  }

  public MoeProblem(Throwable cause, String explanationFormatString, Object... args) {
    super(String.format(explanationFormatString, args), cause);
  }

}
