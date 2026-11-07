package com.labelhub.api.module.ai.precedent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * ADR-021 §2 BM25 (k1 = 1.2, b = 0.75, idf = ln(1 + (N - df + 0.5) / (df + 0.5))). Mirrors
 * evals/labelhub_evals/precedents.py; pinned by packages/contracts/fixtures/precedent-retrieval-corpus.json.
 * Each distinct query term counts once (terms visited in sorted order, as in Python, so float sums match);
 * zero scores are dropped; ties break by key ascending.
 */
public final class Bm25Index<K extends Comparable<K>> {

    public static final double K1 = 1.2;
    public static final double B = 0.75;

    public record Document<K>(K key, String text) {
    }

    private record Posting(int doc, int tf) {
    }

    private final List<K> keys = new ArrayList<>();
    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final double[] norm;
    private final int n;

    public Bm25Index(Collection<Document<K>> docs) {
        List<Integer> lengths = new ArrayList<>();
        int index = 0;
        for (Document<K> doc : docs) {
            keys.add(doc.key());
            Map<String, Integer> counts = new HashMap<>();
            List<String> tokens = tokenize(doc.text());
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
            lengths.add(tokens.size());
            final int docIndex = index++;
            counts.forEach((token, tf) -> postings.computeIfAbsent(token, t -> new ArrayList<>()).add(new Posting(docIndex, tf)));
        }
        n = keys.size();
        double total = 0;
        for (int length : lengths) {
            total += length;
        }
        double avgdl = n == 0 ? 0 : total / n;
        norm = new double[n];
        for (int i = 0; i < n; i++) {
            norm[i] = avgdl == 0 ? K1 : K1 * (1 - B + B * (lengths.get(i) / avgdl));
        }
    }

    public List<K> search(String query, int k, Set<K> exclude) {
        double[] acc = new double[n];
        boolean[] touched = new boolean[n];
        for (String term : new TreeSet<>(tokenize(query))) {
            List<Posting> posting = postings.get(term);
            if (posting == null) {
                continue;
            }
            int df = posting.size();
            double idf = StrictMath.log(1 + (n - df + 0.5) / (df + 0.5));
            // Postings are in document order, like Python's; per-document sums then add terms in the same order.
            for (Posting p : posting) {
                acc[p.doc()] += idf * (p.tf() * (K1 + 1)) / (p.tf() + norm[p.doc()]);
                touched[p.doc()] = true;
            }
        }
        List<Integer> ranked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (touched[i] && acc[i] > 0 && !exclude.contains(keys.get(i))) {
                ranked.add(i);
            }
        }
        ranked.sort(Comparator.<Integer>comparingDouble(i -> -acc[i]).thenComparing(keys::get));
        return ranked.stream().limit(k).map(keys::get).toList();
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        List<String> cjk = new ArrayList<>();
        text.codePoints().forEach(cp -> {
            int c = cp >= 'A' && cp <= 'Z' ? cp + 32 : cp;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                flushCjk(cjk, tokens);
                word.appendCodePoint(c);
            } else if (isCjk(c)) {
                flushWord(word, tokens);
                cjk.add(new String(Character.toChars(c)));
            } else {
                flushWord(word, tokens);
                flushCjk(cjk, tokens);
            }
        });
        flushWord(word, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF)
            || (cp >= 0x3040 && cp <= 0x30FF) || (cp >= 0xAC00 && cp <= 0xD7AF);
    }

    private static void flushWord(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private static void flushCjk(List<String> cjk, List<String> tokens) {
        if (cjk.size() == 1) {
            tokens.add(cjk.get(0));
        } else {
            for (int i = 0; i + 1 < cjk.size(); i++) {
                tokens.add(cjk.get(i) + cjk.get(i + 1));
            }
        }
        cjk.clear();
    }
}
