package org.intellij.grammar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.intellij.grammar.generator.ParserGenerator;
import org.intellij.grammar.psi.impl.BnfFileImpl;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.ParsingTestCase;

/**
 * @author gregsh
 */
public class BnfGeneratorTest extends ParsingTestCase {
  public BnfGeneratorTest() {
    super("generator", "bnf");
  }

  @Override
  protected String getTestDataPath() {
    return "testData";
  }

  public void testBnfGrammar() throws Exception { doGenTest(true); }
  public void testSelf() throws Exception { doGenTest(true); }
  public void testSmall() throws Exception { doGenTest(false); }
  public void testAutopin() throws Exception { doGenTest(false); }
  public void testExternalRules() throws Exception { doGenTest(false); }
  public void testLeftAssociative() throws Exception { doGenTest(false); }
  public void testPsiGen() throws Exception { doGenTest(true); }
  public void testPsiAccessors() throws Exception { doGenTest(true); }
  public void testPsiStart() throws Exception { doGenTest(true); }
  public void testExprParser() throws Exception { doGenTest(false); }
  public void testTokenSequence() throws Exception { doGenTest(false); }

  public void testEmpty() throws Exception {
    myFile = createPsiFile("empty.bnf", "{ }");
    ParserGenerator parserGenerator = new ParserGenerator((BnfFileImpl)myFile, "", myFullDataPath);
    parserGenerator.setUnitTestMode(true);
    parserGenerator.generate();
  }

  @Override
  protected String loadFile(@NonNls String name) throws IOException {
    if (name.equals("BnfGrammar.bnf")) return super.loadFile("../../grammars/Grammar.bnf");
    return super.loadFile(name);
  }

  public void doGenTest(final boolean generatePsi) throws Exception {
    final String name = getTestName(false);
    String text = loadFile(name + ".bnf");
    myFile = createPsiFile(name, text.replaceAll("generatePsi=\"[^\"]*\"", "\0 generatePsi=" + generatePsi));
    List<File> filesToCheck = new ArrayList<File>();
    filesToCheck.add(new File(myFullDataPath, name + ".java"));
    if (generatePsi) {
      filesToCheck.add(new File(myFullDataPath, name + ".PSI.java"));
    }
    for (File file : filesToCheck) {
      if (file.exists()) {
        assertTrue(file.delete());
      }
    }

    ParserGenerator parserGenerator = new ParserGenerator((BnfFileImpl)myFile, "", myFullDataPath);
    parserGenerator.setUnitTestMode(true);
    if (generatePsi) parserGenerator.generate();
    else parserGenerator.generateParser();

    List<String> messages = new ArrayList<String>();
    try {
      for (File file : filesToCheck) {
        assertTrue("Generated file not found: "+file, file.exists());
        final String expectedName = FileUtil.getNameWithoutExtension(file) + ".expected.java";
        String result = loadFile(file.getName());
        try {
          if (OVERWRITE_TESTDATA) throw new FileNotFoundException();
          String expectedText = loadFile(expectedName);
          if (!Comparing.equal(expectedText, result)) {
            throw new FileComparisonFailure(expectedName, expectedText, result, new File(myFullDataPath + File.separator + expectedName).getAbsolutePath());
          }
        }
        catch (FileNotFoundException e) {
          FileWriter writer = new FileWriter(new File(myFullDataPath, expectedName));
          try {
            writer.write(result);
          }
          finally {
            writer.close();
          }
          messages.add("No output text found. File " + expectedName + " created.");
        }
      }
    }
    finally {
      for (File file : filesToCheck) {
        file.delete();
      }
    }
    for (String message : messages) {
      System.err.println(message);
    }
    assertTrue(OVERWRITE_TESTDATA || messages.isEmpty());
  }
}
