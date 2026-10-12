package minisearch;

import com.sun.net.httpserver.*;
import minisearch.models.Document;
import minisearch.algorithms.LevenshteinDistance;
import minisearch.services.SearchEngine;
import minisearch.services.AuthService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small dependency-free HTTP/JSON server, suitable for running with a stock JDK. */
public final class MiniSearchServer {
    private static final String JSON="application/json; charset=utf-8";
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args.length>0?args[0]:".").toAbsolutePath().normalize();int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        SearchEngine engine=new SearchEngine(root.resolve("backend/data/documents.txt"));AuthService auth=new AuthService(root.resolve("backend/data/users.txt"));HttpServer server=HttpServer.create(new InetSocketAddress(port),0);
        server.createContext("/api",exchange->{try{handleApi(exchange,engine,auth);}catch(IllegalArgumentException e){send(exchange,400,Map.of("error",e.getMessage()));}catch(Exception e){e.printStackTrace();send(exchange,500,Map.of("error","The request could not be completed."));}});
        server.createContext("/",exchange->serveStatic(exchange,root.resolve("frontend")));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());server.start();System.out.println("MiniSearch running at http://localhost:"+port);
    }
    private static void handleApi(HttpExchange x,SearchEngine engine,AuthService auth)throws Exception{
        String path=x.getRequestURI().getPath(),method=x.getRequestMethod();
        String token=sessionCookie(x);
        if(path.equals("/api/auth/me")&&method.equals("GET")){Map<String,String>user=auth.userForSession(token);if(user==null)send(x,200,Map.of("authenticated",false));else{Map<String,Object>response=new LinkedHashMap<>();response.put("authenticated",true);response.put("user",user);send(x,200,response);}return;}
        if(path.equals("/api/auth/signup")&&method.equals("POST")){Map<String,Object>body=Json.object(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));Map<String,String>user=auth.register(string(body,"name"),string(body,"email"),string(body,"password"));setSession(x,auth.newSession(user.get("email")));send(x,201,Map.of("user",user));return;}
        if(path.equals("/api/auth/login")&&method.equals("POST")){Map<String,Object>body=Json.object(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));Map<String,String>user=auth.login(string(body,"email"),string(body,"password"));setSession(x,auth.newSession(user.get("email")));send(x,200,Map.of("user",user));return;}
        if(path.equals("/api/auth/logout")&&method.equals("POST")){auth.removeSession(token);x.getResponseHeaders().add("Set-Cookie","MINISEARCH_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");send(x,200,Map.of("loggedOut",true));return;}
        if(auth.userForSession(token)==null){send(x,401,Map.of("error","Please sign in to continue."));return;}
        if(path.equals("/api/documents")&&method.equals("GET")){List<Map<String,Object>> rows=new ArrayList<>();for(Document d:engine.documents()){Map<String,Object>m=new LinkedHashMap<>();m.put("id",d.id());m.put("title",d.title());m.put("content",d.content());m.put("wordCount",d.wordCount());m.put("addedAt",d.addedAt());rows.add(m);}send(x,200,rows);return;}
        if(path.equals("/api/documents")&&method.equals("POST")){Map<String,Object>body=Json.object(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));Document d=engine.add(string(body,"title"),string(body,"content"));send(x,201,Map.of("id",d.id(),"title",d.title(),"content",d.content(),"wordCount",d.wordCount(),"addedAt",d.addedAt()));return;}
        if(path.startsWith("/api/documents/")&&method.equals("DELETE")){int id=Integer.parseInt(path.substring("/api/documents/".length()));if(engine.delete(id)){send(x,200,Map.of("deleted",true,"id",id));}else send(x,404,Map.of("error","Document not found."));return;}
        if(path.equals("/api/stats")&&method.equals("GET")){send(x,200,engine.stats());return;}
        if(path.equals("/api/demo/distance")&&method.equals("GET")){Map<String,String>q=queryParams(x.getRequestURI().getRawQuery());if(!q.containsKey("a")||!q.containsKey("b"))throw new IllegalArgumentException("Provide both words to compare.");send(x,200,Map.of("distance",LevenshteinDistance.between(q.get("a"),q.get("b"))));return;}
        if(path.startsWith("/api/search")&&method.equals("GET")){Map<String,String>q=queryParams(x.getRequestURI().getRawQuery());String type=path.equals("/api/search")?q.getOrDefault("type","similarity"):path.substring("/api/search/".length());send(x,200,engine.search(q.get("query"),type));return;}
        send(x,404,Map.of("error","API route not found."));
    }
    private static String sessionCookie(HttpExchange x){String header=x.getRequestHeaders().getFirst("Cookie");if(header==null)return null;for(String item:header.split(";")){String[]pair=item.trim().split("=",2);if(pair.length==2&&pair[0].equals("MINISEARCH_SESSION"))return pair[1];}return null;}
    private static void setSession(HttpExchange x,String token){x.getResponseHeaders().add("Set-Cookie","MINISEARCH_SESSION="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");}
    private static String string(Map<String,Object>m,String k){Object v=m.get(k);return v==null?"":String.valueOf(v);}
    private static Map<String,String>queryParams(String raw)throws Exception{Map<String,String>m=new HashMap<>();if(raw!=null)for(String part:raw.split("&")){String[]kv=part.split("=",2);m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"");}return m;}
    private static void send(HttpExchange x,int status,Object value)throws IOException{byte[]bytes=Json.stringify(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",JSON);x.getResponseHeaders().set("Access-Control-Allow-Origin","*");x.sendResponseHeaders(status,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}}
    private static void serveStatic(HttpExchange x,Path frontend)throws IOException{String path=x.getRequestURI().getPath();if(path.equals("/"))path="/index.html";Path file=frontend.resolve(path.substring(1)).normalize();if(!file.startsWith(frontend)||!Files.isRegularFile(file)){x.sendResponseHeaders(404,-1);return;}String name=file.getFileName().toString();String type=name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":name.endsWith(".html")?"text/html":"application/octet-stream";byte[]body=Files.readAllBytes(file);x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");x.sendResponseHeaders(200,body.length);try(OutputStream out=x.getResponseBody()){out.write(body);}}
    /** Minimal JSON encoder/parser for request objects; no external dependencies. */
    static final class Json{
        static String stringify(Object v){if(v==null)return"null";if(v instanceof String s)return quote(s);if(v instanceof Number||v instanceof Boolean)return v.toString();if(v instanceof Map<?,?>m){List<String>parts=new ArrayList<>();m.forEach((k,val)->parts.add(quote(String.valueOf(k))+":"+stringify(val)));return"{"+String.join(",",parts)+"}";}if(v instanceof Iterable<?>a){List<String>p=new ArrayList<>();a.forEach(e->p.add(stringify(e)));return"["+String.join(",",p)+"]";}return quote(v.toString());}
        static String quote(String s){StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray())switch(c){case'"'->b.append("\\\"");case'\\'->b.append("\\\\");case'\n'->b.append("\\n");case'\r'->b.append("\\r");case'\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
        static Map<String,Object>object(String s){Parser p=new Parser(s);Object value=p.value();if(!(value instanceof Map<?,?> m))throw new IllegalArgumentException("Request body must be a JSON object.");Map<String,Object>out=new HashMap<>();m.forEach((k,v)->out.put(String.valueOf(k),v));return out;}
        static final class Parser{final String s;int i;Parser(String s){this.s=s;}void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}Object value(){ws();if(i>=s.length())throw bad();char c=s.charAt(i);if(c=='{')return obj();if(c=='"')return str();if(s.startsWith("null",i)){i+=4;return null;}throw bad();}Map<String,Object>obj(){i++;Map<String,Object>m=new HashMap<>();ws();if(take('}'))return m;do{ws();if(i>=s.length()||s.charAt(i)!='"')throw bad();String k=str();ws();if(!take(':'))throw bad();m.put(k,value());ws();if(take('}'))return m;}while(take(','));throw bad();}String str(){i++;StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())throw bad();char e=s.charAt(i++);switch(e){case'"','\\','/'->b.append(e);case'n'->b.append('\n');case'r'->b.append('\r');case't'->b.append('\t');case'b'->b.append('\b');case'f'->b.append('\f');case'u'->{if(i+4>s.length())throw bad();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->throw bad();}}else b.append(c);}throw bad();}boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}IllegalArgumentException bad(){return new IllegalArgumentException("Invalid JSON request body.");}}
    }
}
