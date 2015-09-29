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
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.support.SimpleFieldSet;
import freenet.support.io.ResumeFailedException;
import java.io.IOException;
import java.util.Arrays;
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
                params.putSingle("uri", uri.toString());
                params.putSingle("status", "success");
                params.putSingle("dataMimeType", result.getMimeType());
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
                JSONObject errorObject = new JSONObject();
                errorObject.put("requestedURI", uri.toString());
                errorObject.put("trace", Util.exceptionToJson(fe));
                pluginConnection.send(FCPPluginMessage.constructErrorReply(pluginMessage, "FETCH_ERROR", errorObject.toJSONString()));
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
        try {
            SimpleFieldSet params = new SimpleFieldSet(false);
            params.putSingle("uri", uri.toString());
            params.putSingle("status", "progress");
            params.putSingle("eventclass", ce.getClass().getName());
            params.put("eventcode", ce.getCode());
            params.putSingle("eventdescription", ce.getDescription());
            pluginConnection.send(FCPPluginMessage.constructReplyMessage(pluginMessage, params, null, true, "", ""));
        } catch (IOException ex) {
            Logger.getLogger(FetchCallback.class.getName()).log(Level.SEVERE, null, ex);
        }
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
