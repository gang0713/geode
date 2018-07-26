/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache.backup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.geode.internal.lang.SystemUtils;

/**
 * This class is used to automatically generate a restore script for a backup. It keeps a list of
 * files that were backed up, and a list of files that we should test for to avoid overriding when
 * we restore the backup.
 *
 * <p>
 * It generates either a restore.sh for unix or a restore.bat for windows.
 */
class RestoreScript {

  static final String INCREMENTAL_MARKER_COMMENT =
      "Incremental backup.  Restore baseline originals from previous backups.";

  static final String REFUSE_TO_OVERWRITE_MESSAGE = "Backup not restored. Refusing to overwrite ";

  private static final String[] ABOUT_SCRIPT_COMMENT =
      {"Restore a backup of gemfire persistent data to the location it was backed up",
          "from. This script will refuse to restore if the original data still exists.",
          "This script was automatically generated by the gemfire backup utility.",};

  private static final String EXISTENCE_CHECK_COMMENT =
      "Test for existing originals.  If they exist, do not restore the backup.";

  private static final String RESTORE_DATA_COMMENT = "Restore data";

  private final ScriptGenerator generator;
  private final Map<File, File> baselineFiles = new HashMap<>();
  private final Map<File, File> backedUpFiles = new LinkedHashMap<>();
  private final List<File> existenceTests = new ArrayList<>();

  RestoreScript() {
    this(SystemUtils.isWindows() ? new WindowsScriptGenerator() : new UnixScriptGenerator());
  }

  private RestoreScript(final ScriptGenerator generator) {
    this.generator = generator;
  }

  void addBaselineFiles(final Map<File, File> baselineFiles) {
    this.baselineFiles.putAll(baselineFiles);
  }

  void addBaselineFile(File baseline, File absoluteFile) {
    baselineFiles.put(baseline, absoluteFile);
  }

  public void addFile(final File originalFile, final File backupFile) {
    backedUpFiles.put(backupFile, originalFile.getAbsoluteFile());
  }

  void addExistenceTest(final File originalFile) {
    existenceTests.add(originalFile.getAbsoluteFile());
  }

  File generate(final File outputDir) throws IOException {
    File outputFile = new File(outputDir, generator.getScriptName());
    try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath())) {

      writePreamble(writer);
      writeAbout(writer);
      writeExistenceTest(writer);
      writeRestoreData(writer, outputDir.toPath());
      writeIncrementalData(writer);
      generator.writeExit(writer);
    }

    outputFile.setExecutable(true, true);
    return outputFile;
  }

  private void writePreamble(BufferedWriter writer) throws IOException {
    generator.writePreamble(writer);
    writer.newLine();
  }

  private void writeAbout(BufferedWriter writer) throws IOException {
    for (String comment : ABOUT_SCRIPT_COMMENT) {
      generator.writeComment(writer, comment);
    }
    writer.newLine();
  }

  private void writeExistenceTest(BufferedWriter writer) throws IOException {
    generator.writeComment(writer, EXISTENCE_CHECK_COMMENT);
    for (File file : existenceTests) {
      generator.writeExistenceTest(writer, file);
    }
    writer.newLine();
  }

  private void writeRestoreData(BufferedWriter writer, Path outputDir) throws IOException {
    generator.writeComment(writer, RESTORE_DATA_COMMENT);
    for (Map.Entry<File, File> entry : backedUpFiles.entrySet()) {
      File backup = entry.getKey();
      String[] backupFiles = backup.list();
      boolean backupHasFiles =
          backup.isDirectory() && backupFiles != null && backupFiles.length != 0;
      // backup = outputDir.relativize(backup.toPath()).toFile();
      File original = entry.getValue();
      if (original.isDirectory()) {
        generator.writeCopyDirectoryContents(writer, backup, original, backupHasFiles);
      } else {
        generator.writeCopyFile(writer, backup, original);
      }
    }
  }

  private void writeIncrementalData(BufferedWriter writer) throws IOException {
    // Write out baseline file copies in restore script (if there are any) if this is a restore
    // for an incremental backup
    if (baselineFiles.isEmpty()) {
      return;
    }

    writer.newLine();
    generator.writeComment(writer, INCREMENTAL_MARKER_COMMENT);
    for (Map.Entry<File, File> entry : baselineFiles.entrySet()) {
      generator.writeCopyFile(writer, entry.getKey(), entry.getValue());
    }
  }

  void addUserFile(File original, File dest) {
    addExistenceTest(original);
    addFile(original, dest);
  }
}
