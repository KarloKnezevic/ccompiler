package hr.fer.ppj.cli.test;

import hr.fer.ppj.ir.util.IrNormalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TestRunner {

    private final Path projectRoot;

    public TestRunner(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public TestReport runAll(List<TestCase> tests) {
        TestReport report = new TestReport();

        System.out.println("Running " + tests.size() + " tests...");

        for (TestCase test : tests) {
            runSingle(test, report);
        }

        return report;
    }

    private void runSingle(TestCase test, TestReport report) {
        Path testDir = projectRoot.resolve("examples/" + test.getPath());
        Path sourceFile = testDir.resolve("program.c");

        if (!Files.exists(sourceFile)) {
            report.addFailure(test, "Source file not found: " + sourceFile);
            System.out.println("MISSING: " + test.getId() + " " + test.getPath());
            return;
        }

        String sourceCode;
        try {
            sourceCode = Files.readString(sourceFile);
        } catch (IOException e) {
            report.addFailure(test, "Failed to read source: " + e.getMessage());
            return;
        }

        TestPipeline.CompilationResult result = TestPipeline.run(sourceCode);

        if (test.isValid()) {
            if (!result.success) {
                report.addFailure(test, "Expected success but failed: " + result.error);
                System.out.println("FAIL: " + test.getId());
                return;
            }

            // Compare IR
            Path goldenIrFile = testDir.resolve("program.ir");
            if (!Files.exists(goldenIrFile)) {
                report.addFailure(test, "Golden IR file missing: " + goldenIrFile);
                System.out.println("MISSING IR: " + test.getId());
                return;
            }

            try {
                String goldenIr = Files.readString(goldenIrFile);
                if (!IrNormalizer.equalsNormalized(goldenIr, result.ir)) {
                    report.addFailure(test, "IR mismatch");
                    System.out.println("FAIL (IR): " + test.getId());
                } else {
                    report.addSuccess();
                    // System.out.println("PASS: " + test.getId()); // Too verbose?
                }
            } catch (IOException e) {
                report.addFailure(test, "Failed to read golden IR: " + e.getMessage());
            }

        } else {
            // Invalid test
            if (result.success) {
                report.addFailure(test, "Expected failure but succeeded");
                System.out.println("FAIL (Unexpected Success): " + test.getId());
                return;
            }

            String expectedPattern = test.getExpected() != null ? test.getExpected().getErrorPattern() : "error";
            if (expectedPattern == null)
                expectedPattern = "error";

            // Simple regex match
            // Create regex from pattern (assuming it might be just text, so we might need
            // quoting or flexible matching)
            // For now, case insensitive partial match if not strict regex
            boolean match = Pattern.compile(expectedPattern, Pattern.CASE_INSENSITIVE).matcher(result.error).find()
                    || result.error.toLowerCase().contains(expectedPattern.toLowerCase());

            if (!match) {
                report.addFailure(test, "Error message mismatch. Got: '" + result.error + "', Expected pattern: '"
                        + expectedPattern + "'");
                System.out.println("FAIL (Msg Mismatch): " + test.getId());
            } else {
                report.addSuccess();
            }
        }
    }

    public static class TestReport {
        public int total = 0;
        public int passed = 0;
        public int failed = 0;
        public List<String> failures = new ArrayList<>();

        public void addSuccess() {
            total++;
            passed++;
        }

        public void addFailure(TestCase test, String reason) {
            total++;
            failed++;
            failures.add("[" + test.getId() + "] " + test.getPath() + ": " + reason);
        }
    }
}
