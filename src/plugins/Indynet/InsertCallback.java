/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClient;
import freenet.support.api.Bucket;
import freenet.support.api.RandomAccessBucket;
import freenet.support.io.ResumeFailedException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author ktogias
 */
public class InsertCallback implements ClientPutCallback, RequestClient {

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

    final Lock lock = new ReentrantLock();
    final Condition finished = lock.newCondition();

    public InsertCallback(RandomAccessBucket bucket, Node node, boolean persistent, boolean realtime) {
        this.bucket = bucket;
        this.node = node;
        this.persistent = persistent;
        this.realtime = realtime;
    }

    /**
     * Setter for clientPutter
     *
     * @param clientPutter ClientPutter : The ClientPutter object
     */
    public void setClientPutter(ClientPutter clientPutter) {
        this.clientPutter = clientPutter;
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

    }

    @Override
    public void onGeneratedMetadata(Bucket bucket, BaseClientPutter bcp) {

    }

    @Override
    public void onFetchable(BaseClientPutter bcp) {

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

}
