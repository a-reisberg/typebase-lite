// Your profile name of the sonatype account. The default is the same with the organization value
sonatypeProfileName := "a-reisberg"

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>typebaselite.shalloui.com</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/a-reisberg/typebase-lite/</connection>
      <developerConnection>scm:git:git@github.com:github.com/a-reisberg/typebase-lite/</developerConnection>
      <url>github.com/a-reisberg/typebase-lite/</url>
    </scm>
    <developers>
      <developer>
        <id>a-reisberg</id>
        <name>Alex Reisberg</name>
        <url>https://github.com/a-reisberg</url>
      </developer>
    </developers>
}