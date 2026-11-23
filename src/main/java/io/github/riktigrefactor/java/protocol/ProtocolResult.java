package io.github.riktigrefactor.java.protocol;

import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.Refusal;
import io.github.riktigrefactor.java.Warning;
import io.github.riktigrefactor.java.Workspace;
import io.github.riktigrefactor.java.analysis.TextEdit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The shared protocol's result document (see the specification's protocol.md). */
public final class ProtocolResult {
    private ProtocolResult() {}

    public record CommitMessage(String subject, String body, String trailer) {}

    public static Map<String, Object> of(Workspace workspace, Outcome outcome, CommitMessage commit) {
        Map<String, Object> result = new LinkedHashMap<>();
        switch (outcome) {
            case Outcome.Success success -> {
                result.put("status", "success");
                result.put("workspaceRoot", workspace.root().toString());
                List<Object> edits = new ArrayList<>();
                for (TextEdit edit : success.edits()) {
                    Map<String, Object> map = location(workspace, edit.file(), edit.start(), edit.end());
                    map.put("newText", edit.newText());
                    edits.add(map);
                }
                result.put("edits", edits);
                List<Object> warnings = new ArrayList<>();
                for (Warning warning : success.warnings()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", warning.code());
                    map.put("message", warning.message());
                    map.put("location", location(workspace, warning.file(), warning.start(), warning.end()));
                    warnings.add(map);
                }
                result.put("warnings", warnings);
                Map<String, Object> commitMap = new LinkedHashMap<>();
                commitMap.put("subject", commit.subject());
                commitMap.put("body", commit.body());
                commitMap.put("trailer", commit.trailer());
                result.put("commit", commitMap);
            }
            case Outcome.Refused refused -> {
                Refusal reason = refused.reason();
                result.put("status", "refused");
                result.put("workspaceRoot", workspace.root().toString());
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("code", reason.code());
                map.put("message", reason.message());
                if (reason.file() != null) {
                    map.put("location", location(workspace, reason.file(), reason.start(), reason.end()));
                }
                if (reason.suggestion() != null) {
                    map.put("suggestion", reason.suggestion());
                }
                result.put("reasons", List.of(map));
            }
        }
        return result;
    }

    private static Map<String, Object> location(Workspace workspace, String file, int start, int end) {
        String text = workspace.sources().getOrDefault(file, "");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("file", file);
        map.put("start", position(text, start));
        map.put("end", position(text, end));
        return map;
    }

    private static Map<String, Object> position(String text, int offset) {
        SourceText.Position position = SourceText.position(text, Math.min(Math.max(offset, 0), text.length()));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line", position.line());
        map.put("column", position.column());
        return map;
    }
}
