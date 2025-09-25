/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.googlejavaformat.java.javadoc;

import static com.google.googlejavaformat.java.javadoc.JavadocLexer.lex;
import static com.google.googlejavaformat.java.javadoc.Token.Type.BR_TAG;
import static com.google.googlejavaformat.java.javadoc.Token.Type.PARAGRAPH_OPEN_TAG;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

import com.google.common.collect.ImmutableList;
import com.google.googlejavaformat.java.javadoc.JavadocLexer.LexException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry point for formatting Javadoc.
 *
 * <p>This stateless class reads tokens from the stateful lexer and translates them to "requests"
 * and "writes" to the stateful writer. It also munges tokens into "standardized" forms. Finally, it
 * performs postprocessing to convert the written Javadoc to a one-liner if possible or to leave a
 * single blank line if it's empty.
 */
public final class JavadocFormatter {

  static final int MAX_LINE_LENGTH = 100;

  /**
   * Formats the given Javadoc comment, which must start with ∕✱✱ and end with ✱∕. The output will
   * start and end with the same characters.
   */
  public static String formatJavadoc(String input, int blockIndent) {
    ImmutableList<Token> tokens;
    try {
      tokens = lex(input);
    } catch (LexException e) {
      return input;
    }
    String result = render(tokens, blockIndent);
    return makeSingleLineIfPossible(blockIndent, result);
  }

  private static String render(List<Token> input, int blockIndent) {
    JavadocWriter output = new JavadocWriter(blockIndent);
    for (Token token : input) {
      switch (token.getType()) {
        case BEGIN_JAVADOC -> output.writeBeginJavadoc();
        case END_JAVADOC -> {
          output.writeEndJavadoc();
          return output.toString();
        }
        case FOOTER_JAVADOC_TAG_START -> output.writeFooterJavadocTagStart(token);
        case SNIPPET_BEGIN -> output.writeSnippetBegin(token);
        case SNIPPET_END -> output.writeSnippetEnd(token);
        case LIST_OPEN_TAG -> output.writeListOpen(token);
        case LIST_CLOSE_TAG -> output.writeListClose(token);
        case LIST_ITEM_OPEN_TAG -> output.writeListItemOpen(token);
        case HEADER_OPEN_TAG -> output.writeHeaderOpen(token);
        case HEADER_CLOSE_TAG -> output.writeHeaderClose(token);
        case PARAGRAPH_OPEN_TAG -> output.writeParagraphOpen(standardizePToken(token));
        case BLOCKQUOTE_OPEN_TAG, BLOCKQUOTE_CLOSE_TAG -> output.writeBlockquoteOpenOrClose(token);
        case PRE_OPEN_TAG -> output.writePreOpen(token);
        case PRE_CLOSE_TAG -> output.writePreClose(token);
        case CODE_OPEN_TAG -> output.writeCodeOpen(token);
        case CODE_CLOSE_TAG -> output.writeCodeClose(token);
        case TABLE_OPEN_TAG -> output.writeTableOpen(token);
        case TABLE_CLOSE_TAG -> output.writeTableClose(token);
        case MOE_BEGIN_STRIP_COMMENT -> output.requestMoeBeginStripComment(token);
        case MOE_END_STRIP_COMMENT -> output.writeMoeEndStripComment(token);
        case HTML_COMMENT -> output.writeHtmlComment(token);
        case BR_TAG -> output.writeBr(standardizeBrToken(token));
        case WHITESPACE -> output.requestWhitespace();
        case FORCED_NEWLINE -> output.writeLineBreakNoAutoIndent();
        case LITERAL -> output.writeLiteral(token);
        case PARAGRAPH_CLOSE_TAG, LIST_ITEM_CLOSE_TAG, OPTIONAL_LINE_BREAK -> {}
      }
    }
    throw new AssertionError();
  }

  /*
   * TODO(cpovirk): Is this really the right location for the standardize* methods? Maybe the lexer
   * should include them as part of its own postprocessing? Or even the writer could make sense.
   */

  private static Token standardizeBrToken(Token token) {
    return standardize(token, STANDARD_BR_TOKEN);
  }

  private static Token standardizePToken(Token token) {
    return standardize(token, STANDARD_P_TOKEN);
  }

  private static Token standardize(Token token, Token standardToken) {
    return SIMPLE_TAG_PATTERN.matcher(token.getValue()).matches() ? standardToken : token;
  }

  private static final Token STANDARD_BR_TOKEN = new Token(BR_TAG, "<br>");
  private static final Token STANDARD_P_TOKEN = new Token(PARAGRAPH_OPEN_TAG, "<p>");
  private static final Pattern SIMPLE_TAG_PATTERN = compile("^<\\w+\\s*/?\\s*>", CASE_INSENSITIVE);

  private static final Pattern ONE_CONTENT_LINE_PATTERN = compile(" */[*][*]\n *[*] (.*)\n *[*]/");

  /**
   * Returns the given string or a one-line version of it (e.g., "∕✱✱ Tests for foos. ✱∕") if it
   * fits on one line.
   */
  private static String makeSingleLineIfPossible(int blockIndent, String input) {
    Matcher matcher = ONE_CONTENT_LINE_PATTERN.matcher(input);
    if (matcher.matches()) {
      String line = matcher.group(1);
      if (line.isEmpty()) {
        return "/** */";
      } else if (oneLineJavadoc(line, blockIndent)) {
        return "/** " + line + " */";
      }
    }
    return input;
  }

  private static boolean oneLineJavadoc(String line, int blockIndent) {
    int oneLinerContentLength = MAX_LINE_LENGTH - "/**  */".length() - blockIndent;
    if (line.length() > oneLinerContentLength) {
      return false;
    }
    // If the javadoc contains only a tag, use multiple lines to encourage writing a summary
    // fragment, unless it's /* @hide */.
    if (line.startsWith("@") && !line.equals("@hide")) {
      return false;
    }
    return true;
  }

  private JavadocFormatter() {}
}
