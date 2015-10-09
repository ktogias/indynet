/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.PersistenceDisabledException;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.BaseClientKey;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestStarter;
import freenet.support.SimpleFieldSet;
import freenet.support.api.BucketFactory;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
    protected final static short DEFAULT_PRIORITY = RequestStarter.INTERACTIVE_PRIORITY_CLASS;
    
    protected String path;
    protected FCPPluginConnection pluginConnection;
    protected FCPPluginMessage pluginMessage;
    
    public IndynetResolver(HighLevelSimpleClient client, BucketFactory bf, Node node, String resolvFile, String path, FCPPluginConnection connection, FCPPluginMessage message) throws FileNotFoundException, IOException, ParseException{
        this.client = client;
        this.bf = bf;
        this.node = node;
        this.path = path;
        this.pluginConnection = connection;
        this.pluginMessage = message;
        
        RESOLVE_FILE = resolvFile;
        JSONObject keys = readKeys();
        insertKey = (String) keys.get("insertKey");
        requestKey = (String) keys.get("requestKey");
    }
    
    public IndynetResolver(HighLevelSimpleClient client, BucketFactory bf, Node node, String resolvFile, String path) throws FileNotFoundException, IOException, ParseException{
        this(client, bf, node, resolvFile, path, null, null);
    }
    private JSONObject readKeys() throws IOException, ParseException{
        JSONParser parser = new JSONParser();
        JSONObject keys = (JSONObject) parser.parse(new FileReader(RESOLVE_FILE));
        return keys;
    }
    
    public String resolve(String name) throws FetchException, MalformedURLException, IOException, ParseException, PersistenceDisabledException, InterruptedException, UnsupportedEncodingException, ResolveException{
        return resolve(name, DEFAULT_PRIORITY, false, false);
    }
    
    public String resolve(String name, short priorityClass, boolean persistent, boolean realtime) throws MalformedURLException, FetchException, PersistenceDisabledException, InterruptedException, IOException, ParseException{
        FreenetURI furi = new FreenetURI(requestKey+"/"+name);
        JSONObject resolveObject = Util.fetchJSONObject(furi, client, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
        return (String) resolveObject.get("requestKey");
    }
    
    public FreenetURI register(String requestKey, String name) throws MalformedURLException, BuildResolveURIException, IOException, UnsupportedEncodingException, InsertException, InterruptedException {
        return register(requestKey, name, DEFAULT_PRIORITY, false, false);
    }
    
    public FreenetURI register(String requestKey, String name, short priorityClass, boolean persistent, boolean realtime) throws MalformedURLException, BuildResolveURIException, IOException, UnsupportedEncodingException, InsertException, InterruptedException {
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
        FreenetURI insertedURI = Util.insertJSONObject(robject, resolveURI, client, bf, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
        return insertedURI;
    }
    
    public URI normalizeUri(URI uri) throws URISyntaxException{
        String uriStr = uri.getPath().replaceFirst(path, "");
        String query = uri.getQuery();
        if (query != null){
            uriStr += "?"+query;
        }
        return new URI(uriStr);
    }
    
    public boolean isFreenetKey(String key){
        return key.startsWith("CHK@") || key.startsWith("SSK@") || key.startsWith("USK@") || key.startsWith("KSK@");
    }
    
    public SimpleFieldSet decomposeUri(URI uri){
        String path = uri.getPath();
        SimpleFieldSet decomposition = new SimpleFieldSet(false);
        String[] parts = path.split("/");
        decomposition.putSingle("key", parts[0]);
        String keypath = "";
        for (int i=1; i<parts.length; i++){
                keypath+="/"+parts[i];
        }
        decomposition.putSingle("path", keypath);
        decomposition.putSingle("query", uri.getQuery());
        return decomposition;
    }
    
    private JSONObject buildResolveObject(FreenetURI requestUri, String name){
        Date date= new java.util.Date();
        JSONObject obj = new JSONObject();
        obj.put("keyType", requestUri.getKeyType());
        obj.put("requestKey", requestUri.toString().split("/")[0]);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Timestamp now = new Timestamp(date.getTime());
        obj.put("regTime", now.toString());
        return obj;
    }
    
    private FreenetURI buildResolveUri(String name) throws BuildResolveURIException, MalformedURLException {
        try {
            if (name.isEmpty()){
                throw new BuildResolveURIException("Registration name is empty");
            }
        }
        catch (NullPointerException e){
            throw new BuildResolveURIException("Registration name is missing");
        }
        return new FreenetURI(insertKey+"/"+name.toLowerCase());
    }
    
}
