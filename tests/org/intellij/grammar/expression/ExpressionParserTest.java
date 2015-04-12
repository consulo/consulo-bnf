package org.intellij.grammar.expression;

import com.intellij.testFramework.ParsingTestCase;

/**
 * @author gregsh
 */
public class ExpressionParserTest extends ParsingTestCase {
  public ExpressionParserTest() {
    super("parser/expression", "expr");
  }

  @Override
  protected String getTestDataPath() {
    return "testData";
  }

  public void testSimple() { doTest(true); }

}
