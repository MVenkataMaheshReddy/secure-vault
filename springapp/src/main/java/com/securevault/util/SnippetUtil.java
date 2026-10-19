package com.securevault.util;

import com.securevault.entity.Document;
import com.securevault.entity.DocumentVersion;

public class SnippetUtil {

    public static class MatchInfo {
        private final String snippet;
        private final String matchSource; // "content" or "metadata"

        public MatchInfo(String snippet, String matchSource) {
            this.snippet = snippet;
            this.matchSource = matchSource;
        }

        public String getSnippet() {
            return snippet;
        }

        public String getMatchSource() {
            return matchSource;
        }
    }

    /**
     * Extracts a ~150 char snippet centered around the match, and determines whether
     * the source is "content" (OCR text) or "metadata" (title, description, tags, filename).
     */
    public static MatchInfo extractSnippetAndSource(Document doc, DocumentVersion version, String query) {
        if (query == null || query.isBlank()) {
            String defaultSnippet = doc.getDescription() != null && !doc.getDescription().isBlank()
                    ? truncate(doc.getDescription(), 150)
                    : doc.getTitle();
            return new MatchInfo(defaultSnippet, "metadata");
        }

        String q = query.trim().toLowerCase();

        // 1. Check current version's OCR text first
        if (version != null && version.getOcrText() != null) {
            String ocr = version.getOcrText();
            int idx = ocr.toLowerCase().indexOf(q);
            if (idx >= 0) {
                return new MatchInfo(buildSnippet(ocr, idx, q.length(), 150), "content");
            }
        }

        // 2. Check document description
        if (doc.getDescription() != null) {
            int idx = doc.getDescription().toLowerCase().indexOf(q);
            if (idx >= 0) {
                return new MatchInfo(buildSnippet(doc.getDescription(), idx, q.length(), 150), "metadata");
            }
        }

        // 3. Check document title
        if (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(q)) {
            return new MatchInfo(doc.getTitle(), "metadata");
        }

        // 4. Check original file name
        if (doc.getOriginalFileName() != null && doc.getOriginalFileName().toLowerCase().contains(q)) {
            return new MatchInfo("File name: " + doc.getOriginalFileName(), "metadata");
        }

        // 5. Check tags
        if (doc.getTags() != null) {
            for (String tag : doc.getTags()) {
                if (tag.toLowerCase().contains(q)) {
                    return new MatchInfo("Tag match: " + tag, "metadata");
                }
            }
        }

        // Fallback
        String fallback = doc.getDescription() != null && !doc.getDescription().isBlank()
                ? truncate(doc.getDescription(), 150)
                : doc.getTitle();
        return new MatchInfo(fallback, "metadata");
    }

    public static String buildSnippet(String text, int matchIndex, int matchLength, int targetLength) {
        if (text == null) return "";
        // Normalize whitespace and newlines for clean snippet display
        String cleaned = text.replaceAll("\\s+", " ").trim();
        int cleanIndex = cleaned.toLowerCase().indexOf(
                text.substring(matchIndex, Math.min(matchIndex + matchLength, text.length())).toLowerCase()
        );
        if (cleanIndex < 0) {
            cleanIndex = 0;
        }

        int half = (targetLength - matchLength) / 2;
        int start = Math.max(0, cleanIndex - half);
        int end = Math.min(cleaned.length(), cleanIndex + matchLength + half);

        // Adjust boundaries if one side has fewer characters
        if (start == 0) {
            end = Math.min(cleaned.length(), targetLength);
        } else if (end == cleaned.length()) {
            start = Math.max(0, cleaned.length() - targetLength);
        }

        StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("...");
        }
        sb.append(cleaned, start, end);
        if (end < cleaned.length()) {
            sb.append("...");
        }

        return sb.toString();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) return cleaned;
        return cleaned.substring(0, maxLength) + "...";
    }
}
