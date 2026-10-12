package minisearch.algorithms;

import java.util.Map;

public final class CosineSimilarity {
    private CosineSimilarity() {}
    public static double between(Map<String,Double> a, Map<String,Double> b) {
        double dot=0, aa=0, bb=0;for(double x:a.values())aa+=x*x;for(double y:b.values())bb+=y*y;
        for(var e:a.entrySet())dot+=e.getValue()*b.getOrDefault(e.getKey(),0.0);
        return aa==0||bb==0?0:dot/(Math.sqrt(aa)*Math.sqrt(bb));
    }
}
