package hr.fer.ppj.cli.commands;

import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.test.ManifestLoader;
import hr.fer.ppj.cli.test.TestCase;
import hr.fer.ppj.cli.test.TestRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestCommand implements Command {

    @Override
    public int execute(CliOptions options) {
        Path root = Paths.get("").toAbsolutePath();
        Path manifestPath = root.resolve("examples/manifest.json");

        if (!Files.exists(manifestPath)) {
            System.err.println("Error: Manifest file not found at " + manifestPath);
            return 1;
        }

        try {
            System.out.println("Loading manifest from " + manifestPath);
            List<TestCase> tests = ManifestLoader.load(manifestPath);

            if (options.filter().isPresent()) {
                String filter = options.filter().get().toLowerCase();

                if (filter.equals("valid")) {
                    tests = tests.stream().filter(TestCase::isValid).toList();
                } else if (filter.equals("invalid")) {
                    tests = tests.stream().filter(t -> !t.isValid()).toList();
                } else {
                    tests = tests.stream()
                            .filter(t -> t.getId().toLowerCase().contains(filter) ||
                                    t.getPath().toLowerCase().contains(filter) ||
                                    t.getCategory().toLowerCase().contains(filter))
                            .toList();
                }
                System.out.println("Filtered " + tests.size() + " tests (filter: '" + filter + "')");
            }

            TestRunner runner = new TestRunner(root);
            TestRunner.TestReport report = runner.runAll(tests);

            System.out.println("--------------------------------------------------");
            System.out.println("Summary:");
            System.out.println("  Total:  " + report.total);
            System.out.println("  Passed: " + report.passed);
            System.out.println("  Failed: " + report.failed);

            if (report.failed > 0) {
                System.out.println("\nFailures:");
                for (String failure : report.failures) {
                    System.out.println("  " + failure);
                }
                return 1;
            }

            return 0;

        } catch (IOException e) {
            System.err.println("Error loading manifest: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error running tests: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
