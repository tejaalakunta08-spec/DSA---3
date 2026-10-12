package minisearch.services;

import minisearch.algorithms.*;
import minisearch.models.Document;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns documents, persistence, indexes, query execution and demo metrics. */
public final class SearchEngine {
    private final Path store; private final List<Document> documents=new ArrayList<>();
    private final AtomicInteger nextId=new AtomicInteger(1); private final Map<String,Integer> searchCounts=new HashMap<>();
    private long totalSearches,totalSearchNanos; private InvertedIndex index=new InvertedIndex();
    public SearchEngine(Path store) throws IOException { this.store=store;load(); if(documents.isEmpty())seed();rebuild(); }
    public synchronized List<Document> documents(){return List.copyOf(documents);}
    public synchronized Document add(String title,String content) throws IOException {
        title=title==null?"":title.trim();content=content==null?"":content.trim();
        if(title.isBlank()||content.isBlank())throw new IllegalArgumentException("Title and content are required.");
        for(Document d:documents)if(d.title().equalsIgnoreCase(title))throw new IllegalArgumentException("A document with that title already exists.");
        Document d=new Document(nextId.getAndIncrement(),title,content);documents.add(d);rebuild();save();return d;
    }
    public synchronized boolean delete(int id)throws IOException{boolean removed=documents.removeIf(d->d.id()==id);if(removed){rebuild();save();}return removed;}
    public synchronized Map<String,Object> search(String query,String type) {
        long start=System.nanoTime();String q=query==null?"":query.trim();if(q.isBlank())throw new IllegalArgumentException("Enter a search query.");
        List<Map<String,Object>> results=new ArrayList<>();String normalized=InvertedIndex.normalize(q);
        switch(type){
            case "exact" -> {Set<Integer> ids=new LinkedHashSet<>();for(String token:InvertedIndex.tokens(q))ids.addAll(index.lookup(token));
                for(Document d:documents)if(ids.contains(d.id())){long matched=InvertedIndex.tokens(q).stream().filter(t->index.lookup(t).contains(d.id())).count();double score=(double)matched/Math.max(1,InvertedIndex.tokens(q).size());results.add(result(d,score,"Inverted Index",InvertedIndex.tokens(q)));}}
            case "phrase" -> {String needle=q.toLowerCase(Locale.ROOT);for(Document d:documents)if(new SuffixArray(d.title()+" "+d.content()).contains(needle))results.add(result(d,1,"Suffix Array",List.of(q)));}
            case "fuzzy" -> {Set<String> vocab=new TreeSet<>();for(Document d:documents)vocab.addAll(InvertedIndex.tokens(d.title()+" "+d.content()));
                List<String> queryWords=InvertedIndex.tokens(q);Set<String> matched=new LinkedHashSet<>();List<String> suggestions=new ArrayList<>();
                for(String qw:queryWords){String best=null;int bestDistance=Integer.MAX_VALUE;for(String word:vocab){int distance=LevenshteinDistance.between(qw,word);int threshold=Math.max(1,qw.length()/3);if(distance<=threshold&&distance<bestDistance){best=word;bestDistance=distance;}}
                    if(best!=null){matched.add(best);if(!best.equals(qw))suggestions.add(best);}}
                for(Document d:documents){List<String> words=InvertedIndex.tokens(d.title()+" "+d.content());long hits=matched.stream().filter(words::contains).count();if(hits>0)results.add(result(d,(double)hits/Math.max(1,matched.size()),"Levenshtein Distance",new ArrayList<>(matched)));}
                if(!suggestions.isEmpty())results.forEach(r->r.put("suggestion",String.join(", ",suggestions)));
            }
            case "similarity" -> {Map<String,Double> qv=TFIDF.vector(q,documents);for(Document d:documents){double score=CosineSimilarity.between(qv,TFIDF.vector(d.title()+" "+d.content(),documents));if(score>0)results.add(result(d,score,"TF-IDF + Cosine Similarity",InvertedIndex.tokens(q)));}}
            default -> throw new IllegalArgumentException("Unknown search type. Use exact, phrase, fuzzy or similarity.");
        }
        results.sort((a,b)->Double.compare((double)b.get("score"),(double)a.get("score")));
        long elapsed=(System.nanoTime()-start)/1_000_000;totalSearches++;totalSearchNanos+=System.nanoTime()-start;searchCounts.merge(type,1,Integer::sum);
        Map<String,Object> response=new LinkedHashMap<>();response.put("query",q);response.put("type",type);response.put("executionTime",elapsed);response.put("count",results.size());response.put("results",results);return response;
    }
    public synchronized Map<String,Object> stats(){Map<String,Object>s=new LinkedHashMap<>();s.put("totalDocuments",documents.size());s.put("totalIndexedWords",documents.stream().mapToInt(Document::wordCount).sum());s.put("totalSearches",totalSearches);s.put("averageSearchTime",totalSearches==0?0:(totalSearchNanos/1_000_000.0/totalSearches));s.put("searchesByType",new LinkedHashMap<>(searchCounts));return s;}
    private Map<String,Object> result(Document d,double score,String method,List<String> matches){Map<String,Object>r=new LinkedHashMap<>();r.put("id",d.id());r.put("title",d.title());r.put("content",d.content());r.put("preview",d.content().length()>260?d.content().substring(0,260)+"…":d.content());r.put("score",Math.round(score*10000.0)/10000.0);r.put("method",method);r.put("matches",matches);return r;}
    private void rebuild(){index=new InvertedIndex();index.rebuild(documents);}
    private void seed()throws IOException{String[][] sample={
        {"Data Structures","Data structures organize and store data efficiently. Arrays, linked lists, stacks, queues, trees, and graphs are fundamental structures."},
        {"Algorithms","An algorithm is a finite sequence of steps for solving a problem. Sorting and searching algorithms are analyzed by time and space complexity."},
        {"Java Programming","Java is an object-oriented programming language. The Java virtual machine runs bytecode across platforms and supports collections and concurrency."},
        {"Artificial Intelligence","Artificial intelligence builds systems that perform tasks requiring human reasoning, planning, perception, and language understanding."},
        {"Machine Learning","Machine learning trains models from data. Supervised learning, neural networks, and evaluation help computers find patterns and make predictions."},
        {"Operating Systems","An operating system manages processes, memory, files, devices, scheduling, and security while providing services to application programs."},
        {"Database Management Systems","A DBMS stores and retrieves structured data. Relational databases use tables, SQL, indexes, transactions, and normalization."},
        {"Computer Networks","Computer networks connect devices using protocols. TCP IP, routing, Ethernet, and the web enable reliable communication."},
        {"Information Retrieval","Information retrieval finds relevant documents for a query. Inverted indexes, TF-IDF, and cosine similarity rank search results."},
        {"Programming Fundamentals","Programming uses variables, control flow, functions, and data types to express instructions and build reliable software."}};
        for(String[]s:sample)documents.add(new Document(nextId.getAndIncrement(),s[0],s[1]));save();}
    private void load()throws IOException{if(!Files.exists(store))return;for(String line:Files.readAllLines(store,StandardCharsets.UTF_8)){if(line.isBlank())continue;String[]p=line.split("\\t",4);if(p.length==4){Document d=new Document(Integer.parseInt(p[0]),decode(p[1]),decode(p[2]),decode(p[3]));documents.add(d);nextId.set(Math.max(nextId.get(),d.id()+1));}}}
    private void save()throws IOException{Files.createDirectories(store.getParent());List<String> lines=new ArrayList<>();for(Document d:documents)lines.add(d.id()+"\t"+encode(d.title())+"\t"+encode(d.content())+"\t"+encode(d.addedAt()));Files.write(store,lines,StandardCharsets.UTF_8);}
    private static String encode(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String decode(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
}
