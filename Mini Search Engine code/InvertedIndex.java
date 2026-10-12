package minisearch.algorithms;

import minisearch.models.Document;
import java.util.*;

/** Maps each normalized token to the IDs of documents containing it. */
public final class InvertedIndex {
    private final Map<String, Set<Integer>> postings = new HashMap<>();
    public void rebuild(Collection<Document> documents) {
        postings.clear();
        for (Document doc : documents) for (String token : tokens(doc.title() + " " + doc.content()))
            postings.computeIfAbsent(token, ignored -> new LinkedHashSet<>()).add(doc.id());
    }
    public Set<Integer> lookup(String token) { return new LinkedHashSet<>(postings.getOrDefault(normalize(token), Set.of())); }
    public static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!token.isBlank()) result.add(token);
        return result;
    }
    public static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }
}
