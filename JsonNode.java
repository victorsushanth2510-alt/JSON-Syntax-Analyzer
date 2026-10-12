import java.util.ArrayList;
import java.util.List;

public class JsonNode {
    public enum NodeType {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    private final NodeType type;
    private final String value;
    private final String rawValue;
    private final int line;
    private final int column;
    private final List<JsonNode> children;
    private final List<String> memberKeys;

    private String objectKey;
    private int index = -1;

    public JsonNode(NodeType type, String value, String rawValue, int line, int column) {
        this.type = type;
        this.value = value;
        this.rawValue = rawValue;
        this.line = line;
        this.column = column;
        this.children = new ArrayList<>();
        this.memberKeys = new ArrayList<>();
    }

    public NodeType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getRawValue() {
        return rawValue;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public List<JsonNode> getChildren() {
        return children;
    }

    public List<String> getMemberKeys() {
        return memberKeys;
    }

    public void addChild(JsonNode child) {
        children.add(child);
        if (type == NodeType.OBJECT) {
            memberKeys.add(child.getObjectKey());
        }
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isObject() {
        return type == NodeType.OBJECT;
    }

    public boolean isArray() {
        return type == NodeType.ARRAY;
    }

    public boolean isPrimitive() {
        return type == NodeType.STRING || type == NodeType.NUMBER || type == NodeType.BOOLEAN || type == NodeType.NULL;
    }

    public String getTypeLabel() {
        switch (type) {
            case OBJECT:
                return "Object";
            case ARRAY:
                return "Array";
            case STRING:
                return "String";
            case NUMBER:
                return "Number";
            case BOOLEAN:
                return "Boolean";
            case NULL:
                return "Null";
            default:
                return "Unknown";
        }
    }

    public String getDisplayName() {
        if (type == NodeType.OBJECT) {
            return "{...}";
        }
        if (type == NodeType.ARRAY) {
            return "[...]";
        }
        if (type == NodeType.STRING) {
            return value;
        }
        return String.valueOf(value);
    }
}
