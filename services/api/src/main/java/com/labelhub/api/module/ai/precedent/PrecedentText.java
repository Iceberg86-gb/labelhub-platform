package com.labelhub.api.module.ai.precedent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ADR-021 §2: what BM25 indexes and what the prompt shows. Mirrors evals/labelhub_evals/precedents.py
 * ({@code text_of}, {@code labeled_text}, {@code excerpt}); pinned by precedent-retrieval-corpus.json.
 */
public final class PrecedentText {

    public static final int EXCERPT_CODE_POINTS = 1200;

    private PrecedentText() {
    }

    /** String leaves in canonical key order (depth first, list order kept), joined with newlines. */
    public static String textOf(Object value) {
        List<String[]> leaves = new ArrayList<>();
        collect(value, "", leaves);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < leaves.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(leaves.get(i)[1]);
        }
        return out.toString();
    }

    /** The same leaves as {@code path: text} lines. */
    public static String labeledText(Object value) {
        List<String[]> leaves = new ArrayList<>();
        collect(value, "", leaves);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < leaves.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(leaves.get(i)[0]).append(": ").append(leaves.get(i)[1]);
        }
        return out.toString();
    }

    public static String excerpt(String text) {
        if (text.codePointCount(0, text.length()) <= EXCERPT_CODE_POINTS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, EXCERPT_CODE_POINTS)) + "…";
    }

    private static void collect(Object node, String path, List<String[]> out) {
        if (node instanceof String text) {
            out.add(new String[] {path, text});
        } else if (node instanceof Map<?, ?> map) {
            // Canonical order = Java String natural order (UTF-16), same as Python's sorted() for BMP keys.
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            sorted.forEach((key, child) -> collect(child, path.isEmpty() ? key : path + "." + key, out));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collect(list.get(i), path.isEmpty() ? String.valueOf(i) : path + "." + i, out);
            }
        }
    }
}
