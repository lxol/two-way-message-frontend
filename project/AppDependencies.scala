import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    play.sbt.PlayImport.ws,
    "com.google.inject" % "guice" % "4.2.2",
    "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "5.3.0",
    "uk.gov.hmrc" %% "auth-client" % "2.29.0-play-25",
    "uk.gov.hmrc" %% "play-health" % "3.14.0-play-25",
    "uk.gov.hmrc" %% "play-ui" % "8.2.0-play-25",
    "uk.gov.hmrc" %% "http-caching-client" % "9.0.0-play-25",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "0.2.0",
    "uk.gov.hmrc" %% "bootstrap-play-25" % "5.1.0",
    "uk.gov.hmrc" %% "play-language" % "3.4.0",
    "uk.gov.hmrc" %% "play-whitelist-filter" % "2.0.0",
    "net.codingwell" %% "scala-guice" % "4.2.2",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-25",
    "uk.gov.hmrc" %% "url-builder" % "3.3.0-play-25"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25",
    "org.scalatest" %% "scalatest" % "3.0.5",
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1",
    "org.pegdown" % "pegdown" % "1.6.0",
    "org.jsoup" % "jsoup" % "1.10.3",
    "com.typesafe.play" %% "play-test" % PlayVersion.current,
    "org.mockito" % "mockito-core" % "2.23.4",
    "org.scalacheck" %% "scalacheck" % "1.13.4",
    "org.scalactic" %% "scalactic" % "3.0.5"
  )

  def apply() = compile ++ test
}
