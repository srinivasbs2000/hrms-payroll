package com.acme.hrms.payroll.compensation.internal.formula;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parser and evaluator for the payroll formula language.
 *
 * <p>The language deliberately supports only decimal literals, component-code references,
 * parentheses, +, -, *, / and ABS, MIN, MAX and ROUND. It never delegates to a scripting,
 * expression, SQL or reflection engine.
 */
public final class RestrictedFormulaCompiler {
  private static final int MAX_EXPRESSION_LENGTH = 1000;
  private static final int MAX_TOKENS = 256;
  private static final int MAX_DEPTH = 32;
  private static final int MAX_DEPENDENCIES = 64;
  private static final Set<String> FUNCTIONS = Set.of("ABS", "MIN", "MAX", "ROUND");

  public CompiledFormula compile(String expression) {
    if (expression == null || expression.isBlank()) {
      throw error("FORMULA_REQUIRED", 0, "formula expression is required");
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      throw error("FORMULA_TOO_LONG", MAX_EXPRESSION_LENGTH, "maximum length is 1000");
    }
    Parser parser = new Parser(tokenize(expression));
    Node root = parser.parse();
    LinkedHashSet<String> dependencies = new LinkedHashSet<>();
    root.collectDependencies(dependencies);
    if (dependencies.size() > MAX_DEPENDENCIES) {
      throw error("TOO_MANY_DEPENDENCIES", 0, "maximum dependency count is 64");
    }
    return new CompiledFormula(root.canonical(), dependencies, root);
  }

  private List<Token> tokenize(String expression) {
    List<Token> tokens = new ArrayList<>();
    int index = 0;
    while (index < expression.length()) {
      char current = expression.charAt(index);
      if (Character.isWhitespace(current)) {
        index++;
        continue;
      }
      if (tokens.size() >= MAX_TOKENS) {
        throw error("TOO_MANY_TOKENS", index, "maximum token count is 256");
      }
      TokenType simple = switch (current) {
        case '+' -> TokenType.PLUS;
        case '-' -> TokenType.MINUS;
        case '*' -> TokenType.STAR;
        case '/' -> TokenType.SLASH;
        case '(' -> TokenType.LEFT_PAREN;
        case ')' -> TokenType.RIGHT_PAREN;
        case ',' -> TokenType.COMMA;
        default -> null;
      };
      if (simple != null) {
        tokens.add(new Token(simple, Character.toString(current), index));
        index++;
        continue;
      }
      if (Character.isDigit(current)) {
        int start = index;
        boolean decimalPoint = false;
        while (index < expression.length()) {
          char candidate = expression.charAt(index);
          if (candidate == '.') {
            if (decimalPoint) {
              throw error("INVALID_NUMBER", index, "multiple decimal points");
            }
            decimalPoint = true;
            index++;
          } else if (Character.isDigit(candidate)) {
            index++;
          } else {
            break;
          }
        }
        String text = expression.substring(start, index);
        if (text.endsWith(".")) {
          throw error("INVALID_NUMBER", index - 1, "fractional digits are required");
        }
        BigDecimal value;
        try {
          value = new BigDecimal(text);
        } catch (NumberFormatException exception) {
          throw error("INVALID_NUMBER", start, "invalid decimal literal");
        }
        if (value.precision() > 19 || value.scale() > 10) {
          throw error("NUMBER_OUT_OF_RANGE", start, "maximum precision is 19 and scale is 10");
        }
        tokens.add(new Token(TokenType.NUMBER, text, start));
        continue;
      }
      if (Character.isLetter(current) || current == '_') {
        int start = index;
        while (index < expression.length()) {
          char candidate = expression.charAt(index);
          if (!Character.isLetterOrDigit(candidate) && candidate != '_') {
            break;
          }
          index++;
        }
        String text = expression.substring(start, index).toUpperCase(Locale.ROOT);
        if (!text.matches("^[A-Z][A-Z0-9_]{1,39}$")) {
          throw error("INVALID_IDENTIFIER", start, "component codes use 2-40 uppercase characters");
        }
        tokens.add(new Token(TokenType.IDENTIFIER, text, start));
        continue;
      }
      throw error("UNSUPPORTED_TOKEN", index, "unsupported character");
    }
    tokens.add(new Token(TokenType.END, "", expression.length()));
    return tokens;
  }

  private static FormulaCompilationException error(String code, int position, String detail) {
    return new FormulaCompilationException(code, position, detail);
  }

  private enum TokenType {
    NUMBER, IDENTIFIER, PLUS, MINUS, STAR, SLASH, LEFT_PAREN, RIGHT_PAREN, COMMA, END
  }

  private record Token(TokenType type, String text, int position) {}

  interface Node {
    BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context);
    String canonical();
    void collectDependencies(LinkedHashSet<String> dependencies);
  }

  private record NumberNode(BigDecimal value) implements Node {
    @Override public BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context) {
      return value;
    }
    @Override public String canonical() { return value.stripTrailingZeros().toPlainString(); }
    @Override public void collectDependencies(LinkedHashSet<String> dependencies) {}
  }

  private record ReferenceNode(String code, int position) implements Node {
    @Override public BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context) {
      BigDecimal value = values.get(code);
      if (value == null) {
        throw error("MISSING_COMPONENT_VALUE", position, "no value supplied for " + code);
      }
      return value;
    }
    @Override public String canonical() { return code; }
    @Override public void collectDependencies(LinkedHashSet<String> dependencies) {
      dependencies.add(code);
    }
  }

  private record UnaryNode(Node operand) implements Node {
    @Override public BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context) {
      return operand.evaluate(values, context).negate(context);
    }
    @Override public String canonical() { return "(-" + operand.canonical() + ")"; }
    @Override public void collectDependencies(LinkedHashSet<String> dependencies) {
      operand.collectDependencies(dependencies);
    }
  }

  private record BinaryNode(Token operator, Node left, Node right) implements Node {
    @Override public BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context) {
      BigDecimal first = left.evaluate(values, context);
      BigDecimal second = right.evaluate(values, context);
      return switch (operator.type()) {
        case PLUS -> first.add(second, context);
        case MINUS -> first.subtract(second, context);
        case STAR -> first.multiply(second, context);
        case SLASH -> {
          if (second.signum() == 0) {
            throw error("DIVISION_BY_ZERO", operator.position(), "divisor evaluated to zero");
          }
          yield first.divide(second, context);
        }
        default -> throw new IllegalStateException("Unsupported binary operator");
      };
    }
    @Override public String canonical() {
      return "(" + left.canonical() + operator.text() + right.canonical() + ")";
    }
    @Override public void collectDependencies(LinkedHashSet<String> dependencies) {
      left.collectDependencies(dependencies);
      right.collectDependencies(dependencies);
    }
  }

  private record FunctionNode(Token function, List<Node> arguments) implements Node {
    @Override public BigDecimal evaluate(Map<String, BigDecimal> values, MathContext context) {
      List<BigDecimal> evaluated = arguments.stream()
          .map(argument -> argument.evaluate(values, context))
          .toList();
      return switch (function.text()) {
        case "ABS" -> evaluated.get(0).abs(context);
        case "MIN" -> evaluated.get(0).min(evaluated.get(1));
        case "MAX" -> evaluated.get(0).max(evaluated.get(1));
        case "ROUND" -> {
          int scale;
          try {
            scale = evaluated.get(1).intValueExact();
          } catch (ArithmeticException exception) {
            throw error("INVALID_ROUND_SCALE", function.position(), "ROUND scale must be an integer");
          }
          if (scale < 0 || scale > 10) {
            throw error("INVALID_ROUND_SCALE", function.position(), "ROUND scale must be between 0 and 10");
          }
          yield evaluated.get(0).setScale(scale, RoundingMode.HALF_UP);
        }
        default -> throw new IllegalStateException("Unsupported function");
      };
    }
    @Override public String canonical() {
      return function.text() + "(" + arguments.stream().map(Node::canonical)
          .reduce((left, right) -> left + "," + right).orElse("") + ")";
    }
    @Override public void collectDependencies(LinkedHashSet<String> dependencies) {
      arguments.forEach(argument -> argument.collectDependencies(dependencies));
    }
  }

  private final class Parser {
    private final List<Token> tokens;
    private int current;
    private int depth;

    private Parser(List<Token> tokens) { this.tokens = tokens; }

    private Node parse() {
      Node result = expression();
      if (!check(TokenType.END)) {
        throw error("UNEXPECTED_TOKEN", peek().position(), "unexpected " + peek().text());
      }
      return result;
    }

    private Node expression() {
      Node result = term();
      while (match(TokenType.PLUS, TokenType.MINUS)) {
        Token operator = previous();
        result = new BinaryNode(operator, result, term());
      }
      return result;
    }

    private Node term() {
      Node result = unary();
      while (match(TokenType.STAR, TokenType.SLASH)) {
        Token operator = previous();
        result = new BinaryNode(operator, result, unary());
      }
      return result;
    }

    private Node unary() {
      if (match(TokenType.MINUS)) {
        return new UnaryNode(unary());
      }
      return primary();
    }

    private Node primary() {
      if (++depth > MAX_DEPTH) {
        throw error("FORMULA_TOO_DEEP", peek().position(), "maximum nesting depth is 32");
      }
      try {
        if (match(TokenType.NUMBER)) {
          return new NumberNode(new BigDecimal(previous().text()));
        }
        if (match(TokenType.IDENTIFIER)) {
          Token identifier = previous();
          if (!match(TokenType.LEFT_PAREN)) {
            return new ReferenceNode(identifier.text(), identifier.position());
          }
          if (!FUNCTIONS.contains(identifier.text())) {
            throw error("UNSUPPORTED_FUNCTION", identifier.position(), identifier.text());
          }
          List<Node> arguments = new ArrayList<>();
          if (!check(TokenType.RIGHT_PAREN)) {
            do {
              arguments.add(expression());
            } while (match(TokenType.COMMA));
          }
          consume(TokenType.RIGHT_PAREN, "EXPECTED_RIGHT_PAREN");
          int expected = "ABS".equals(identifier.text()) ? 1 : 2;
          if (arguments.size() != expected) {
            throw error("INVALID_ARGUMENT_COUNT", identifier.position(),
                identifier.text() + " requires " + expected + " argument(s)");
          }
          if ("ROUND".equals(identifier.text())) {
            if (!(arguments.get(1) instanceof NumberNode scaleNode)) {
              throw error("INVALID_ROUND_SCALE", identifier.position(),
                  "ROUND scale must be a literal integer between 0 and 10");
            }
            int scale;
            try {
              scale = scaleNode.value().intValueExact();
            } catch (ArithmeticException exception) {
              throw error("INVALID_ROUND_SCALE", identifier.position(),
                  "ROUND scale must be a literal integer between 0 and 10");
            }
            if (scale < 0 || scale > 10) {
              throw error("INVALID_ROUND_SCALE", identifier.position(),
                  "ROUND scale must be a literal integer between 0 and 10");
            }
          }
          return new FunctionNode(identifier, List.copyOf(arguments));
        }
        if (match(TokenType.LEFT_PAREN)) {
          Node result = expression();
          consume(TokenType.RIGHT_PAREN, "EXPECTED_RIGHT_PAREN");
          return result;
        }
        throw error("EXPECTED_EXPRESSION", peek().position(), "expression expected");
      } finally {
        depth--;
      }
    }

    private void consume(TokenType type, String code) {
      if (!match(type)) {
        throw error(code, peek().position(), type + " expected");
      }
    }

    private boolean match(TokenType... types) {
      for (TokenType type : types) {
        if (check(type)) {
          current++;
          return true;
        }
      }
      return false;
    }

    private boolean check(TokenType type) { return peek().type() == type; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
  }
}
