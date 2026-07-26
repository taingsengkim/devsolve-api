package kh.edu.istad.ite.devsoleapi.feature.comment;


public enum CommentableType {

    REPORT("report"),
    SOLUTION("solution"),
    PROGRAM("program"),
    PROBLEM("problem");

    private final String value;

    CommentableType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CommentableType fromValue(String value) {
        for (CommentableType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid commentable type: " + value);
    }
}