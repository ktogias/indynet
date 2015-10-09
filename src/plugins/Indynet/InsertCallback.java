/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ExpectedHashesEvent;
import freenet.client.events.FinishedCompressionEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.client.events.StartedCompressionEvent;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.RandomAccessBucket;
import freenet.support.io.ResumeFailedException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.TimeZone;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/**
 *
 * @author ktogias
 */
public class InsertCallback implements ClientPutCallback, RequestClient, ClientEventListener {

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_CANCELLED = 2;

    private final RandomAccessBucket bucket;
    private ClientPutter clientPutter;
    private final Node node;
    private final boolean persistent;
    private final boolean realtime;
    private int status;
    private InsertException ie;
    private FreenetURI insertedURI;

    private final Lock lock = new ReentrantLock();
    private final Condition finished = lock.newCondition();
    
    private final FCPPluginConnection pluginConnection;
    private final FCPPluginMessage pluginMessage;
    private final InsertContext context;
    private final FreenetURI uri;

    public InsertCallback(Node node, InsertContext context, FreenetURI uri, RandomAccessBucket bucket, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage) {
        this.bucket = bucket;
        this.node = node;
        this.persistent = persistent;
        this.realtime = realtime;
        this.pluginConnection = pluginConnection;
        this.pluginMessage = pluginMessage;
        this.context = context;
        this.uri = uri;
    }
    
    public InsertCallback(Node node, InsertContext context, FreenetURI uri, RandomAccessBucket bucket, boolean persistent, boolean realtime) {
        this(node, context, uri, bucket, persistent, realtime, null, null);
    }

    /**
     * Setter for clientPutter
     *
     * @param clientPutter ClientPutter : The ClientPutter object
     */
    public void setClientPutter(ClientPutter clientPutter) {
        this.clientPutter = clientPutter;
    }
    
    public void subscribeToContextEvents(){
        context.eventProducer.addEventListener(this);
    }
    
    public void unsubscribeFromContextEvents(){
        context.eventProducer.removeEventListener(this);
    }

    /**
     * Method to cancel the insert
     *
     * When called the onging insert is cancelled and rhe bucket is destroyed
     */
    public void cancel() {
        lock.lock();
        try {
            clientPutter.cancel(node.clientCore.clientContext);
            bucket.free();
            status = STATUS_CANCELLED;
            finished.signalAll();
        } finally {
            lock.unlock();
        }
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "Canceled");
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Returns the Exception thrown from a failed insert process
     *
     * @return InsertException
     */
    public InsertException getInsertException() {
        return ie;
    }

    @Override
    public void onGeneratedURI(FreenetURI furi, BaseClientPutter bcp) {
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "GeneratedURI");
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onGeneratedMetadata(Bucket bucket, BaseClientPutter bcp) {
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "GeneratedMetadata");
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onFetchable(BaseClientPutter bcp) {
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "Fetchable");
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onSuccess(BaseClientPutter bcp) {
        lock.lock();
        try {
            status = STATUS_SUCCESS;
            insertedURI = bcp.getURI();
            bucket.free();
            finished.signalAll();
        } finally {
            lock.unlock();
        }
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("insertedUri", insertedURI.toString());
                params.putSingle("status", "Success");
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, true, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onFailure(InsertException ie, BaseClientPutter bcp) {
        lock.lock();
        try {
            status = STATUS_FAILURE;
            bucket.free();
            this.ie = ie;
            finished.signalAll();
        } finally {
            lock.unlock();
        }
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "Failure");
                params.putSingle("JSONError", Util.exceptionToJson(ie).toJSONString());
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "INSERT_FAILURE", "Insert failed!"));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onResume(ClientContext cc) throws ResumeFailedException {

    }

    @Override
    public RequestClient getRequestClient() {
        return this;
    }

    @Override
    public boolean persistent() {
        return persistent;
    }

    @Override
    public boolean realTimeFlag() {
        return realtime;
    }

    /**
     * Returns the status of the insertion
     *
     * @return int
     * @throws java.lang.InterruptedException
     */
    public int getStatus() throws InterruptedException {
        lock.lock();
        try {
            finished.await();
            return status;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the URI of the inserted data after insert success
     *
     * @return FreenetURI
     */
    public FreenetURI getInsertedURI() {
        return insertedURI;
    }

    @Override
    public void receive(ClientEvent ce, ClientContext cc) {
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "InsertCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "ReceivedEvent");
                params.putSingle("eventclass", ce.getClass().getName());
                params.put("eventcode", ce.getCode());
                params.putSingle("eventdescription", ce.getDescription());
                if(ce instanceof SplitfileProgressEvent) {
                    eventToParams((SplitfileProgressEvent) ce, params);
                }
                else if (ce instanceof StartedCompressionEvent){
                    eventToParams((StartedCompressionEvent)ce, params);
                }
                else if (ce instanceof FinishedCompressionEvent){
                    eventToParams((FinishedCompressionEvent)ce, params);
                }
                else if (ce instanceof ExpectedHashesEvent){
                    eventToParams((ExpectedHashesEvent)ce, params);
                }
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    
    private void eventToParams(SplitfileProgressEvent ce, SimpleFieldSet params) {
        params.put("finalizedTotal", ce.finalizedTotal);
        params.put("totalBlocks", ce.totalBlocks);
        params.put("succeedBlocks", ce.succeedBlocks);
        params.put("failedBlocks", ce.failedBlocks);
        params.put("fatallyFailedBlocks", ce.fatallyFailedBlocks);
        params.put("minSuccessFetchBlocks", ce.minSuccessFetchBlocks);
        params.put("minSuccessfulBlocks", ce.minSuccessfulBlocks);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        if (ce.latestSuccess != null){
            Timestamp latestSuccess = new Timestamp(ce.latestSuccess.getTime());
            params.putSingle("latestSuccess", latestSuccess.toLocalDateTime().toString());
        }
        if (ce.latestFailure != null){
            Timestamp latestFailure = new Timestamp(ce.latestFailure.getTime());
            params.putSingle("latestFailure", latestFailure.toLocalDateTime().toString());
        }

    }
    
    private void eventToParams(StartedCompressionEvent ce, SimpleFieldSet params){
        params.putSingle("codec", ce.codec.name);
    }

    private void eventToParams(FinishedCompressionEvent ce, SimpleFieldSet params){
        params.put("codec", ce.codec);
        params.put("originalSize", ce.originalSize);
        params.put("compressedSize", ce.compressedSize);
    }
    
    private void eventToParams(ExpectedHashesEvent ce, SimpleFieldSet params){
        params.put("hashesLength", ce.hashes.length);
    }
}
