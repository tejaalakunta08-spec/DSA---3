package minisearch.algorithms;

import java.util.*;

/** Builds a suffix array with prefix doubling and binary-searches a normalized text. */
public final class SuffixArray {
    private final String text;
    private final int[] suffixes;
    public SuffixArray(String source) {
        if (source == null) throw new IllegalArgumentException("Source text must not be null.");
        text = source.toLowerCase(Locale.ROOT);
        int n = text.length(); suffixes = new int[n]; int[] rank = new int[n], next = new int[n];
        for (int i=0;i<n;i++) { suffixes[i]=i; rank[i]=text.charAt(i); }
        for (int width=1;width<n;width*=2) {
            final int w=width; final int[] currentRank=rank;
            Integer[] boxed = new Integer[n]; for(int i=0;i<n;i++) boxed[i]=suffixes[i];
            Arrays.sort(boxed, (a,b) -> { int c=Integer.compare(currentRank[a],currentRank[b]); if(c!=0)return c;
                return Integer.compare(a+w<n?currentRank[a+w]:-1,b+w<n?currentRank[b+w]:-1); });
            for(int i=0;i<n;i++)suffixes[i]=boxed[i];
            if(n>0) next[suffixes[0]]=0;
            for(int i=1;i<n;i++){int a=suffixes[i-1],b=suffixes[i];
                next[b]=next[a]+(rank[a]!=rank[b] || (a+w<n?rank[a+w]:-1)!=(b+w<n?rank[b+w]:-1)?1:0);}
            int[] swap=rank;rank=next;next=swap;if(n>0&&rank[suffixes[n-1]]==n-1)break;
            if(width>n/2)break;
        }
    }
    public boolean contains(String pattern) {
        if (pattern == null) throw new IllegalArgumentException("Pattern must not be null.");
        String q=pattern.toLowerCase(Locale.ROOT); if(q.isEmpty())return false;
        int lo=0,hi=suffixes.length-1;
        while(lo<=hi){int mid=(lo+hi)>>>1, pos=suffixes[mid]; int cmp=text.regionMatches(pos,q,0,q.length())?0:
            text.substring(pos,Math.min(text.length(),pos+q.length())).compareTo(q);
            if(cmp==0)return true;if(cmp<0)lo=mid+1;else hi=mid-1;}
        return false;
    }
}
