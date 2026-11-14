package com.pawpose.domain.match.pipeline;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SimilarityScorer {
    public double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) throw new IllegalArgumentException("INVALID_VECTOR");
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            if (!Double.isFinite(a[i]) || !Double.isFinite(b[i])) throw new IllegalArgumentException("INVALID_VECTOR");
            dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i];
        }
        if (normA < 1e-12 || normB < 1e-12) throw new IllegalArgumentException("INVALID_VECTOR");
        return Math.max(-1, Math.min(1, dot / Math.sqrt(normA * normB)));
    }
    public Score score(double[][] a, double[][] b, String breedA, String breedB, String colorA, String colorB, String regionA, String regionB) {
        if (a.length == 0 || b.length == 0) throw new IllegalArgumentException("INVALID_VECTOR");
        double raw = -1;
        for (double[] left : a) for (double[] right : b) raw = Math.max(raw, cosine(left, right));
        double adjusted = raw;
        if (sameKnown(breedA, breedB)) adjusted += .05;
        Set<String> colors = words(colorA); colors.retainAll(words(colorB));
        if (!colors.isEmpty()) adjusted += .03;
        if (sameKnown(regionA, regionB)) adjusted += .03;
        return new Score(raw, Math.max(0, Math.min(1, adjusted)));
    }
    private boolean sameKnown(String a, String b) { return a != null && b != null && !a.isBlank() && !a.equals("모름") && a.trim().equalsIgnoreCase(b.trim()); }
    private Set<String> words(String s) {
        if (s == null || s.isBlank() || s.equals("모름")) return new HashSet<>();
        return new HashSet<>(Arrays.asList(s.toLowerCase(Locale.ROOT).trim().split("[\\s,+/]+")));
    }
    public record Score(double raw, double adjusted) {}
}
