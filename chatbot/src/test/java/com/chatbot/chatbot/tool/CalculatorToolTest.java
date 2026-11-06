package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesArithmeticWithPrecedenceAndParentheses() throws Exception {
        ToolResult result = execute("2 + 3 * (4 - 1)");
        assertTrue(result.ok());
        assertEquals("11", result.data().get("result"));
    }

    @Test
    void dividesWithScaleAndStripsTrailingZeros() throws Exception {
        ToolResult result = execute("1 / 4");
        assertTrue(result.ok());
        assertEquals("0.25", result.data().get("result"));
    }

    @Test
    void rejectsDivisionByZeroAndInvalidExpression() throws Exception {
        assertFalse(execute("1 / 0").ok());
        assertFalse(execute("2 + abc").ok());
        assertFalse(execute("").ok());
    }

    private ToolResult execute(String expression) throws Exception {
        var args = objectMapper.readTree(objectMapper.writeValueAsString(
                java.util.Map.of("expression", expression)));
        return tool.execute(new ToolContext(new CurrentUser(1L, "test", 0, null, true), 1L, "call_1"), args);
    }
}
