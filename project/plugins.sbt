scalaVersion := "2.9.1"

//addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.2")

resolvers ++= Seq(
	Classpaths.typesafeResolver,
	"sbt-idea-repo" at "http://mpeltonen.github.com/maven/",
	"ansvia releases repo" at "http://scala.repo.ansvia.com/nexus/content/repositories/releases",
	Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
)

//addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse" % "1.5.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.2.0")

//addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6")

addSbtPlugin("com.github.siasia" % "xsbt-proguard-plugin" % "0.1.2")
