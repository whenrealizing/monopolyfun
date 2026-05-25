package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.service.view.WorkThreadPacketView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkThreadMarkdown {
    private static final Pattern GITHUB_PR = Pattern.compile("https://github\\.com/[^\\s)]+/pull/\\d+");

    public WorkThreadPacketView packet(ProjectEntity project, WorkThreadEntity thread) {
        String markdown = markdown(project, thread);
        return new WorkThreadPacketView(
                project.projectNo(),
                project.id(),
                thread.id(),
                thread.threadNo(),
                "openclaw",
                thread.taskValue(),
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                thread.title(),
                thread.goal(),
                thread.deliverables(),
                thread.acceptanceCriteria(),
                thread.repoRef(),
                thread.issueUrl(),
                markdown);
    }

    public ParsedResult parseResult(String markdown) {
        String value = markdown == null ? "" : markdown;
        Map<String, String> frontmatter = frontmatter(value);
        String summary = section(value, "Summary");
        String evidence = section(value, "Evidence");
        String prUrl = firstGithubPr(evidence);
        String testSummary = firstPrefixedLine(evidence, "Test:");
        List<String> changedFiles = bulletSection(value, "Changed Files");
        return new ParsedResult(frontmatter, summary, prUrl, testSummary, changedFiles);
    }

    private String markdown(ProjectEntity project, WorkThreadEntity thread) {
        return """
                ---
                contractVersion: 1.0
                packetType: work_thread
                projectNo: %s
                projectId: %s
                workThreadId: %s
                threadNo: %s
                runtime: openclaw
                taskValue: %d
                bounty: %d %s
                ---

                # Work Thread

                ## Goal
                %s

                ## Deliverables
                %s

                ## Acceptance Criteria
                %s

                ## Context
                - Repo: %s
                - Issue: %s
                """.formatted(
                project.projectNo(),
                project.id(),
                thread.id(),
                thread.threadNo(),
                thread.taskValue(),
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                thread.goal(),
                bullets(thread.deliverables()),
                bullets(thread.acceptanceCriteria()),
                blankToDash(thread.repoRef()),
                blankToDash(thread.issueUrl()));
    }

    private static String bullets(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- 未填写";
        }
        return values.stream().map(value -> "- " + value).reduce((left, right) -> left + "\n" + right).orElse("- 未填写");
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static Map<String, String> frontmatter(String markdown) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String[] lines = markdown.split("\\R");
        if (lines.length == 0 || !"---".equals(lines[0].trim())) {
            return values;
        }
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if ("---".equals(line)) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                values.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return values;
    }

    private static String section(String markdown, String title) {
        Pattern pattern = Pattern.compile("(?ms)^##\\s+" + Pattern.quote(title) + "\\s*$\\R(.*?)(?=^##\\s+|\\z)");
        Matcher matcher = pattern.matcher(markdown);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static List<String> bulletSection(String markdown, String title) {
        String section = section(markdown, title);
        if (section.isBlank()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (String line : section.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                values.add(trimmed.substring(2).trim());
            }
        }
        return List.copyOf(values);
    }

    private static String firstGithubPr(String value) {
        Matcher matcher = GITHUB_PR.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group() : "";
    }

    private static String firstPrefixedLine(String value, String prefix) {
        for (String line : (value == null ? "" : value).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2).trim();
            }
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    public record ParsedResult(
            Map<String, String> frontmatter,
            String summary,
            String prUrl,
            String testSummary,
            List<String> changedFiles
    ) {
    }
}
