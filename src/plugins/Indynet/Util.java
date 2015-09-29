/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import freenet.client.ClientMetadata;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestStarter;
import freenet.support.api.BucketFactory;
import freenet.support.api.RandomAccessBucket;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author ktogias
 */
public class Util {

    public static InsertCallback insertJSONObject(JSONObject object, FreenetURI uri, HighLevelSimpleClient client, BucketFactory bf, Node node) throws UnsupportedEncodingException, IOException, InsertException {
        byte[] data = object.toJSONString().getBytes("UTF-8");
        RandomAccessBucket bucket = bf.makeBucket(data.length + 1);
        OutputStream os = bucket.getOutputStream();
        os.write(data);
        os.close();
        os = null;
        bucket.setReadOnly();
        ClientMetadata metadata = new ClientMetadata("application/json");
        InsertBlock ib = new InsertBlock(bucket, metadata, uri);
        InsertContext ictx = client.getInsertContext(true);
        InsertCallback callback = new InsertCallback(bucket, node, false, false);
        ClientPutter pu
                = client.insert(ib, null, false, ictx, callback, RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
        callback.setClientPutter(pu);
        return callback;
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
}
