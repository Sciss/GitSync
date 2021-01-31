# GitSync

[![Build Status](https://github.com/Sciss/GitSync/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/Sciss/GitSync/actions?query=workflow%3A%22Scala+CI%22)

## statement

GitSync is a small tool to compare local and remote git repositories.
It is (C)opyright 2017–2021 by Hanns Holger Rutz. All rights reserved. GitSync is released under 
the [GNU Lesser General Public License](https://raw.github.com/Sciss/GitSync/main/LICENSE) v2.1+ and comes with 
absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`.

## requirements / running

This project compiles against Scala 2.13 using sbt. It requires git 2.11 or newer being installed.
GitSync does not _alter_ your git repositories in any way (except for running `git remote update`),
it simply _lists_ things that are out of sync.

To get the options, simply type `sbt run`:

    Usage: GitSync [options]

      -d, --base-dir <value>   Base directory to scan for git repositories (required)
      -m, --max-depth <value>  Maximum depth of recursive directory scan (default: 2)
      -a, --ahead-only         Ignore local branches that are behind corresponding remote branches
      -b, --behind-only        Ignore local branches that are ahead corresponding remote branches
      -r, --ref-branches <name1>,<name2>...
                               Remote branches to compare against for local-only branches (default: main,work)
      -i, --list-ignored       Lists ignored files (except those given by `-x`)
      -x, --exclude-ignored <name1>,<name2>...
                               Specify files not included when using `-i`

So for example, to scan for all branches that are out of sync:

    sbt "run -d ~/Documents/devel"

The process may take a while, depending on the number of repositories found, the network speed, etc.
The output might look like this:

    AudioWidgets - Local branch 'foo' is behind
    GitSync - State is dirty
    LinuxConfig - Local branch 'main' is ahead
    LostTrack - Could not update remove refs
    Mellite - Local branch 'column' is ahead

In this example, the first line means, remote (origin) is ahead for the given branch.
The second line means there are untracked files or uncommitted changes (you can find out by running `git status` within that repository).
The third and fifth lines means local commits are ahead and haven't been pushed yet.
The fourth line means it was not possible to determine the remote state (possibly the URL is outdated).

My typical call for checking if a computer has source directories with crucial files not uploaded:

    sbt "run -d ~/Documents/devel -a -m 3 -i -x target,.idea,.idea_modules,.bsp,.bloop,_site"

Using `-i` here means, I'm able to find files such as `_creation` which I have put into `.gitignore` so they
do not end up in the public remote repository, but I may want to create a backup of them. Ignored files will be
listed like this:

    anemone_15b3c-video - ignored: conv_image_out
    
So the directory `conv_image_out` inside the repository `anemone_15b3c-video` contains stuff that I might need
to copy if I want to wipe that repository.

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)
