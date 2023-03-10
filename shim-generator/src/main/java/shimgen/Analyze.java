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

package shimgen;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class Analyze {

  private static final Logger logger = LoggerFactory.getLogger(Analyze.class);

  static final HashSet<String> FILTERED_TYPES = new HashSet<String>() {
    {
      add("RestClient");
      add("MigrationClient");
    }
  };

  private static final String autoGeneratedComment = "\n" +
    "[NOTE] This is an automatically generated file.\n" +
    "       Do not make changes to this file but to the shim code generator.\n" +
    "\n";

  private static String pathToMainFile;
  private static String pathToGeneration;
  private static String targetPackageName;
  private static String pathToMainPackage;

  public static void main(String[] args) throws IOException {

    if (args.length != 3) {
      logger.error("Expecting 3 arguments: the path to RestHighLevelClient.java, then the path to the code generation directory, then the generated code package name");
      System.exit(1);
    }

    pathToMainFile = args[0];
    pathToMainPackage = new File(pathToMainFile).getParent();
    pathToGeneration = args[1];
    targetPackageName = args[2];
    logger.info("Analyzing {}, generating code to {} in package {}", pathToMainFile, pathToGeneration, targetPackageName);

    new Analyze().run();
  }

  private void run() throws IOException {
    CompilationUnit unit = JavaParser.parse(new File(pathToMainFile));
    List<String> nestedClients = unit.accept(new NestedClientFinder(), null);
    logger.info("Found the following nested clients: {}", nestedClients);

    generatedFilesPackageDir().mkdirs();
    generateCode("RestHighLevelClient");
    for (String nestedClient : nestedClients) {
      generateCode(nestedClient);
    }
  }

  private File generatedFilesPackageDir() {
    return new File(pathToGeneration, targetPackageName.replace('.', '/'));
  }

  private void generateCode(String unitName) throws IOException {
    File inputFile = new File(pathToMainPackage, unitName + ".java");
    CompilationUnit sourceUnit = JavaParser.parse(inputFile);

    CompilationUnit shimInterfaceUnit = new CompilationUnit();
    shimInterfaceUnit.setBlockComment(autoGeneratedComment);
    shimInterfaceUnit
      .setPackageDeclaration(targetPackageName)
      .addImport("io.vertx.core.*")
      .addImport("io.vertx.codegen.annotations.*")
      .addImport("org.opensearch.client.*");

    ClassOrInterfaceDeclaration shimInterface = shimInterfaceUnit
      .addInterface(unitName)
      .addAnnotation("VertxGen");

    CompilationUnit shimImplementationUnit = new CompilationUnit();
    shimImplementationUnit.setBlockComment(autoGeneratedComment);
    shimImplementationUnit
      .setPackageDeclaration(targetPackageName)
      .addImport("io.vertx.core.*")
      .addImport("org.opensearch.client.*");

    ClassOrInterfaceDeclaration shimImplementation = shimImplementationUnit
      .addClass(unitName + "Impl")
      .setPublic(false)
      .addImplementedType(unitName);

    sourceUnit.accept(new ShimMaker(shimInterfaceUnit, shimInterface, shimImplementationUnit, shimImplementation), null);

    logger.debug("Interface:\n{} ", shimInterfaceUnit.toString());
    logger.debug("Implementation:\n{} ", shimImplementationUnit.toString());
    Files.write(new File(generatedFilesPackageDir(), unitName + ".java").toPath(), Collections.singletonList(shimInterfaceUnit.toString()));
    Files.write(new File(generatedFilesPackageDir(), unitName + "Impl.java").toPath(), Collections.singletonList(shimImplementationUnit.toString()));
  }
}
