package minisearch.algorithms;

import java.util.Locale;

public final class LevenshteinDistance {
    private LevenshteinDistance() {}
    public static int between(String a, String b) {
        if (a == null || b == null) throw new IllegalArgumentException("Words must not be null.");
        a=a.toLowerCase(Locale.ROOT); b=b.toLowerCase(Locale.ROOT); int[] previous=new int[b.length()+1], current=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){current[0]=i;for(int j=1;j<=b.length();j++)
            current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            int[] swap=previous;previous=current;current=swap;}
        return previous[b.length()];
    }
}
