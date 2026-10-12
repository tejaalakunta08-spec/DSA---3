package minisearch.services;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local accounts stored with salted PBKDF2 hashes and expiring in-memory sessions. */
public final class AuthService {
    private static final int ITERATIONS=210_000;
    private static final long SESSION_MILLIS=8L*60*60*1000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Path file;
    private final Map<String,Account> accounts=new ConcurrentHashMap<>();
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();

    public AuthService(Path file)throws Exception{this.file=file;load();if(!accounts.containsKey("demo@minisearch.local")){register("Demo User","demo@minisearch.local","MiniSearch123!");}}

    public synchronized Map<String,String> register(String name,String email,String password)throws Exception{
        name=name==null?"":name.trim();email=normalize(email);password=password==null?"":password;
        if(name.isBlank()||name.length()>70)throw new IllegalArgumentException("Enter your name (up to 70 characters).");
        if(!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))throw new IllegalArgumentException("Enter a valid email address.");
        if(password.length()<8||password.length()>128)throw new IllegalArgumentException("Password must be 8 to 128 characters long.");
        if(accounts.containsKey(email))throw new IllegalArgumentException("An account with that email already exists. Please sign in.");
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);Account account=new Account(name,email,salt,hash(password.toCharArray(),salt));accounts.put(email,account);save();return publicUser(account);
    }
    public synchronized Map<String,String> login(String email,String password)throws Exception{
        email=normalize(email);Account account=accounts.get(email);
        if(account==null||!MessageDigest.isEqual(account.hash,hash((password==null?"":password).toCharArray(),account.salt)))throw new IllegalArgumentException("Email or password is incorrect.");
        return publicUser(account);
    }
    public String newSession(String email){byte[] token=new byte[32];RANDOM.nextBytes(token);String id=Base64.getUrlEncoder().withoutPadding().encodeToString(token);sessions.put(id,new Session(email,System.currentTimeMillis()+SESSION_MILLIS));return id;}
    public Map<String,String> userForSession(String id){if(id==null)return null;Session session=sessions.get(id);if(session==null)return null;if(session.expiresAt<System.currentTimeMillis()){sessions.remove(id);return null;}Account account=accounts.get(session.email);return account==null?null:publicUser(account);}
    public void removeSession(String id){if(id!=null)sessions.remove(id);}
    private void load()throws IOException{if(!Files.exists(file))return;for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[]p=line.split("\\t",4);if(p.length!=4)continue;try{String email=decode(p[0]);accounts.put(email,new Account(decode(p[1]),email,Base64.getDecoder().decode(p[2]),Base64.getDecoder().decode(p[3])));}catch(IllegalArgumentException ignored){}}}
    private void save()throws IOException{Files.createDirectories(file.getParent());List<String>lines=new ArrayList<>();for(Account a:accounts.values())lines.add(encode(a.email)+"\t"+encode(a.name)+"\t"+Base64.getEncoder().encodeToString(a.salt)+"\t"+Base64.getEncoder().encodeToString(a.hash));Files.write(file,lines,StandardCharsets.UTF_8);}
    private static byte[] hash(char[] password,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();Arrays.fill(password,'\0');}}
    private static Map<String,String>publicUser(Account a){return Map.of("name",a.name,"email",a.email);}
    private static String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private static String encode(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String decode(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    private record Account(String name,String email,byte[]salt,byte[]hash){}
    private record Session(String email,long expiresAt){}
}
