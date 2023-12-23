/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  `java-library`
  `maven-publish`
  signing
  id("com.github.ben-manes.versions") version "0.39.0"
}

allprojects {

  apply(plugin = "java")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")

  version = "0.9.0"
  group = "io.reactiverse"

  extra["vertxVersion"] = "4.3.6"
  extra["opensearchClientVersion"] = "2.4.1"
  extra["mutinyBindingsVersion"] = "2.29.0"

  extra["assertjVersion"] = "3.23.1"
  extra["tcVersion"] = "1.17.6"
  extra["opensearchTCVersion"] = "2.0.0"
  extra["junitVersion"] = "5.9.0"
  extra["logbackVersion"] = "1.2.3"
  extra["javaParserVersion"] = "3.9.1"

  extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

  if (!project.hasProperty("ossrhUsername")) {
    extra["ossrhUsername"] = "foo"
  }

  if (!project.hasProperty("ossrhPassword")) {
    extra["ossrhPassword"] = "bar"
  }

  repositories {
    mavenCentral()
    maven {
      url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    mavenLocal()
  }

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

val vertxVersion = extra["vertxVersion"]
val opensearchClientVersion = extra["opensearchClientVersion"]

dependencies {
  api("io.vertx:vertx-core:${vertxVersion}")
  api("org.opensearch.client:opensearch-rest-high-level-client:${opensearchClientVersion}")
  compileOnly("io.vertx:vertx-codegen:${vertxVersion}")
}

sourceSets {
  main {
    java {
      setSrcDirs(listOf("src/main/java", "src/main/generated"))
    }
  }
}

tasks {
  create<Copy>("copy-shims") {
    from(getByPath(":shim-generator:opensearch-process"))
    into("src/main/generated")
  }

  getByName("compileJava").dependsOn("copy-shims")

  getByName<Delete>("clean") {
    delete.add("src/main/generated")
  }

  create<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    classifier = "sources"
  }

  create<Jar>("javadocJar") {
    from(javadoc)
    classifier = "javadoc"
  }

  javadoc {
    if (JavaVersion.current().isJava9Compatible) {
      (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
  }

  withType<Sign> {
    onlyIf { project.extra["isReleaseVersion"] as Boolean }
  }
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifact(tasks["sourcesJar"])
      artifact(tasks["javadocJar"])
      pom {
        name.set(project.name)
        description.set("Reactiverse OpenSearch client")
        url.set("https://github.com/reactiverse/opensearch-client")
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
        developers {
          developer {
            id.set("jponge")
            name.set("Julien Ponge")
            email.set("julien.ponge@gmail.com")
          }
          developer {
            id.set("sboeckelmann")
            name.set("Sven Böckelmann")
            email.set("sven.boeckelmann@googlemail.com")
          }
        }
        scm {
          connection.set("scm:git:git@github.com:reactiverse/opensearch-client.git")
          developerConnection.set("scm:git:git@github.com:reactiverse/opensearch-client.git")
          url.set("https://github.com/reactiverse/opensearch-client")
        }
      }
    }
  }
  repositories {
    // To locally check out the poms
    maven {
      val releasesRepoUrl = uri("$buildDir/repos/releases")
      val snapshotsRepoUrl = uri("$buildDir/repos/snapshots")
      name = "BuildDir"
      url = if (project.extra["isReleaseVersion"] as Boolean) releasesRepoUrl else snapshotsRepoUrl
    }
    maven {
      val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
      val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
      name = "SonatypeOSS"
      url = if (project.extra["isReleaseVersion"] as Boolean) releasesRepoUrl else snapshotsRepoUrl
      credentials {
        val ossrhUsername: String by project
        val ossrhPassword: String by project
        username = ossrhUsername
        password = ossrhPassword
      }
    }
  }
}

signing {
  def signingKey = findProperty("signingKey")
  def signingPassword = findProperty("signingPassword")
  useInMemoryPgpKeys(signingKey, signingPassword)
  sign(publishing.publications["mavenJava"])
}

tasks.wrapper {
  gradleVersion = "7.1"
  distributionType = Wrapper.DistributionType.ALL
}
