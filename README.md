moe-java
========

My fork of http://code.google.com/p/moe-java/
Created fork at revision http://code.google.com/p/moe-java/source/detail?r=31

This fork is created so that the scrubber from the old, Python MOE
(http://code.google.com/p/make-open-easy/) can be used with the new,
Java MOE (http://code.google.com/p/moe-java/).

Therefore, to use this version of MOE, you must checkout and build
Python MOE to get the scrubber. Follow the instructions at:

http://code.google.com/p/make-open-easy/wiki/InstallingMoe

But be sure to run the `make install` commands with `sudo`.

Suppose you check things out to:

    /opt/make-open-easy/moe/scrubber/scrubber.py
    
Then, when you use Java MOE, you must pass the absolute path in via a flag:

    java -Dmoe.scrubber=/opt/make-open-easy/moe/scrubber/scrubber.py \
        -jar build/jar/java-moe.jar "$@"
