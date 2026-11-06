package com.chatbot.chatbot.tool;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 安全四则运算：只支持数字、括号、+ - * /，不执行任意代码。 */
@Component
public class CalculatorTool implements Tool {

    private static final int MAX_EXPRESSION_LENGTH = 200;

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String label() {
        return "计算表达式";
    }

    @Override
    public String description() {
        return "当用户需要精确计算四则运算（加、减、乘、除、括号）时调用。"
                + "不要用于解方程、单位换算或需要外部数据的计算。参数 expression 必填。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expression", Map.<String, Object>of(
                "type", "string",
                "description", "只包含数字、括号和 + - * / 的表达式"));
        return Map.<String, Object>of(
                "type", "object",
                "properties", properties,
                "required", List.of("expression"));
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments) {
        String expression = arguments.path("expression").isTextual()
                ? arguments.path("expression").asText().strip() : "";
        if (expression.isEmpty()) {
            return ToolResult.error("expression 不能为空");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            return ToolResult.error("expression 不能超过 " + MAX_EXPRESSION_LENGTH + " 个字符");
        }
        try {
            BigDecimal result = new Parser(expression).parse();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("expression", expression);
            data.put("result", result.toPlainString());
            return ToolResult.ok(expression + " = " + result.toPlainString(), data);
        } catch (ArithmeticException | IllegalArgumentException e) {
            return ToolResult.error("计算失败: " + e.getMessage());
        }
    }

    /** 递归下降解析，避免用脚本引擎执行任意表达式。 */
    private static final class Parser {

        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        BigDecimal parse() {
            BigDecimal value = expression();
            skipSpace();
            if (pos != text.length()) {
                throw new IllegalArgumentException("无法识别的字符: " + text.charAt(pos));
            }
            return value;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            for (;;) {
                skipSpace();
                if (take('+')) {
                    value = value.add(term());
                } else if (take('-')) {
                    value = value.subtract(term());
                } else {
                    return value;
                }
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            for (;;) {
                skipSpace();
                if (take('*')) {
                    value = value.multiply(factor());
                } else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.signum() == 0) {
                        throw new ArithmeticException("除数不能为 0");
                    }
                    value = value.divide(divisor, 10, RoundingMode.HALF_UP).stripTrailingZeros();
                } else {
                    return value;
                }
            }
        }

        private BigDecimal factor() {
            skipSpace();
            if (take('+')) {
                return factor();
            }
            if (take('-')) {
                return factor().negate();
            }
            if (take('(')) {
                BigDecimal value = expression();
                if (!take(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }
            return number();
        }

        private BigDecimal number() {
            skipSpace();
            int start = pos;
            boolean dot = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("缺少数字");
            }
            return new BigDecimal(text.substring(start, pos));
        }

        private boolean take(char expected) {
            skipSpace();
            if (pos < text.length() && text.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
