/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ExpectedHashesEvent;
import freenet.client.events.ExpectedMIMEEvent;
import freenet.client.events.FinishedCompressionEvent;
import freenet.client.events.SplitfileCompatibilityModeEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.client.events.StartedCompressionEvent;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.support.SimpleFieldSet;
import freenet.support.io.ResumeFailedException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author ktogias
 */
public class FetchCallback implements ClientGetCallback, RequestClient, ClientEventListener{
    
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_CANCELLED = 2;
    
    private final Node node;
    private final FetchContext context;
    private final FreenetURI uri;
    private final boolean persistent;
    private final boolean realtime;
    private final FCPPluginConnection pluginConnection;
    private final FCPPluginMessage pluginMessage;
    private ClientGetter getter;
    private FetchResult result;
    private FetchException fe;
    
    private int status;
    
    final Lock lock = new ReentrantLock();
    final Condition finished = lock.newCondition();

    public FetchCallback(Node node, FetchContext context, FreenetURI uri, boolean persistent, boolean realtime, FCPPluginConnection pluginConnection, FCPPluginMessage pluginMessage){
        this.node = node;
        this.context = context;
        this.uri = uri;
        this.persistent = persistent;
        this.realtime = realtime;
        this.pluginConnection = pluginConnection;
        this.pluginMessage = pluginMessage;
    }
    
    public void setClientGetter(ClientGetter getter){
        this.getter = getter;
    }
    
    public void subscribeToContextEvents(){
        context.eventProducer.addEventListener(this);
    }
    
    public void unsubscribeFromContextEvents(){
        context.eventProducer.removeEventListener(this);
    }
    
    public FetchException getFetchException() {
        return fe;
    }
    
    public void cancel() {
        lock.lock();
        try {
            getter.cancel(node.clientCore.clientContext);
            status = STATUS_CANCELLED;
            finished.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void onSuccess(FetchResult fr, ClientGetter cg) {
        lock.lock();
        try {
            status = STATUS_SUCCESS;
            this.result = fr;
            finished.signalAll();
        } finally {
            lock.unlock();
        }
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "FetchCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "Success");
                params.putSingle("dataMimeType", result.getMimeType());
                params.put("dataSize", result.size());
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, result.asBucket(), true, "", ""));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onFailure(FetchException fe, ClientGetter cg) {
        lock.lock();
        try {
            status = STATUS_FAILURE;
            this.fe = fe;
            finished.signalAll();
        } finally {
            lock.unlock();
        }
        if (pluginConnection != null){
            try {
                SimpleFieldSet params = new SimpleFieldSet(false);
                params.putSingle("origin", "FetchCallback");
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "Failure");
                params.putSingle("JSONError", Util.exceptionToJson(fe).toJSONString());
                pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, false, "FETCH_FAILURE", "Fetch failed!"));
            } catch (IOException ex) {
                Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void onResume(ClientContext cc) throws ResumeFailedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
                else if (ce instanceof ExpectedMIMEEvent){
                    eventToParams((ExpectedMIMEEvent)ce, params);
                }
                else if (ce instanceof SplitfileCompatibilityModeEvent){
                    eventToParams((SplitfileCompatibilityModeEvent)ce, params);
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
    
    private void eventToParams(ExpectedMIMEEvent ce, SimpleFieldSet params){
        params.putSingle("expectedMIMEType", ce.expectedMIMEType);
    }

    private void eventToParams(SplitfileCompatibilityModeEvent ce, SimpleFieldSet params){
        params.putSingle("maxCompatibilityMode", ce.maxCompatibilityMode.name());
        params.putSingle("minCompatibilityMode", ce.minCompatibilityMode.name());
    }
    
    private void eventToParams(ExpectedHashesEvent ce, SimpleFieldSet params){
        params.put("hashesLength", ce.hashes.length);
    }
    
    public int getStatus() throws InterruptedException {
        lock.lock();
        try {
            finished.await();
            return status;
        } finally {
            lock.unlock();
        }
    }
    
    public FetchResult getResult(){
        return result;
    }
    
}
