package minisearch.algorithms;

import minisearch.models.Document;
import java.util.*;

public final class TFIDF {
    private TFIDF() {}
    public static Map<String,Double> vector(String text, Collection<Document> corpus) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(corpus, "corpus must not be null");
        List<String> words=InvertedIndex.tokens(text); Map<String,Integer> counts=new HashMap<>();
        words.forEach(w->counts.merge(w,1,Integer::sum)); Map<String,Double> vector=new HashMap<>();
        List<Set<String>> documentTerms = new ArrayList<>(corpus.size());
        for (Document document : corpus) {
            documentTerms.add(new HashSet<>(InvertedIndex.tokens(document.title() + " " + document.content())));
        }
        for(var entry:counts.entrySet()){int df=0;for(Set<String> terms:documentTerms)if(terms.contains(entry.getKey()))df++;
            double tf=(double)entry.getValue()/Math.max(1,words.size());
            double idf=Math.log((1.0+documentTerms.size())/(1.0+df))+1.0;
            vector.put(entry.getKey(),tf*idf);}
        return vector;
    }
}
