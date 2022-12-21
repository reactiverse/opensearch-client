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

  version = "0.9.0-SNAPSHOT"
  group = "io.reactiverse"

  extra["vertxVersion"] = "4.1.0"
  extra["opensearchClientVersion"] = "2.4.1"
  extra["mutinyBindingsVersion"] = "2.7.0"

  extra["assertjVersion"] = "3.19.0"
  extra["tcVersion"] = "1.15.3"
  extra["junitVersion"] = "5.7.2"
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
}
