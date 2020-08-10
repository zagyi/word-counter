version := "1.0.0"
isSnapshot := true
scalaVersion := "2.13.3"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val circeVersion     = "0.12.3"
lazy val quicklensVersion = "1.6.0"
lazy val sttpVersion      = "2.2.3"
lazy val zioVersion       = "1.0.0-RC21-2"

lazy val deps = Seq(
  "ch.qos.logback"               % "logback-classic"                % "1.2.3",
  "com.softwaremill.quicklens"   %% "quicklens"                     % quicklensVersion,
  "com.softwaremill.quicklens"   %% "quicklens"                     % quicklensVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % sttpVersion,
  "com.softwaremill.sttp.client" %% "core"                          % sttpVersion,
  "dev.zio"                      %% "zio"                           % zioVersion,
  "dev.zio"                      %% "zio-streams"                   % zioVersion,
  "dev.zio"                      %% "zio-test"                      % zioVersion % "test",
  "dev.zio"                      %% "zio-test-sbt"                  % zioVersion % "test",
  "io.circe"                     %% "circe-core"                    % circeVersion,
  "io.circe"                     %% "circe-generic"                 % circeVersion,
  "io.circe"                     %% "circe-parser"                  % circeVersion,
  "org.polynote"                 %% "uzhttp"                        % "0.2.4",
  "org.typelevel"                % "cats-core_2.13"                 % "2.1.1"
)

lazy val root = (project in file("."))
  .settings(
    name := "word-count-app",
    organization := "com.github.zagyi",
    parallelExecution in Test := false,
    scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
      "-Yrangepos",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused:params,-implicits",
      "-Ywarn-value-discard",
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-explaintypes",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-unchecked"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
    libraryDependencies ++= deps
  )

parallelExecution in Test := false
