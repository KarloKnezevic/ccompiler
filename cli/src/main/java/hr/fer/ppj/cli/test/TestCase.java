package hr.fer.ppj.cli.test;

public class TestCase {
    private String id;
    private String path;
    private boolean valid;
    private String category;
    private ExpectedOutput expected;

    public TestCase(String id, String path, boolean valid, String category, ExpectedOutput expected) {
        this.id = id;
        this.path = path;
        this.valid = valid;
        this.category = category;
        this.expected = expected;
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public boolean isValid() {
        return valid;
    }

    public String getCategory() {
        return category;
    }

    public ExpectedOutput getExpected() {
        return expected;
    }

    public static class ExpectedOutput {
        private String returnValue;
        private String errorPattern;

        public ExpectedOutput(String returnValue, String errorPattern) {
            this.returnValue = returnValue;
            this.errorPattern = errorPattern;
        }

        public String getReturnValue() {
            return returnValue;
        }

        public String getErrorPattern() {
            return errorPattern;
        }

        public boolean isError() {
            return errorPattern != null;
        }
    }
}
