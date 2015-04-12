package org.intellij.grammar;

import java.io.IOException;

import org.jetbrains.annotations.NonNls;
import com.intellij.testFramework.ParsingTestCase;

/**
 * @author gregsh
 */
public class BnfParserTest extends ParsingTestCase {
  public BnfParserTest() {
    super("parser", "bnf");
  }
  @Override
  protected String getTestDataPath() {
    return "/testData";
  }

  public void testBnfGrammar() { doTest(true); }
  public void testSelf() { doTest(true); }
  public void testBrokenAttr() { doTest(true); }
  public void testBrokenEverything() { doTest(true); }
  public void testAlternativeSyntax() { doTest(true); }
  public void testExternalExpression() { doTest(true); }
  public void testFixes() { doTest(true); }

  @Override
  protected String loadFile(@NonNls String name) throws IOException {
    if (name.equals("BnfGrammar.bnf")) return super.loadFile("../../grammars/Grammar.bnf");
    return super.loadFile(name);
  }
}
