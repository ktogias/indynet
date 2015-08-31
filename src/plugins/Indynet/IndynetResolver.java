/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;
import freenet.client.ClientMetadata;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.ClientPutter;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    
    public IndynetResolver(HighLevelSimpleClient client, BucketFactory bf, Node node, String resolvFile) throws FileNotFoundException, IOException, ParseException{
        this.client = client;
        this.bf = bf;
        this.node = node;
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
        BaseClientKey.getBaseKey(requestUri);
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
