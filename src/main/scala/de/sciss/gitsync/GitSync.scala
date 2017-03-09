package de.sciss.gitsync

import de.sciss.file._
import scopt.OptionParser

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Try

object GitSync {
  case class Config(
                   baseDir      : File         = file("base-dir"),
                   maxDepth     : Int          = 2,
                   aheadOnly    : Boolean      = false,
                   behindOnly   : Boolean      = false,
                   mainBranches : Seq[String]  = Seq("master", "work")
                   )

  def main(args: Array[String]): Unit = {
    val default = Config()
    val parser = new OptionParser[Config]("GitSync") {
      opt[File]('d', "base-dir")
        .required()
        .text("Base directory to scan for git repositories (required)")
        .action { (v, c) => c.copy(baseDir = v) }
      opt[Int]('m', "max-depth")
        .text(s"Maximum depth of recursive directory scan (default: ${default.maxDepth})")
        .validate(i => if (i >= 0) Right(()) else Left("Must be >= 0"))
        .action { (v, c) => c.copy(maxDepth = v) }
      opt[Unit]('a', "ahead-only")
        .text("Ignore local branches that are behind corresponding remote branches")
        .action { (_, c) => c.copy(aheadOnly = true) }
      opt[Unit]('b', "behind-only")
        .text("Ignore local branches that are ahead corresponding remote branches")
        .action { (_, c) => c.copy(behindOnly = true) }
      opt[Seq[String]]('r', "ref-branches")
        .text(s"Remote branches to compare against for local-only branches (default: ${default.mainBranches.mkString(",")})")
        .valueName("<name1>,<name2>...")
        .action( (v, c) =>
        c.copy(mainBranches = v) )
    }
    // println(s"DETECTED ARGS: ${args.mkString("[", ", ", "]")}")
    parser.parse(args, default).fold(sys.exit(1))(run)
  }

  import sys.process._

  implicit class RichProcessBuilder[A](private val b: A)(implicit view: A => ProcessBuilder) {
    def !? : Try[String] = Try(b.!!)
  }

  def bail(message: String): Nothing = {
    Console.err.println(message)
    sys.exit(1)
  }

  // minimum acceptable git version
  val minGitMaj = 2
  val minGitMin = 11
  val git       = "git"

  def verifyGitVersion(): Unit = {
    val t = Seq(git, "--version").!?
    t.fold(_ => bail("git not found"), { s =>
      val p = "git version "

      def cannotDetermine(): Nothing = bail("Cannot determine git version")

      if (s.startsWith(p)) {
        val (maj, min) = Try {
          s.substring(p.length).trim.split('.').toList match {
            case majS :: minS :: _ => (majS.toInt, minS.toInt)
            case _ => cannotDetermine()
          }
        } .getOrElse(cannotDetermine())

        if (maj < minGitMaj || (maj == minGitMaj && min < minGitMin))
          bail(s"git version $maj.$min too old, requires at least $minGitMaj.$minGitMin")

      } else {
        cannotDetermine()
      }
    })
  }

  def run(config: Config): Unit = {
    verifyGitVersion()
    val base = file(config.baseDir.path.replaceFirst("^~", userHome.path))
    if (!base.isDirectory) {
      bail(s"Not a directory: $base")
    }
    val config1 = config.copy(baseDir = base)
    loop(config1, dir = base, depth = 0, seen = mutable.Set.empty)
  }

  def loop(config: Config, dir: File, depth: Int, seen: mutable.Set[File]): Unit = {
    if (!seen.add(dir)) return

    val isRoot = (dir / ".git").isDirectory
    if (isRoot) check(config, dir)
    else if (depth <= config.maxDepth) {
      val children = dir.children(_.isDirectory).sortBy(_.name.toLowerCase)
      children.foreach { sub =>
        loop(config, dir = sub, depth = depth + 1, seen = seen)
      }
    }
  }

  def relativize(parent: File, sub: File): File = {
    // Note: .getCanonicalFile will resolve symbolic links.
    // In order to support artifacts being symbolic links
    // inside a parent folder, we must not resolve them!

    val can     = sub   .absolute // .getCanonicalFile
    val base    = parent.absolute // .getCanonicalFile

    @tailrec def loop(res: File, leftOpt: Option[File]): File = {
      val left = leftOpt.getOrElse(
        throw new IllegalArgumentException(s"File $sub is not in a subdirectory of $parent")
      )

      if (left == base) res
      else loop(file(left.name) / res.path, left.parentOption)
    }

    val cf = loop(file(can.name), can.parentOption)
    cf
  }

  private val swallow = ProcessLogger(_ => ())

  /** This is the actual algorithm.
    *
    * We do the following:
    * `git remote update` to make sure we've fetched the remote changes
    * `git status --porcelain` should be empty, otherwise there is some dirty state
    * `git branch -vv --no-abbrev --no-color` lists all branches and their status, as
    * `"<bit> <name> <hash> <remote-info> <message>"`
    *
    * Where
    * - `<bit>` is either space (not current branch) or asterisk (current branch)
    * - `<remote-info>` is either empty (no remote branch exists), or `"[<remote-name>/<branch-name><status>]"`
    *   where status is either empty (branches are in sync), or it is `": ahead <num-commits>"` or
    *   `": behind <num-commits>"`
    *
    * @param dir  the git root directory
    */
  def check(config: Config, dir: File): Unit = {
    // inside the directory
    def runGit(cmd: String*): Try[String] = {
      Try(Process(Seq(git +: cmd: _*), dir).!!(swallow))
    }

    val ok = runGit("remote", "update").isSuccess

    def info(message: String): Unit = {
      val dirR = relativize(config.baseDir, dir)
      Console.out.println(s"$dirR - $message")
    }

    if (!ok) {
      info("Could not update remote refs.")
      return
    }

    val cleanTr = runGit("status", "--porcelain")
    cleanTr.fold(_ => info("Could not determine status."), { s =>
      if (s.trim.nonEmpty) info("State is dirty")
      else {
        val      listTr  = runGit("branch", "-vv", "--no-abbrev", "--no-color")
//        lazy val remotes = runGit("remote").fold[Seq[String]](
//          _ => { info("No remote repository found"); Nil },
//          s =>  s.split('\n').filter(_.nonEmpty)
//        )
        lazy val remotes = runGit("branch", "-r", "--no-color").fold[Seq[String]](
          _ => { info("No remote repository found"); Nil },
          s => {
            // all will look like
            // `Seq("origin/debug", "origin/master", "origin/plus_txn")`
            val all = s.split('\n').filter(ln => ln.nonEmpty && !ln.contains("->")).map(_.trim)
            all.filter { ref =>
              config.mainBranches.exists(ref.contains)
            }
          }
        )

//            val remotes =
//            remotes.flatMap { remote =>
//              // remoteBranch <- config.mainBranches
//              git branch -r --no-color
//            }


        listTr.fold(_ => info("Could not determine branches."), { listS =>
          listS.split('\n').filter(_.nonEmpty).foreach { ln =>
            // yeah, well, let's not getting into regex again
            val t1        = ln.substring(1).trim
            val i1        = t1.indexOf(' ')
            val branch    = t1.substring(0, i1)
            val t2        = t1.substring(i1).trim
            val i2        = t2.indexOf(' ')
            // val hash      = t2.substring(0, i2)
            val t3        = t2.substring(i2).trim
            val hasRemote = t3.nonEmpty && t3.charAt(0) == '['

            if (hasRemote) {
              val i3        = t3.indexOf(']')
              val t4        = t3.substring(1, i3)
              val diverges  = t4.contains(":")
              if (diverges) {
                val isAhead   = t4.contains(": ahead")
                val isBehind  = t4.contains(": behind")
                if (isAhead ) {
                  if (!config.behindOnly) info(s"Local branch '$branch' is ahead" )
                }
                else if (isBehind) {
                  if (!config.aheadOnly) info(s"Local branch '$branch' is behind")
                } else {
                  info(s"Cannot determine diversion for branch '$branch'")
                }
              }

            } else {
              remotes.exists { remoteRef =>
                val revList = s"$branch...$remoteRef"
                val warnedAhead = !config.behindOnly && {
                  val aheadTr = runGit("rev-list", "--left-only", revList)
                  aheadTr.fold(_ => { info("Cannot determine rev-list"); true }, revs => {
                    val res = revs.trim.nonEmpty
                    if (res) info(s"Local branch '$branch' is ahead" )
                    res
                  })
                }
                lazy val warnedBehind = !config.aheadOnly && {
                  val behindTr = runGit("rev-list", "--right-only", revList)
                  behindTr.fold(_ => { info("Cannot determine rev-list"); true }, revs => {
                    val res = revs.trim.nonEmpty
                    if (res) info(s"Local branch '$branch' is behind" )
                    res
                  })
                }
                warnedAhead || warnedBehind
              }
            }
          }
        })
      }
    })
  }
}