# GitSync

[![Flattr this](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=sciss&url=https%3A%2F%2Fgithub.com%2FSciss%2FGitSync&title=GitSync&language=Scala&tags=github&category=software)
[![Build Status](https://travis-ci.org/Sciss/GitSync.svg?branch=master)](https://travis-ci.org/Sciss/GitSync)

## statement

GitSync is a small library to compare local and remote git repositories.
It is (C)opyright 2017 by Hanns Holger Rutz. All rights reserved. GitSync is released under the [GNU Lesser General Public License](https://raw.github.com/Sciss/GitSync/master/LICENSE) v2.1+ and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

## requirements / running

This project compiles against Scala 2.12 using sbt 0.13.

To get the options, simply type `sbt run`:

    Usage: GitSync [options]

      -d, --base-dir <value>   Base directory to scan for git repositories (required)
      -m, --max-depth <value>  Maximum depth of recursive directory scan (default: 2)
      -a, --ahead-only         Ignore local branches that are behind corresponding remote branches
      -b, --behind-only        Ignore local branches that are ahead corresponding remote branches
      -r, --ref-branches <name1>,<name2>...
                               Remote branches to compare against for local-only branches (default: master,work)

So for example, to scan for all branches that are newer on local:

    sbt "run -a -d ~/Documents/devel"

The process may take a while, depending on the number of repositories found, the network speed, etc.
The output might look like this:

    GitSync - State is dirty
    LinuxConfig - Local branch 'master' is ahead
    Mellite - Local branch 'column' is ahead

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)