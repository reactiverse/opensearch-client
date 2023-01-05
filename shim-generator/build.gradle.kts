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

val opensearch by configurations.creating

val opensearchSourcesDir = "$buildDir/opensearch-sources/"
val opensearchShimsDir = "${buildDir}/opensearch-shims"

val opensearchClientVersion = extra["opensearchClientVersion"]
val javaParserVersion = extra["javaParserVersion"]
val logbackVersion = extra["logbackVersion"]

dependencies {
  implementation("ch.qos.logback:logback-classic:${logbackVersion}")
  implementation("com.github.javaparser:javaparser-core:${javaParserVersion}")
  opensearch("org.opensearch.client:opensearch-rest-high-level-client:${opensearchClientVersion}:sources")
}

tasks {

  create<Copy>("opensearch-unpack") {
    val sources = opensearch.resolve().filter { it.name.endsWith("-sources.jar") }
    sources.forEach { from(zipTree(it)) }
    into(opensearchSourcesDir)
  }

  create<JavaExec>("opensearch-process") {
    main = "shimgen.Analyze"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(
      "$opensearchSourcesDir/org/opensearch/client/RestHighLevelClient.java",
      opensearchShimsDir,
      "io.reactiverse.opensearch.client"
    )
    description = "Generate the shims from the OpenSearch source code"
    group = "build"
    dependsOn("opensearch-unpack")
    inputs.dir(opensearchSourcesDir)
    outputs.dir(opensearchShimsDir)
  }
}

