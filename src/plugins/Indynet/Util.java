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
import freenet.client.events.SimpleEventProducer;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.api.RandomAccessBucket;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author ktogias
 */
public class Util {
    
    public static InsertCallback insertDataAsync(RandomAccessBucket bucket, FreenetURI uri, String contentType, HighLevelSimpleClient client, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws IOException, InsertException{
        ClientMetadata metadata = new ClientMetadata(contentType);
        InsertBlock ib = new InsertBlock(bucket, metadata, uri);
        InsertContext ictx = new InsertContext(client.getInsertContext(true), new SimpleEventProducer());
        InsertCallback callback = new InsertCallback(node, ictx, uri, bucket, persistent, realtime, pluginConnection, pluginMessage);
        callback.subscribeToContextEvents();
        ClientPutter pu
                = client.insert(ib, null, false, ictx, callback, priorityClass);
        callback.setClientPutter(pu);
        return callback;
    }
    
    public static InsertCallback insertDataAsync(byte[] data, FreenetURI uri, String contentType, HighLevelSimpleClient client, BucketFactory bf, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws IOException, InsertException{
        RandomAccessBucket bucket = Util.ByteArrayToRandomAccessBucket(data, bf);
        bucket.setReadOnly();
        return insertDataAsync(bucket, uri, contentType, client, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
    }
    
    public static FreenetURI insertData(byte[] data, FreenetURI uri, String contentType, HighLevelSimpleClient client, BucketFactory bf, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws IOException, InsertException, InterruptedException{
        InsertCallback callback = Util.insertDataAsync(data, uri, contentType, client, bf, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
        int status = callback.getStatus();
        if (status == InsertCallback.STATUS_SUCCESS){
            return callback.getInsertedURI();
        }
        else {
            throw callback.getInsertException();
        }
    }
    
    public static FreenetURI insertData(RandomAccessBucket bucket, FreenetURI uri, String contentType, HighLevelSimpleClient client, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws IOException, InsertException, InterruptedException{
        InsertCallback callback = Util.insertDataAsync(bucket, uri, contentType, client, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
        int status = callback.getStatus();
        if (status == InsertCallback.STATUS_SUCCESS){
            return callback.getInsertedURI();
        }
        else {
            throw callback.getInsertException();
        }
    }

    public static FreenetURI insertJSONObject(JSONObject object, FreenetURI uri, HighLevelSimpleClient client, BucketFactory bf, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws UnsupportedEncodingException, IOException, InsertException, InterruptedException {
        byte[] data = object.toJSONString().getBytes("UTF-8");
        return Util.insertData(data, uri, "application/json", client, bf, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
    }
    
    public static FetchCallback fetchDataAsync(FreenetURI uri, HighLevelSimpleClient client, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws FetchException, PersistenceDisabledException {
        FetchContext fctx = new FetchContext(client.getFetchContext(), IDENTICAL_MASK);
        FetchCallback callback = new FetchCallback(node, fctx, uri, persistent, realtime, pluginConnection, pluginMessage);
        callback.subscribeToContextEvents();
        ClientGetter get = new ClientGetter(callback, uri, fctx, priorityClass);
        callback.setClientGetter(get);
        node.clientCore.clientContext.start(get);
        return callback;
    }
    
    public static FetchResult fetchData(FreenetURI uri, HighLevelSimpleClient client, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws FetchException, PersistenceDisabledException, InterruptedException{
        FetchCallback callback = Util.fetchDataAsync(uri, client, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage);
        int status = callback.getStatus();
        if (status == FetchCallback.STATUS_SUCCESS){
            return callback.getResult();
        }
        else {
            throw callback.getFetchException();
        }
    }
    
    public static JSONObject fetchJSONObject(FreenetURI uri, HighLevelSimpleClient client, Node node, short priorityClass, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) throws FetchException, PersistenceDisabledException, InterruptedException, IOException, ParseException {
        byte[] data = Util.fetchData(uri, client, node, priorityClass, persistent, realtime, pluginConnection, pluginMessage).asByteArray();
        JSONParser parser = new JSONParser();
        JSONObject fetchedObject = (JSONObject) parser.parse(new String(data, "UTF-8"));
        return fetchedObject;
    }

    public static <String, User> Map<String, User> createLRUMap(final int maxEntries) {
        return Collections.synchronizedMap(new LinkedHashMap<String, User>(maxEntries * 10 / 7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, User> eldest) {
                return size() > maxEntries;
            }
        });
    }
    
    public static JSONObject exceptionToJson(Exception ex) {
        JSONObject errorObject = new JSONObject();
        errorObject.put("exception", ex.getClass().getName());
        errorObject.put("message", ex.getMessage());
        JSONArray trace = new JSONArray();
        for (StackTraceElement element: ex.getStackTrace()){
            trace.add(element.toString());
        }
        errorObject.put("trace", trace);
        return errorObject;
    }
    
    public static RandomAccessBucket ByteArrayToRandomAccessBucket(byte[] data, BucketFactory bf) throws IOException{
        RandomAccessBucket bucket = bf.makeBucket(data.length);
        OutputStream os = bucket.getOutputStream();
        os.write(data);
        os.close();
        return bucket;
    }
    
    public static FreenetURI BuildInsertURI(String key, String filename, Integer version) throws MalformedURLException{
        FreenetURI keyURI = new FreenetURI(key);
        if (filename != null && !filename.isEmpty()){
            if (!key.endsWith("/")){
                key+= "/";
            }
            key+=filename;
        }
        if (version >= 0){
            if (keyURI.isSSK()){
                key+="-"+version.toString();
            }
            else if (keyURI.isUSK()){
                key+="/"+version.toString();
            }
        }
        return new FreenetURI(key);
    }
    
    public static FCPPluginMessage constructSuccessReplyMessage(FCPPluginMessage fcppm, String origin){
        return constructReplyMessage(fcppm, origin, "Success", null, null,"", "", null);
    }
    
    public static FCPPluginMessage constructSuccessReplyMessage(FCPPluginMessage fcppm, String origin, SimpleFieldSet extraParams){
        return constructReplyMessage(fcppm, origin, "Success", extraParams, null, "", "", null);
    }
    
    public static FCPPluginMessage constructSuccessReplyMessage(FCPPluginMessage fcppm, String origin, SimpleFieldSet extraParams, Bucket data){
        return constructReplyMessage(fcppm, origin, "Success", extraParams, data, "", "", null);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin){
        return constructReplyMessage(fcppm, origin, "Failure", null, null, "", "", null);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin, SimpleFieldSet extraParams){
        return constructReplyMessage(fcppm, origin, "Failure", extraParams, null, "", "", null);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin, String errorCode, String errorMessage){
        return constructReplyMessage(fcppm, origin, "Failure", null, null, errorCode, errorMessage, null);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin, String errorCode, String errorMessage, Exception ex){
        return constructReplyMessage(fcppm, origin, "Failure", null, null,errorCode, errorMessage, ex);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin, SimpleFieldSet extraParams, String errorCode, String errorMessage){
        return constructReplyMessage(fcppm, origin, "Failure", extraParams, null, errorCode, errorMessage, null);
    }
    
    public static FCPPluginMessage constructFailureReplyMessage(FCPPluginMessage fcppm, String origin, SimpleFieldSet extraParams, String errorCode, String errorMessage, Exception ex){
        return constructReplyMessage(fcppm, origin, "Failure", extraParams, null,errorCode, errorMessage, ex);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status){
        return constructReplyMessage(fcppm, origin, status, null, null, "", "", null);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status, SimpleFieldSet extraParams){
        return constructReplyMessage(fcppm, origin, status, extraParams, null, "", "", null);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status, Bucket data, String errorCode, String errorMessage){
        return constructReplyMessage(fcppm, origin, status, null, data, errorCode, errorMessage, null);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status, SimpleFieldSet extraParams, String errorCode, String errorMessage){
        return constructReplyMessage(fcppm, origin, status, extraParams, null, errorCode, errorMessage, null);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status, SimpleFieldSet extraParams, Bucket data, String errorCode, String errorMessage){
        return constructReplyMessage(fcppm, origin, status, extraParams, data, errorCode, errorMessage, null);
    }
    
    public static FCPPluginMessage constructReplyMessage(FCPPluginMessage fcppm, String origin, String status, SimpleFieldSet extraParams, Bucket data, String errorCode, String errorMessage, Exception ex){
        SimpleFieldSet params = new SimpleFieldSet(false);
        params.putSingle("origin", origin);
        params.putSingle("status", status);
        
        if (ex != null){
            params.putSingle("JSONError", Util.exceptionToJson(ex).toJSONString());
        }
        if (extraParams != null){
            params.putAllOverwrite(extraParams);
        }
        boolean messageSuccess = false;
        if (status.equalsIgnoreCase("Success")){
            messageSuccess = true;
        }
        return FCPPluginMessage.constructReplyMessage(fcppm, params, data, messageSuccess, errorCode, errorMessage);
    }
}
