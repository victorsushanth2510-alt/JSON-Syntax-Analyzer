import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Parser {

    public static class Statistics {
        public int totalObjects;
        public int totalArrays;
        public int totalObjectMembers;
        public int totalObjectKeys;
        public int totalArrayElements;
        public int totalStringValues;
        public int totalNumericValues;
        public int totalBooleanValues;
        public int totalNullValues;
        public int totalPrimitiveValues;
        public int emptyObjects;
        public int emptyArrays;
        public int maxNestingDepth;
        public int totalTokens;
        public int totalInputCharacters;
        public int totalNonWhitespaceCharacters;
        public int maxArrayLength;
        public int maxObjectMembers;
    }

    private final List<Lexer.Token> tokens;
    private final String sourceText;
    private int pos = 0;
    private JsonNode rootNode;
    private final List<String> duplicateKeyWarnings = new ArrayList<>();

    public Parser(List<Lexer.Token> tokens) {
        this.tokens = tokens == null ? new ArrayList<>() : tokens;
        this.sourceText = this.tokens.isEmpty() ? "" : tokens.stream().map(t -> t.getValue()).reduce("", (a, b) -> a + b);
    }

    public JsonNode parse() throws Exception {
        if (tokens.isEmpty()) {
            throw syntaxError(null, "No JSON tokens were produced");
        }
        pos = 0;
        duplicateKeyWarnings.clear();
        rootNode = value(0);
        if (!current().getType().equals("EOF")) {
            throw syntaxError(current(), "Unexpected content after the root JSON value");
        }
        return rootNode;
    }

    public JsonNode getRootNode() {
        return rootNode;
    }

    public List<String> getDuplicateKeyWarnings() {
        return duplicateKeyWarnings;
    }

    public String prettyPrint() throws Exception {
        if (rootNode == null) {
            throw new Exception("No parsed JSON available to format.");
        }
        return formatNode(rootNode, 0, true).toString();
    }

    public String minify() throws Exception {
        if (rootNode == null) {
            throw new Exception("No parsed JSON available to minify.");
        }
        return formatNode(rootNode, 0, false).toString();
    }

    public Object findValueByPath(String path) throws Exception {
        if (rootNode == null) {
            throw new Exception("No parsed JSON available.");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new Exception("JSON path is empty.");
        }

        String trimmed = path.trim();
        if (!trimmed.startsWith("$")) {
            throw new Exception("Unsupported JSON path. Use a root-based path such as $.student.name or $.students[0].name.");
        }

        if (trimmed.equals("$")) {
            return rootNode;
        }

        String remaining = trimmed.substring(1);
        JsonNode current = rootNode;
        int i = 0;
        while (i < remaining.length()) {
            if (remaining.charAt(i) == '.') {
                i++;
                if (i >= remaining.length()) {
                    throw new Exception("Invalid path segment after '.'.");
                }
                StringBuilder key = new StringBuilder();
                while (i < remaining.length() && remaining.charAt(i) != '.' && remaining.charAt(i) != '[') {
                    key.append(remaining.charAt(i));
                    i++;
                }
                if (key.length() == 0) {
                    throw new Exception("Invalid property name in path.");
                }
                if (current == null || !current.isObject()) {
                    throw new Exception("Path segment '" + key + "' cannot be accessed because the current node is not an object.");
                }
                boolean found = false;
                for (JsonNode child : current.getChildren()) {
                    if (key.toString().equals(child.getObjectKey())) {
                        current = child;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new Exception("Property '" + key + "' was not found in the current object.");
                }
                continue;
            }

            if (remaining.charAt(i) == '[') {
                int end = remaining.indexOf(']', i);
                if (end < 0) {
                    throw new Exception("Missing closing bracket in JSON path.");
                }
                String indexToken = remaining.substring(i + 1, end).trim();
                if (indexToken.isEmpty()) {
                    throw new Exception("Array index is missing.");
                }
                if (!indexToken.matches("\\d+")) {
                    throw new Exception("Unsupported JSON path syntax: only numeric array indices are supported.");
                }
                int index = Integer.parseInt(indexToken);
                if (current == null || !current.isArray()) {
                    throw new Exception("Array index '" + index + "' cannot be used because the current node is not an array.");
                }
                if (index < 0 || index >= current.getChildren().size()) {
                    throw new Exception("Array index '" + index + "' is out of range.");
                }
                current = current.getChildren().get(index);
                i = end + 1;
                continue;
            }

            throw new Exception("Unsupported JSON path syntax near '" + remaining.substring(i) + "'. Only dot notation and numeric array indices are supported.");
        }

        return current;
    }

    public Statistics getStatistics() {
        Statistics stats = new Statistics();
        stats.totalTokens = tokens.size() - 1;
        stats.totalInputCharacters = sourceText.length();
        stats.totalNonWhitespaceCharacters = sourceText.replaceAll("\\s+", "").length();
        if (rootNode == null) {
            return stats;
        }

        collectStats(rootNode, 0, stats);
        return stats;
    }

    private void collectStats(JsonNode node, int depth, Statistics stats) {
        stats.maxNestingDepth = Math.max(stats.maxNestingDepth, depth);

        if (node.isObject()) {
            stats.totalObjects++;
            int memberCount = node.getChildren().size();
            stats.totalObjectMembers += memberCount;
            stats.totalObjectKeys += memberCount;
            stats.maxObjectMembers = Math.max(stats.maxObjectMembers, memberCount);
            if (memberCount == 0) {
                stats.emptyObjects++;
            }
            for (JsonNode child : node.getChildren()) {
                collectStats(child, depth + 1, stats);
            }
            return;
        }

        if (node.isArray()) {
            stats.totalArrays++;
            int arrayLength = node.getChildren().size();
            stats.totalArrayElements += arrayLength;
            stats.maxArrayLength = Math.max(stats.maxArrayLength, arrayLength);
            if (arrayLength == 0) {
                stats.emptyArrays++;
            }
            for (JsonNode child : node.getChildren()) {
                collectStats(child, depth + 1, stats);
            }
            return;
        }

        stats.totalPrimitiveValues++;
        switch (node.getType()) {
            case STRING:
                stats.totalStringValues++;
                break;
            case NUMBER:
                stats.totalNumericValues++;
                break;
            case BOOLEAN:
                stats.totalBooleanValues++;
                break;
            case NULL:
                stats.totalNullValues++;
                break;
            default:
                break;
        }
    }

    private Lexer.Token current() {
        return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size() - 1);
    }

    private void next() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
    }

    private void expect(String expectedType) throws Exception {
        Lexer.Token token = current();
        if (!token.getType().equals(expectedType)) {
            throw syntaxError(token, "Expected '" + expectedType + "' but found '" + token.getType() + "'");
        }
        next();
    }

    private boolean match(String expectedType) {
        if (current().getType().equals(expectedType)) {
            next();
            return true;
        }
        return false;
    }

    private JsonNode value(int depth) throws Exception {
        Lexer.Token token = current();
        if (token == null) {
            throw new Exception("Unexpected end of input while reading a JSON value.");
        }

        switch (token.getType()) {
            case "STRING":
                next();
                return new JsonNode(JsonNode.NodeType.STRING, token.getDecodedValue(), token.getValue(), token.getLine(), token.getColumn());
            case "NUMBER":
                next();
                return new JsonNode(JsonNode.NodeType.NUMBER, token.getValue(), token.getValue(), token.getLine(), token.getColumn());
            case "TRUE":
            case "FALSE":
                next();
                return new JsonNode(JsonNode.NodeType.BOOLEAN, token.getDecodedValue(), token.getValue(), token.getLine(), token.getColumn());
            case "NULL":
                next();
                return new JsonNode(JsonNode.NodeType.NULL, "null", token.getValue(), token.getLine(), token.getColumn());
            case "{":
                return object(depth);
            case "[":
                return array(depth);
            default:
                throw syntaxError(token, "Expected a JSON value but found '" + token.getType() + "'");
        }
    }

    private JsonNode object(int depth) throws Exception {
        Lexer.Token startToken = current();
        expect("{");
        JsonNode objectNode = new JsonNode(JsonNode.NodeType.OBJECT, "{}", "{}", startToken.getLine(), startToken.getColumn());
        Set<String> seenKeys = new HashSet<>();

        if (match("}")) {
            return objectNode;
        }

        while (true) {
            Lexer.Token keyToken = current();
            if (!keyToken.getType().equals("STRING")) {
                throw syntaxError(keyToken, "Object key must be a string.");
            }

            String key = keyToken.getDecodedValue();
            if (seenKeys.contains(key)) {
                duplicateKeyWarnings.add("Duplicate key '" + key + "' at line " + keyToken.getLine() + ", column " + keyToken.getColumn());
            }
            seenKeys.add(key);
            next();
            expect(":");
            JsonNode memberNode = value(depth + 1);
            memberNode.setObjectKey(key);
            objectNode.addChild(memberNode);

            if (match(",")) {
                if (match("}")) {
                    throw syntaxError(current(), "Trailing comma is not allowed in an object.");
                }
                continue;
            }
            break;
        }

        expect("}");
        return objectNode;
    }

    private JsonNode array(int depth) throws Exception {
        Lexer.Token startToken = current();
        expect("[");
        JsonNode arrayNode = new JsonNode(JsonNode.NodeType.ARRAY, "[]", "[]", startToken.getLine(), startToken.getColumn());

        if (match("]")) {
            return arrayNode;
        }

        int index = 0;
        while (true) {
            JsonNode element = value(depth + 1);
            element.setIndex(index);
            arrayNode.addChild(element);
            index++;

            if (match(",")) {
                if (match("]")) {
                    throw syntaxError(current(), "Trailing comma is not allowed in an array.");
                }
                continue;
            }
            break;
        }

        expect("]");
        return arrayNode;
    }

    private Exception syntaxError(Lexer.Token token, String message) {
        if (token == null) {
            return new Exception(message);
        }
        return new Exception(message + " at Line " + token.getLine() + ", Column " + token.getColumn());
    }

    private StringBuilder formatNode(JsonNode node, int depth, boolean pretty) {
        StringBuilder builder = new StringBuilder();

        if (node.isObject()) {
            builder.append('{');
            if (!node.getChildren().isEmpty()) {
                if (pretty) {
                    builder.append('\n');
                }
                for (int i = 0; i < node.getChildren().size(); i++) {
                    JsonNode child = node.getChildren().get(i);
                    if (pretty) {
                        builder.append(indent(depth + 1));
                    }
                    builder.append('"').append(escapeString(child.getObjectKey())).append('"')
                            .append(':');
                    if (pretty) {
                        builder.append(' ');
                    }
                    builder.append(formatNode(child, depth + 1, pretty));
                    if (i < node.getChildren().size() - 1) {
                        builder.append(',');
                    }
                    if (pretty) {
                        builder.append('\n');
                    }
                }
                if (pretty) {
                    builder.append(indent(depth));
                }
            }
            builder.append('}');
            return builder;
        }

        if (node.isArray()) {
            builder.append('[');
            if (!node.getChildren().isEmpty()) {
                if (pretty) {
                    builder.append('\n');
                }
                for (int i = 0; i < node.getChildren().size(); i++) {
                    JsonNode child = node.getChildren().get(i);
                    if (pretty) {
                        builder.append(indent(depth + 1));
                    }
                    builder.append(formatNode(child, depth + 1, pretty));
                    if (i < node.getChildren().size() - 1) {
                        builder.append(',');
                    }
                    if (pretty) {
                        builder.append('\n');
                    }
                }
                if (pretty) {
                    builder.append(indent(depth));
                }
            }
            builder.append(']');
            return builder;
        }

        if (node.getType() == JsonNode.NodeType.STRING) {
            return new StringBuilder().append('"').append(escapeString(node.getValue())).append('"');
        }

        if (node.getType() == JsonNode.NodeType.NUMBER || node.getType() == JsonNode.NodeType.BOOLEAN || node.getType() == JsonNode.NodeType.NULL) {
            return new StringBuilder(node.getValue());
        }

        return new StringBuilder(node.getValue());
    }

    private String indent(int depth) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            result.append("    ");
        }
        return result.toString();
    }

    private String escapeString(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}
