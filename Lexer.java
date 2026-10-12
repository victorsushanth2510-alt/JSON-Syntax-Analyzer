import java.util.ArrayList;
import java.util.List;

public class Lexer {

    public static class Token {
        private final String type;
        private final String value;
        private final String decodedValue;
        private final int line;
        private final int column;
        private final int startIndex;
        private final int endIndex;

        public Token(String type, String value, String decodedValue, int line, int column, int startIndex, int endIndex) {
            this.type = type;
            this.value = value;
            this.decodedValue = decodedValue;
            this.line = line;
            this.column = column;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public String getDecodedValue() {
            return decodedValue;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        @Override
        public String toString() {
            return type + " (" + value + ") at Line: " + line + ", Column: " + column;
        }
    }

    private final String input;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    public Lexer(String input) {
        this.input = input == null ? "" : input;
    }

    private char current() {
        return pos < input.length() ? input.charAt(pos) : '\0';
    }

    private char peek(int offset) {
        int index = pos + offset;
        return index < input.length() ? input.charAt(index) : '\0';
    }

    private void move() {
        if (pos >= input.length()) {
            return;
        }
        char currentChar = input.charAt(pos);
        if (currentChar == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        pos++;
    }

    private void moveCount(int count) {
        for (int i = 0; i < count; i++) {
            move();
        }
    }

    private boolean isJsonWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean isLiteralBoundaryAfter(int index) {
        if (index >= input.length()) {
            return true;
        }
        char next = input.charAt(index);
        return isJsonWhitespace(next) || "{}[],:".indexOf(next) >= 0;
    }

    public List<Token> tokenize() throws Exception {
        List<Token> tokens = new ArrayList<>();
        pos = 0;
        line = 1;
        column = 1;

        while (pos < input.length()) {
            char c = current();
            if (isJsonWhitespace(c)) {
                move();
                continue;
            }

            int startLine = line;
            int startColumn = column;
            int startIndex = pos;

            if ("{}[],:".indexOf(c) >= 0) {
                tokens.add(new Token(String.valueOf(c), String.valueOf(c), String.valueOf(c), startLine, startColumn, startIndex, pos));
                move();
            } else if (c == '"') {
                tokens.add(readString(startLine, startColumn, startIndex));
            } else if (c == '-' || isDigit(c)) {
                tokens.add(readNumber(startLine, startColumn, startIndex));
            } else if (input.startsWith("true", pos) && isLiteralBoundaryAfter(pos + 4)) {
                tokens.add(new Token("TRUE", "true", "true", startLine, startColumn, startIndex, pos + 4));
                moveCount(4);
            } else if (input.startsWith("false", pos) && isLiteralBoundaryAfter(pos + 5)) {
                tokens.add(new Token("FALSE", "false", "false", startLine, startColumn, startIndex, pos + 5));
                moveCount(5);
            } else if (input.startsWith("null", pos) && isLiteralBoundaryAfter(pos + 4)) {
                tokens.add(new Token("NULL", "null", "null", startLine, startColumn, startIndex, pos + 4));
                moveCount(4);
            } else if (isIdentifierChar(c)) {
                throw error("Invalid literal or identifier", line, column);
            } else {
                throw error("Invalid character '" + printable(c) + "'", line, column);
            }
        }

        tokens.add(new Token("EOF", "", "", line, column, pos, pos));
        return tokens;
    }

    private Token readString(int startLine, int startColumn, int startIndex) throws Exception {
        int stringStart = pos;
        StringBuilder builder = new StringBuilder();
        move();

        while (pos < input.length()) {
            char c = current();
            if (c == '"') {
                move();
                String lexeme = input.substring(stringStart, pos);
                return new Token("STRING", lexeme, builder.toString(), startLine, startColumn, startIndex, pos);
            }

            if (c < 0x20) {
                throw error("Unescaped control character in string", line, column);
            }

            if (c == '\\') {
                move();
                if (pos >= input.length()) {
                    throw error("Unterminated escape sequence", line, column);
                }
                char escape = current();
                switch (escape) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escape);
                        move();
                        break;
                    case 'b':
                        builder.append('\b');
                        move();
                        break;
                    case 'f':
                        builder.append('\f');
                        move();
                        break;
                    case 'n':
                        builder.append('\n');
                        move();
                        break;
                    case 'r':
                        builder.append('\r');
                        move();
                        break;
                    case 't':
                        builder.append('\t');
                        move();
                        break;
                    case 'u':
                        move();
                        char[] hex = new char[4];
                        for (int i = 0; i < 4; i++) {
                            if (pos >= input.length() || !isHexDigit(current())) {
                                throw error("Unicode escape must contain four hexadecimal digits", line, column);
                            }
                            hex[i] = current();
                            move();
                        }
                        int codePoint = Integer.parseInt(new String(hex), 16);
                        if (Character.isSupplementaryCodePoint(codePoint)) {
                            builder.append(Character.toChars(codePoint));
                        } else {
                            builder.append((char) codePoint);
                        }
                        break;
                    default:
                        throw error("Invalid escape sequence", line, column);
                }
            } else {
                builder.append(c);
                move();
            }
        }

        throw error("Unterminated string", startLine, startColumn);
    }

    private Token readNumber(int startLine, int startColumn, int startIndex) throws Exception {
        int numberStart = pos;
        StringBuilder builder = new StringBuilder();

        if (current() == '-') {
            builder.append('-');
            move();
            if (pos >= input.length() || !isDigit(current())) {
                throw error("Expected a digit after minus sign", line, column);
            }
        }

        if (current() == '0') {
            builder.append('0');
            move();
            if (pos < input.length() && isDigit(current())) {
                throw error("Leading zeros are not allowed in JSON numbers", line, column);
            }
        } else {
            while (pos < input.length() && isDigit(current())) {
                builder.append(current());
                move();
            }
        }

        if (pos < input.length() && current() == '.') {
            builder.append('.');
            move();
            if (pos >= input.length() || !isDigit(current())) {
                throw error("Incomplete decimal number", line, column);
            }
            while (pos < input.length() && isDigit(current())) {
                builder.append(current());
                move();
            }
        }

        if (pos < input.length() && (current() == 'e' || current() == 'E')) {
            builder.append(current());
            move();
            if (pos < input.length() && (current() == '+' || current() == '-')) {
                builder.append(current());
                move();
            }
            if (pos >= input.length() || !isDigit(current())) {
                throw error("Incomplete exponent in number", line, column);
            }
            while (pos < input.length() && isDigit(current())) {
                builder.append(current());
                move();
            }
        }

        String raw = input.substring(numberStart, pos);
        if (!isValidNumberLiteral(raw)) {
            throw error("Invalid number format", line, column);
        }

        return new Token("NUMBER", raw, raw, startLine, startColumn, startIndex, pos);
    }

    private boolean isValidNumberLiteral(String literal) {
        if (literal == null || literal.isEmpty()) {
            return false;
        }

        if (literal.equals("-") || literal.equals("-.") || literal.equals("-e") || literal.equals("-E")) {
            return false;
        }

        if (!literal.matches("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")) {
            return false;
        }

        if (literal.startsWith("-0") && literal.length() > 2 && literal.charAt(2) != '.') {
            return false;
        }

        if (literal.contains("e") || literal.contains("E")) {
            String[] parts = literal.split("[eE]");
            if (parts.length != 2) {
                return false;
            }
            if (parts[1].isEmpty() || parts[1].equals("+") || parts[1].equals("-")) {
                return false;
            }
        }

        return true;
    }

    private String printable(char c) {
        if (c == '\0') {
            return "end of input";
        }
        if (c == '\n') {
            return "newline";
        }
        if (c == '\r') {
            return "carriage return";
        }
        if (c == '\t') {
            return "tab";
        }
        return String.valueOf(c);
    }

    private Exception error(String message, int errorLine, int errorColumn) {
        return new Exception(message + " at Line " + errorLine + ", Column " + errorColumn);
    }
}
