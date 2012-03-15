addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.2")

resolvers ++= Seq(
	Classpaths.typesafeResolver,
	"sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
)

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse" % "1.5.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")
