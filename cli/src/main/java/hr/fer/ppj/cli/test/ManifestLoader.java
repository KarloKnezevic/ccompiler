package hr.fer.ppj.cli.test;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ManifestLoader {

    public static List<TestCase> load(Path manifestPath) throws IOException {
        String content = Files.readString(manifestPath);
        JSONArray json = new JSONArray(content);

        List<TestCase> tests = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject obj = json.getJSONObject(i);

            String id = obj.getString("id");
            String path = obj.getString("path");
            boolean valid = obj.getBoolean("valid");
            String category = obj.getString("category");

            TestCase.ExpectedOutput expected = null;
            if (obj.has("expected")) {
                JSONObject expObj = obj.getJSONObject("expected");
                if (expObj.has("return")) {
                    expected = new TestCase.ExpectedOutput(String.valueOf(expObj.get("return")), null);
                } else if (expObj.has("error")) {
                    String pattern = "error"; // default
                    Object errObj = expObj.get("error");
                    if (errObj instanceof JSONObject) {
                        pattern = ((JSONObject) errObj).optString("pattern", "error");
                    } else if (errObj instanceof String) {
                        pattern = (String) errObj;
                    }
                    expected = new TestCase.ExpectedOutput(null, pattern);
                }
            }

            tests.add(new TestCase(id, path, valid, category, expected));
        }

        return tests;
    }
}
