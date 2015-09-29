/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;
import freenet.client.ClientMetadata;
import freenet.client.FetchContext;
import static freenet.client.FetchContext.IDENTICAL_MASK;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutter;
import freenet.client.async.PersistenceDisabledException;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.BaseClientKey;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestStarter;
import freenet.support.SimpleFieldSet;
import freenet.support.api.BucketFactory;
import freenet.support.api.RandomAccessBucket;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.TimeZone;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author ktogias
 */
public class IndynetResolver {
    protected HighLevelSimpleClient client;
    protected BucketFactory bf;
    protected Node node;
    protected static String RESOLVE_FILE;
    protected static String insertKey;
    protected static String requestKey;
    protected static String path;
    
    public IndynetResolver(HighLevelSimpleClient client, BucketFactory bf, Node node, String resolvFile, String path) throws FileNotFoundException, IOException, ParseException{
        this.client = client;
        this.bf = bf;
        this.node = node;
        this.path = path;
        RESOLVE_FILE = resolvFile;
        JSONObject keys = readKeys();
        insertKey = (String) keys.get("insertKey");
        requestKey = (String) keys.get("requestKey");
    }
    
    private JSONObject readKeys() throws IOException, ParseException{
        JSONParser parser = new JSONParser();
        JSONObject keys = (JSONObject) parser.parse(new FileReader(RESOLVE_FILE));
        return keys;
    }
    
    public JSONObject resolve(String name) throws FetchException, MalformedURLException, IOException, ParseException{
        FetchResult result = client.fetch(new FreenetURI(requestKey+"/"+name));
        JSONParser parser = new JSONParser();
        JSONObject resolveObject = (JSONObject) parser.parse(new String(result.asByteArray(), "UTF-8"));
        return resolveObject;
    }
    
    public String resolve(String name, FCPPluginConnection connection, FCPPluginMessage message, short priorityClass, boolean persistent, boolean realtime) throws MalformedURLException, FetchException, PersistenceDisabledException, InterruptedException, ParseException, UnsupportedEncodingException, IOException, ResolveErrorException{
        FreenetURI furi = new FreenetURI(requestKey+"/"+name);
        FetchContext fctx = new FetchContext(client.getFetchContext(), IDENTICAL_MASK);
        FetchCallback callback = new FetchCallback(node, fctx, furi, persistent, realtime, connection, message);
        callback.subscribeToContextEvents();
        ClientGetter get = new ClientGetter(callback, furi, fctx, priorityClass);
        callback.setClientGetter(get);
        node.clientCore.clientContext.start(get);
        int status = callback.getStatus();
        if (status == FetchCallback.STATUS_SUCCESS){
            JSONParser parser = new JSONParser();
            JSONObject resolveObject = (JSONObject) parser.parse(new String(callback.getResult().asByteArray(), "UTF-8"));
            return (String) resolveObject.get("requestKey");
        }
        else {
            throw new ResolveErrorException("Name resolution failed");
        }
    }
    
    public SimpleFieldSet register(String requestKey, String name) throws MalformedURLException, FetchException, IOException, InsertException, InterruptedException, Exception{
        FreenetURI requestUri;
        try {
            requestUri = new FreenetURI(requestKey);
        }
        catch (MalformedURLException e1){
            try {
                requestUri = new FreenetURI(requestKey+"/dummy");
            }
            catch (MalformedURLException e2){
                requestUri = new FreenetURI(requestKey+"/dummy/0");
            }
        }
        try {
            BaseClientKey.getBaseKey(requestUri);
        }
        catch (MalformedURLException e1){
            BaseClientKey.getBaseKey(requestUri.setDocName("dummy"));
        }
        JSONObject robject = buildResolveObject(requestUri, name);
        FreenetURI resolveURI = buildResolveUri(name);
        InsertCallback callback = insertRegistration(robject, resolveURI);
        int status = callback.getStatus();
        SimpleFieldSet result = new SimpleFieldSet(false);
        result.put("status", status);
        if (status == InsertCallback.STATUS_SUCCESS){
            result.putSingle("resolveURI", callback.getInsertedURI().toString());
        }
        else {
            result.putSingle("error", callback.getInsertException().getMessage());
        }
        return result;
    }
    
    public String getKey(String url) throws MalformedNamedUrlException, MalformedURLException, FetchException, PersistenceDisabledException, InterruptedException, ParseException, IOException, UnsupportedEncodingException, ResolveErrorException{
        return getKey(url, null, null, RequestStarter.INTERACTIVE_PRIORITY_CLASS, false, false);
    }
    
    public String getKey(String url, short priorityClass, boolean persistent, boolean realtime) throws MalformedNamedUrlException, MalformedURLException, FetchException, PersistenceDisabledException, InterruptedException, IOException, ParseException, UnsupportedEncodingException, ResolveErrorException{
        return getKey(url, null, null, priorityClass, persistent, realtime);
    }
    
    public String getKey(String url, FCPPluginConnection connection, FCPPluginMessage message, short priorityClass, boolean persistent, boolean realtime) throws MalformedNamedUrlException, MalformedURLException, FetchException, PersistenceDisabledException, InterruptedException, ParseException, IOException, UnsupportedEncodingException, ResolveErrorException{
        if (url.startsWith("["+path+"]")){
            url = url.replaceFirst("["+path+"]", "");
        }
        if (isKey(url)){
            return url;
        }
        else {
            try {
                SimpleFieldSet namedUrlParts = decomposeNamedUrl(url);
                String key = resolve(namedUrlParts.get("name"), connection, message, priorityClass, persistent, realtime);
                return key+"/"+namedUrlParts.get("path");
            }
            catch (NullPointerException ex){
                throw new MalformedNamedUrlException("Could not decompose named url!");
            }
        }
        
    }
    
    public boolean isKey(String url){
        return url.startsWith("CHK@") || url.startsWith("SSK@") || url.startsWith("USK@") || url.startsWith("KSK@");
    }
    
    public SimpleFieldSet decomposeNamedUrl(String url){
        SimpleFieldSet decomposition = new SimpleFieldSet(false);
        String[] parts = url.split("[/]");
        decomposition.putSingle("name", parts[0]);
        String keypath = "";
        for (int i=1; i<parts.length; i++){
                keypath+="/"+parts[i];
        }
        decomposition.putSingle("path", keypath);
        return decomposition;
    }
    
    private JSONObject buildResolveObject(FreenetURI requestUri, String name){
        Date date= new java.util.Date();
        JSONObject obj = new JSONObject();
        obj.put("keyType", requestUri.getKeyType());
        obj.put("requestKey", requestUri.toString().split("/")[0]);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Timestamp now = new Timestamp(date.getTime());
        obj.put("regTime", now.toLocalDateTime().toString());
        return obj;
    }
    
    private FreenetURI buildResolveUri(String name) throws MalformedURLException, Exception{
        try {
            if (name.isEmpty()){
                throw new Exception("Registration name is empty");
            }
        }
        catch (NullPointerException e){
            throw new Exception("Registration name is missing");
        }
        return new FreenetURI(insertKey+"/"+name.toLowerCase());
    }
    
    
    private InsertCallback insertRegistration(JSONObject regObj, FreenetURI uri) throws IOException, InsertException{
        byte[] data = regObj.toJSONString().getBytes("UTF-8");
        RandomAccessBucket bucket = bf.makeBucket(data.length+1);
        OutputStream os = bucket.getOutputStream();
        os.write(data);
        os.close(); 
        os = null;
        bucket.setReadOnly();
        ClientMetadata metadata = new ClientMetadata("application/json");
        InsertBlock ib = new InsertBlock(bucket, metadata, uri);
        InsertContext ictx = client.getInsertContext(true);
        InsertCallback callback = new InsertCallback(bucket, node, false, false);
        ClientPutter pu = 
            client.insert(ib, null, false, ictx, callback, RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
        callback.setClientPutter(pu);
        return callback;
    }
    
}
