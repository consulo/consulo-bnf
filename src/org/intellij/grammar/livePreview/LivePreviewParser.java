/*
 * Copyright 2011-2013 Gregory Shrago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.grammar.livePreview;

import static org.intellij.grammar.generator.ParserGeneratorUtil.*;
import static org.intellij.grammar.parser.GeneratedParserUtilBase.*;
import static org.intellij.grammar.psi.BnfTypes.*;

import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.generator.ExpressionGeneratorHelper;
import org.intellij.grammar.generator.ExpressionHelper;
import org.intellij.grammar.generator.ParserGeneratorUtil;
import org.intellij.grammar.generator.RuleGraphHelper;
import org.intellij.grammar.parser.GeneratedParserUtilBase;
import org.intellij.grammar.psi.BnfExpression;
import org.intellij.grammar.psi.BnfExternalExpression;
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfLiteralExpression;
import org.intellij.grammar.psi.BnfPredicate;
import org.intellij.grammar.psi.BnfReferenceOrToken;
import org.intellij.grammar.psi.BnfRule;
import org.intellij.grammar.psi.impl.GrammarUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;

/**
 * @author gregsh
 */
public class LivePreviewParser implements PsiParser {

  private final BnfFile myFile;
  private final LivePreviewLanguage myLanguage;
  private Map<String,String> mySimpleTokens;
  private BnfRule myGrammarRoot;
  private final Map<String, IElementType> myElementTypes = new THashMap<String, IElementType>();
  private RuleGraphHelper myGraphHelper;
  private ExpressionHelper myExpressionHelper;
  private MultiMap<BnfRule, BnfRule> myRuleExtendsMap;
  private boolean generateExtendedPin;
  private String myTokenTypeText;

  private final TObjectIntHashMap myRuleNumbers = new TObjectIntHashMap();
  private BitSet[] myBitSets;

  public LivePreviewParser(Project project, LivePreviewLanguage language) {
    myLanguage = language;
    myFile = language.getGrammar(project);
  }

  @NotNull
  @Override
  public ASTNode parse(IElementType root, PsiBuilder originalBuilder, LanguageVersion languageVersion) {
    //com.intellij.openapi.progress.ProgressIndicator indicator = com.intellij.openapi.progress.ProgressManager.getInstance().getProgressIndicator();
    //if (indicator != null ) indicator.startNonCancelableSection(); // todo drop me
    init(originalBuilder);
    PsiBuilder builder = adapt_builder_(root, originalBuilder, this);
    PsiBuilder.Marker mark = builder.mark();
    GeneratedParserUtilBase.enterErrorRecordingSection(builder, 0, _SECTION_RECOVER_, null);
    boolean result = false;
    result = myGrammarRoot != null && rule(builder, 1, myGrammarRoot, Collections.<String, Parser>emptyMap());
    GeneratedParserUtilBase.exitErrorRecordingSection(builder, 0, result, true, _SECTION_RECOVER_, TRUE_CONDITION);
    mark.done(root);
    return builder.getTreeBuilt();
  }

  private void init(PsiBuilder builder) {
    myGrammarRoot = myFile == null? null : ContainerUtil.getFirstItem(myFile.getRules());
    if (myGrammarRoot == null) return;
    generateExtendedPin = getRootAttribute(myFile, KnownAttribute.EXTENDED_PIN);
    mySimpleTokens = LivePreviewLexer.collectTokenPattern2Name(myFile);
    myGraphHelper = RuleGraphHelper.getCached(myFile);
    myRuleExtendsMap = myGraphHelper.getRuleExtendsMap();
    myExpressionHelper = ExpressionHelper.getCached(myFile);

    myTokenTypeText = getRootAttribute(myFile, KnownAttribute.ELEMENT_TYPE_PREFIX);

    Lexer lexer = ((PsiBuilderImpl)builder).getLexer();
    if (lexer instanceof LivePreviewLexer) {
      for (LivePreviewLexer.Token type : ((LivePreviewLexer)lexer).getTokens()) {
        myElementTypes.put(type.constantName, type.tokenType);
      }
    }
    for (BnfRule rule : myFile.getRules()) {
      String elementType = ParserGeneratorUtil.getElementType(rule);
      if (StringUtil.isEmpty(elementType)) continue;
      myElementTypes.put(elementType, new RuleElementType(elementType, rule, myLanguage));
    }
    int count = 0;
    for (BnfRule rule : myFile.getRules()) {
      myRuleNumbers.put(rule, count ++);
    }
    myBitSets = new BitSet[builder.getOriginalText().length()+1];
    for (int i = 0; i < myBitSets.length; i++) {
      myBitSets[i] = new BitSet(count);
    }
  }

  private boolean rule(PsiBuilder builder, int level, BnfRule rule, Map<String, Parser> externalArguments) {
    BitSet bitSet = myBitSets[builder.getCurrentOffset()];
    int ruleNumber = myRuleNumbers.get(rule);
    if (bitSet.get(ruleNumber)) {
      builder.error("Endless recursion detected for '" + rule.getName() + "'");
      return false;
    }
    bitSet.set(ruleNumber);
    boolean result = generateNodeCall(builder, level, rule, rule.getExpression(), rule.getName(), externalArguments);
    bitSet.clear(ruleNumber);
    return result;
  }

  protected boolean expression(PsiBuilder builder,
                               int level,
                               final BnfRule rule,
                               BnfExpression initialNode,
                               String funcName,
                               Map<String, Parser> externalArguments) {
    boolean isRule = initialNode.getParent() == rule;
    BnfExpression node = getNonTrivialNode(initialNode);

    IElementType type = getEffectiveType(node);

    boolean firstNonTrivial = node == ParserGeneratorUtil.Rule.firstNotTrivial(rule);
    boolean isPrivate = !(isRule || firstNonTrivial) || ParserGeneratorUtil.Rule.isPrivate(rule) || myGrammarRoot == rule;
    boolean isLeft = firstNonTrivial && ParserGeneratorUtil.Rule.isLeft(rule);
    boolean isLeftInner = isLeft && (isPrivate || ParserGeneratorUtil.Rule.isInner(rule));
    String recoverRoot = firstNonTrivial ? getAttribute(rule, KnownAttribute.RECOVER_UNTIL) : null;
    boolean canCollapse = !isPrivate && (!isLeft || isLeftInner) && firstNonTrivial && canCollapse(rule);

    IElementType elementType = getElementType(rule);

    List<BnfExpression> children;
    if (node instanceof BnfReferenceOrToken || node instanceof BnfLiteralExpression || node instanceof BnfExternalExpression) {
      children = Collections.singletonList(node);
      if (isPrivate && !isLeftInner && recoverRoot == null) {
        return generateNodeCall(builder, level, rule, node, getNextName(funcName, 0), externalArguments);
      }
      else {
        type = BNF_SEQUENCE;
      }
    }
    else {
      children = getChildExpressions(node);
      if (children.isEmpty()) {
        if (isPrivate || elementType != null) {
          return true;
        }
        else {
          builder.mark().done(elementType);
          return true;
        }
      }
    }
    if (!recursion_guard_(builder, level, funcName)) return false;

    String frameName = firstNonTrivial && !Rule.isMeta(rule)? getRuleDisplayName(rule, !isPrivate) : null;
    //if (recoverRoot == null && (isRule || firstNonTrivial)) {
    //  frameName = generateFirstCheck(rule, frameName, true);
    //}

    PinMatcher pinMatcher = new PinMatcher(rule, type, firstNonTrivial ? rule.getName() : funcName);
    boolean pinApplied = false;
    boolean alwaysTrue = type == BNF_OP_OPT || type == BNF_OP_ZEROMORE;

    boolean result_ = (type == BNF_OP_ZEROMORE || type == BNF_OP_OPT);
    boolean pinned = pinMatcher.active();
    boolean pinned_ = false;

    int start_ = builder.getCurrentOffset();

    PsiBuilder.Marker left_marker_ = isLeft? (PsiBuilder.Marker)builder.getLatestDoneMarker() : null;
    if (isLeft && generateExtendedPin) {
      if (!invalid_left_marker_guard_(builder, left_marker_, funcName)) return false;
    }
    PsiBuilder.Marker marker_ = !alwaysTrue || !isPrivate? builder.mark() : null;

    String sectionType = recoverRoot != null ? "_SECTION_RECOVER_" :
                         type == BNF_OP_AND ? "_SECTION_AND_" :
                         type == BNF_OP_NOT ? "_SECTION_NOT_" :
                         pinned || frameName != null ? "_SECTION_GENERAL_" : null;
    if (sectionType != null) {
      enterErrorRecordingSection(builder, level, sectionType, frameName);
    }

    boolean predicateEncountered = false;
    int[] skip = {0};
    for (int i = 0, p = 0, childrenSize = children.size(); i < childrenSize; i++) {
      BnfExpression child = children.get(i);

      if (type == BNF_CHOICE) {
        if (i == 0) result_ = generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
        else if (!result_) result_ = generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
      }
      else if (type == BNF_SEQUENCE) {
        predicateEncountered |= pinApplied && ParserGeneratorUtil.getEffectiveExpression(myFile, child) instanceof BnfPredicate;
        if (skip[0] == 0) {
          if (i == 0) {
            result_ = generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments);
          }
          else {
            if (pinApplied && generateExtendedPin && !predicateEncountered) {
              if (i == childrenSize - 1) {
                // do not report error for last child
                if (i == p + 1) {
                  result_ = result_ && generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments);
                }
                else {
                  result_ = pinned_ && generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments) && result_;
                }
              }
              else if (i == p + 1) {
                result_ = result_ && report_error_(builder, generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments));
              }
              else {
                result_ = pinned_ && report_error_(builder, generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments)) && result_;
              }
            }
            else {
              result_ = result_ && generateTokenSequenceCall(builder, level, rule, children, funcName, i, pinMatcher, pinApplied, skip, externalArguments);
            }
          }
        }
        else {
          skip[0]--; // we are inside already generated token sequence
          if (pinApplied && i == p + 1) p++; // shift pinned index as we skip
        }
        if (!pinApplied && pinMatcher.matches(i, child)) {
          pinApplied = true;
          p = i;
          pinned_ = result_; // pin = pinMatcher.pinValue
        }
      }
      else if (type == BNF_OP_OPT) {
        generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
      }
      else if (type == BNF_OP_ONEMORE || type == BNF_OP_ZEROMORE) {
        if (type == BNF_OP_ONEMORE) {
          result_ = generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
        }
        int offset_ = builder.getCurrentOffset();
        //noinspection LoopConditionNotUpdatedInsideLoop
        while (alwaysTrue || result_) {
          if (!generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments)) break;
          int next_offset_ = builder.getCurrentOffset();
          if (offset_ == next_offset_) {
            empty_element_parsed_guard_(builder, offset_, funcName);
            break;
          }
          offset_ = next_offset_;
        }
      }
      else if (type == BNF_OP_AND) {
        result_ = generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
      }
      else if (type == BNF_OP_NOT) {
        result_ = !generateNodeCall(builder, level, rule, child, getNextName(funcName, i), externalArguments);
      }
      else {
        addWarning(myFile.getProject(), "unexpected: " + type);
      }
    }

    if (type == BNF_OP_AND || type == BNF_OP_NOT) {
      marker_.rollbackTo();
    }
    else if (!isPrivate && elementType != null) {
      LighterASTNode last_ = canCollapse && (alwaysTrue || result_) ? builder.getLatestDoneMarker() : null;
      if (last_ != null && last_.getStartOffset() == start_ && type_extends_(last_.getTokenType(), elementType)) {
        marker_.drop();
      }
      else if (result_ || pinned_) {
        if (isLeftInner) {
          marker_.done(elementType);
          left_marker_.precede().done(((LighterASTNode)left_marker_).getTokenType());
          left_marker_.drop();
        }
        else if (isLeft) {
          marker_.drop();
          left_marker_.precede().done(elementType);
        }
        else {
          marker_.done(elementType);
        }
      }
      else {
        marker_.rollbackTo();
      }
    }
    else if (!alwaysTrue) {
      if (!result_ && !pinned_) {
        marker_.rollbackTo();
      }
      else {
        marker_.drop();
      }
      if (isLeftInner) {
        left_marker_.precede().done(((LighterASTNode)left_marker_).getTokenType());
        left_marker_.drop();
      }
    }
    if (sectionType != null) {
      final BnfRule untilRule = recoverRoot != null ? myFile.getRule(recoverRoot) : null;
      result_ = exitErrorRecordingSection(builder, level, alwaysTrue || result_, pinned_,
                                          sectionType, untilRule == null? null : new Parser() {
        @Override
        public boolean parse(PsiBuilder builder, int level) {
          return rule(builder, level, untilRule, Collections.<String, Parser>emptyMap());
        }
      });
    }

    return alwaysTrue || result_ || pinned_;
  }

  private boolean type_extends_(IElementType elementType1, IElementType elementType2) {
    if (!(elementType1 instanceof RuleElementType)) return false;
    if (!(elementType2 instanceof RuleElementType)) return false;
    for (BnfRule baseRule : myRuleExtendsMap.keySet()) {
      Collection<BnfRule> ruleClass = myRuleExtendsMap.get(baseRule);
      if (ruleClass.contains(((RuleElementType)elementType1).rule) &&
          ruleClass.contains(((RuleElementType)elementType2).rule)) return true;
    }
    return false;
  }


  protected boolean generateNodeCall(PsiBuilder builder, int level, BnfRule rule, @Nullable BnfExpression node, String nextName, Map<String, Parser> externalArguments) {
    IElementType type = node == null ? BNF_REFERENCE_OR_TOKEN : getEffectiveType(node);
    String text = node == null ? nextName : node.getText();
    if (type == BNF_STRING) {
      String value = StringUtil.stripQuotesAroundValue(text);
      String attributeName = getTokenName(value);
      if (attributeName != null) {
        return generateConsumeToken(builder, attributeName);
      }
      return generateConsumeTextToken(builder, value);
    }
    else if (type == BNF_NUMBER) {
      return generateConsumeTextToken(builder, text);
    }
    else if (type == BNF_REFERENCE_OR_TOKEN) {
      BnfRule subRule = myFile.getRule(text);
      if (subRule != null) {
        //String method;
        if (Rule.isExternal(subRule)) {
          // not supported
          return false;
          //method = generateExternalCall(rule, clause, GrammarUtil.getExternalRuleExpressions(subRule), nextName);
          //return method + "(builder_, level_ + 1" + clause.toString() + ")";
        }
        else {
          ExpressionHelper.ExpressionInfo info = ExpressionGeneratorHelper.getInfoForExpressionParsing(myExpressionHelper, subRule);
          if (info == null) {
            return rule(builder, level + 1, subRule, externalArguments);
          }
          else {
            return generateExpressionRoot(builder, level, info, info.getPriority(subRule) - 1);
          }
        }
      }
      return generateConsumeToken(builder, text);
    }
    else if (type == BNF_EXTERNAL_EXPRESSION) {
      List<BnfExpression> expressions = ((BnfExternalExpression)node).getExpressionList();
      if (expressions.size() == 1 && Rule.isMeta(rule)) {
        Parser parser = externalArguments.get(node.getText());
        return parser != null && parser.parse(builder, level);
      }
      else {
        return generateExternalCall(builder, level, rule, expressions, nextName, externalArguments);
      }
    }
    else {
      return expression(builder, level, rule, node, nextName, externalArguments);
    }
  }

  private boolean generateTokenSequenceCall(PsiBuilder builder,
                                            int level,
                                            BnfRule rule,
                                            List<BnfExpression> children,
                                            String funcName,
                                            int startIndex,
                                            PinMatcher pinMatcher,
                                            boolean pinApplied,
                                            int[] skip,
                                            Map<String, Parser> externalArguments) {
    BnfExpression nextChild = children.get(startIndex);
    if (startIndex == children.size() - 1 || !isTokenExpression(nextChild)) {
      return generateNodeCall(builder, level, rule, nextChild, funcName, externalArguments);
    }
    ArrayList<IElementType> list = new ArrayList<IElementType>();
    int pin = pinApplied ? -1 : 0;
    for (int i = startIndex, len = children.size(); i < len; i++) {
      BnfExpression child = children.get(i);
      IElementType type = child.getNode().getElementType();
      String text = child.getText();
      String tokenName;
      if (type == BNF_STRING && text.charAt(0) != '\"') {
        tokenName = getTokenName(StringUtil.stripQuotesAroundValue(text));
      }
      else if (type == BNF_REFERENCE_OR_TOKEN && myFile.getRule(text) == null) {
        tokenName = text;
      }
      else {
        break;
      }
      list.add(getTokenElementType(tokenName));
      if (!pinApplied && pinMatcher.matches(i, child)) {
        pin = i - startIndex + 1;
      }
    }
    if (list.size() < 2) {
      return generateNodeCall(builder, level, rule, nextChild, funcName, externalArguments);
    }
    skip[0] = list.size() - 1;
    return consumeTokens(builder, pin, list.toArray(new IElementType[list.size()]));
  }

  private boolean generateExternalCall(PsiBuilder builder,
                                       int level,
                                       final BnfRule rule,
                                       List<BnfExpression> expressions,
                                       String nextName,
                                       final Map<String, Parser> externalArguments) {
    List<BnfExpression> callParameters = expressions;
    List<BnfExpression> metaParameters = Collections.emptyList();
    List<String> metaParameterNames;
    String method = expressions.size() > 0 ? expressions.get(0).getText() : null;
    final BnfRule targetRule = method == null ? null : myFile.getRule(method);
    // handle external rule call: substitute and merge arguments from external expression and rule definition
    if (targetRule != null) {
      metaParameterNames = GrammarUtil.collectExtraArguments(targetRule, targetRule.getExpression());
      if (Rule.isExternal(targetRule)) {
        // not supported
        return false;
        //callParameters = GrammarUtil.getExternalRuleExpressions(targetRule);
        //metaParameters = expressions;
        //method = callParameters.get(0).getText();
        //if (metaParameterNames.size() < expressions.size() - 1) {
        //  callParameters = ContainerUtil.concat(callParameters, expressions.subList(metaParameterNames.size() + 1, expressions.size()));
        //}
      }
    }
    else {
      if ("eof".equals(method) && expressions.size() == 1) {
        return GeneratedParserUtilBase.eof(builder, level);
      }
      // not supported
      return false;
    }
    if (callParameters.size() <= 1) {
      return rule(builder, level, targetRule, externalArguments);
    }
    Map<String, Parser> argumentMap = new HashMap<String, Parser>();
    for (int i = 1, len = Math.min(callParameters.size(), metaParameterNames.size() + 1); i < len; i++) {
      BnfExpression nested = callParameters.get(i);
      String argument = nested.getText();
      final String argNextName;
      final String argName;
      int metaIdx;
      if (argument.startsWith("<<") && (metaIdx = metaParameterNames.indexOf(argument)) > -1) {
        nested = metaParameters.get(metaIdx + 1);
        argument = nested.getText();
        argNextName = getNextName(nextName, metaIdx);
        argName = argument;
      }
      else {
        argNextName = getNextName(nextName, i - 1);
        argName = metaParameterNames.get(i - 1);
      }
      final BnfExpression finalNested = nested;
      if (nested instanceof BnfReferenceOrToken) {
        final BnfRule argRule = myFile.getRule(argument);
        if (argRule != null) {
          argumentMap.put(argName, new Parser() {
            @Override
            public boolean parse(PsiBuilder builder, int level) {
              return rule(builder, level, argRule, Collections.<String, Parser>emptyMap());
            }
          });
        }
        else {
          // not supported
          //clause.append(argument);
        }
      }
      else if (nested instanceof BnfLiteralExpression) {
        // not supported
        //if (argument.startsWith("\'")) {
        //  clause.append(StringUtil.unquoteString(argument));
        //}
        //else {
        //  clause.append(argument);
        //}
      }
      else if (nested instanceof BnfExternalExpression) {
        List<BnfExpression> expressionList = ((BnfExternalExpression)nested).getExpressionList();
        boolean metaRule = Rule.isMeta(rule);
        if (metaRule && expressionList.size() == 1) {
          // parameter
          argumentMap.put(argName, externalArguments.get(expressionList.get(0).getText()));
        }
        else {
          argumentMap.put(argName, new Parser() {
            @Override
            public boolean parse(PsiBuilder builder, int level) {
              return generateNodeCall(builder, level, targetRule, finalNested, argNextName, externalArguments);
            }
          });
        }
      }
      else {
        argumentMap.put(argName, new Parser() {
          @Override
          public boolean parse(PsiBuilder builder, int level) {
            return generateNodeCall(builder, level, targetRule, finalNested, argNextName, externalArguments);
          }
        });
      }
    }
    return rule(builder, level, targetRule, argumentMap);
  }


  private boolean canCollapse(BnfRule rule) {
    Map<PsiElement, RuleGraphHelper.Cardinality> map = myGraphHelper.getFor(rule);
    for (PsiElement element : map.keySet()) {
      if (element instanceof LeafPsiElement) continue;
      RuleGraphHelper.Cardinality c = map.get(element);
      if (c.optional()) continue;
      if (!(element instanceof BnfRule)) return false;
      if (!myGraphHelper.collapseEachOther(rule, (BnfRule)element)) return false;
    }
    return myRuleExtendsMap.containsScalarValue(rule);
  }


  private String getTokenName(String value) {
    return mySimpleTokens.get(value);
  }

  @Nullable
  private IElementType getElementType(BnfRule rule) {
    String elementType = ParserGeneratorUtil.getElementType(rule);
    if (StringUtil.isEmpty(elementType)) return null;
    return getElementType(elementType);
  }

  private IElementType getElementType(String elementType) {
    return myElementTypes.get(elementType);
  }

  private boolean generateConsumeToken(PsiBuilder builder, String tokenName) {
    IElementType tokenType = getTokenElementType(tokenName);
    return tokenType != null && generateConsumeToken(builder, tokenType);
  }

  protected boolean generateConsumeToken(PsiBuilder builder, IElementType tokenType) {
    return consumeToken(builder, tokenType);
  }

  protected boolean generateConsumeTextToken(PsiBuilder builder, String tokenText) {
    return consumeToken(builder, tokenText);
  }

  private IElementType getTokenElementType(String token) {
    return getElementType(myTokenTypeText + token.toUpperCase());
  }

  protected boolean isTokenExpression(BnfExpression node) {
    return node instanceof BnfLiteralExpression || node instanceof BnfReferenceOrToken && myFile.getRule(node.getText()) == null;
  }

  public static class RuleElementType extends IElementType {
    public final BnfRule rule;

    RuleElementType(String elementType, BnfRule rule, Language language) {
      super(elementType, language, null, false);
      this.rule = rule;
    }

  }

  // Expression Generator Helper part
  private boolean generateExpressionRoot(PsiBuilder builder, int level, ExpressionHelper.ExpressionInfo info, int priority_) {
    Map<String, List<ExpressionHelper.OperatorInfo>> opCalls = new LinkedHashMap<String, List<ExpressionHelper.OperatorInfo>>();
    for (BnfRule rule : info.priorityMap.keySet()) {
      ExpressionHelper.OperatorInfo operator = info.operatorMap.get(rule);
      String opCall = getNextName(operator.rule.getName(), 0);
      List<ExpressionHelper.OperatorInfo> list = opCalls.get(opCall);
      if (list == null) opCalls.put(opCall, list = new ArrayList<ExpressionHelper.OperatorInfo>(2));
      list.add(operator);
    }
    Set<String> sortedOpCalls = opCalls.keySet();

    // main entry
    String methodName = info.rootRule.getName();
    String kernelMethodName = getNextName(methodName, 0);
    String frameName = quote(ParserGeneratorUtil.getRuleDisplayName(info.rootRule, true));
    if (!recursion_guard_(builder, level, methodName)) return false;
    //g.generateFirstCheck(info.rootRule, frameName, true);
    PsiBuilder.Marker marker_ = builder.mark();
    boolean result_ = false;
    boolean pinned_ = false;
    enterErrorRecordingSection(builder, level, _SECTION_GENERAL_, frameName);

    boolean first = true;
    for (String opCall : sortedOpCalls) {
      ExpressionHelper.OperatorInfo
        operator = ContainerUtil
        .getFirstItem(ExpressionGeneratorHelper.findOperators(opCalls.get(opCall), ExpressionHelper.OperatorType.ATOM,
                                                              ExpressionHelper.OperatorType.PREFIX));
      if (operator == null) continue;
      if (first || !result_) {
        result_ = generateNodeCall(builder, level, operator.rule, null, operator.rule.getName(), Collections.<String, Parser>emptyMap());
      }
      first = false;
    }

    pinned_ = result_;
    result_ = result_ && generateKernelMethod(builder, level + 1, kernelMethodName, info, opCalls, priority_);
    if (!result_ && !pinned_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    result_ = exitErrorRecordingSection(builder, level, result_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  private boolean generateKernelMethod(PsiBuilder builder,
                                      int level,
                                      String methodName,
                                      ExpressionHelper.ExpressionInfo info,
                                      Map<String, List<ExpressionHelper.OperatorInfo>> opCalls,
                                      int priority_) {
    Set<String> sortedOpCalls = opCalls.keySet();

    if (!recursion_guard_(builder, level, methodName)) return false;
    PsiBuilder.Marker marker_ = null;
    boolean result_ = true;
    main: while (true) {
      PsiBuilder.Marker left_marker_ = (PsiBuilder.Marker)builder.getLatestDoneMarker();
      if (!invalid_left_marker_guard_(builder, left_marker_, methodName)) return false;

      boolean first = true;
      for (String opCall : sortedOpCalls) {
        ExpressionHelper.OperatorInfo operator =
          ContainerUtil.getFirstItem(
            ExpressionGeneratorHelper.findOperators(opCalls.get(opCall), ExpressionHelper.OperatorType.BINARY,
                                                    ExpressionHelper.OperatorType.N_ARY,
                                                    ExpressionHelper.OperatorType.POSTFIX));
        if (operator == null) continue;
        int priority = info.getPriority(operator.rule);
        if (first) marker_ = builder.mark();
        first = false;

        if (priority_ <  priority &&
            (operator.substitutor == null || ((LighterASTNode)left_marker_).getTokenType() == getElementType(operator.substitutor)) &&
            generateNodeCall(builder, level, info.rootRule, operator.operator, getNextName(operator.rule.getName(), 0), Collections.<String, Parser>emptyMap())) {

          IElementType elementType = getElementType(operator.rule);
          boolean rightAssociative = ParserGeneratorUtil.getAttribute(operator.rule, KnownAttribute.RIGHT_ASSOCIATIVE);
          if (operator.type == ExpressionHelper.OperatorType.BINARY) {
              result_ = report_error_(builder, generateExpressionRoot(builder, level, info, (rightAssociative ? priority - 1 : priority)));
            if (operator.tail != null) result_ = generateNodeCall(builder, level, operator.rule, operator.tail, getNextName(operator.rule.getName(), 1), Collections.<String, Parser>emptyMap()) && result_;
          }
          else if (operator.type == ExpressionHelper.OperatorType.N_ARY) {
            while (true) {
              result_ = report_error_(builder, generateExpressionRoot(builder, level, info, priority));
              if (operator.tail != null) result_ = report_error_(builder, generateNodeCall(builder, level, operator.rule, operator.tail, getNextName(operator.rule.getName(), 1), Collections.<String, Parser>emptyMap())) && result_;
              if (!generateNodeCall(builder, level, info.rootRule, operator.operator, getNextName(operator.rule.getName(), 0), Collections.<String, Parser>emptyMap())) break;
            }
          }
          else if (operator.type == ExpressionHelper.OperatorType.POSTFIX) {
            result_ = true;
          }
          marker_.drop();
          left_marker_.precede().done(elementType);
          continue main;
        }
      }
      if (first) {
        // no BINARY or POSTFIX operators present
        break;
      }
      else {
        marker_.rollbackTo();
        break;
      }
    }
    return result_;
  }
}
