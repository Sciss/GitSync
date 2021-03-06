lazy val root = project.in(file("."))
  .settings(
    name               := "GitSync",
    version            := "0.3.0",
    organization       := "de.sciss",
    scalaVersion       := "2.13.4",
    description        := "Scan directory for git repositories and report if they diverge from origin",
    homepage           := Some(url(s"https://github.com/Sciss/${name.value}")),
    licenses           := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
    libraryDependencies ++= Seq(
      "com.github.scopt"  %% "scopt"    % "4.0.0",
      "de.sciss"          %% "fileutil" % "1.1.5",
    ),
  )
