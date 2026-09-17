package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
public record MethodCallExpr(SourcePosition position, ExpressionNode receiver,
                      String methodName, List<String> typeArguments,
                      List<ExpressionNode> arguments) implements ExpressionNode {
    public MethodCallExpr {
        arguments = new ArrayList<>(arguments);
    }
}
