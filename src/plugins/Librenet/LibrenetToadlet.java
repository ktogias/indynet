/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Librenet;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.FProxyFetchTracker;
import static freenet.clients.http.FProxyToadlet.MAX_LENGTH_NO_PROGRESS;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.RequestClientBuilder;
import freenet.support.api.HTTPRequest;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Toadlet class
 * This class actually handles the incoming HTTP requests 
 * 
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class LibrenetToadlet extends Toadlet implements LinkEnabledCallback{
    protected String path; //The url path under witch the Toadlet is accessed
    protected HighLevelSimpleClient client;
    protected Node node;
    
    /**
     * Class Constructor
     * 
     * @param path String : The url path under witch the Toadlet is accessed
     * @param client HighLevelSimpleClient
     * @param node Node
     */
    public LibrenetToadlet(String path, HighLevelSimpleClient client, Node node) {
        super(client);
        this.path = path;
        this.client = client;
        this.node = node;
    }
    
    /**
     * Resturns the path
     * 
     * @return String 
     */
    @Override
    public String path() {
            return path;
    }
    
    /**
     * isEnabled returns true
     * 
     * @param tc ToadletContext : The Context object
     * @return boolean
     */
    @Override
    public boolean isEnabled(ToadletContext tc) {
        return true;
    }
    
    @Override
    public void handleMethodGET(URI uri, HTTPRequest httpr, ToadletContext tc) throws ToadletContextClosedException, IOException{
        String key = null;
        try {
            key = getKeyFromUri(uri);
        }
        catch (Exception e){
            writeReply(tc, 400, "text/plain", "error", "Bad request");
        }
        FreenetURI furi = new FreenetURI(key); 
        FetchResult result;
        try {
            result = client.fetch(furi);
            writeReply(tc, 200, result.getMimeType(), "", result.asBucket());
        } catch (FetchException ex) {
            if (ex.isDNF()){
                writeReply(tc, 404, "text/plain", "error", "Not found!");
            }
            else {
                writeReply(tc, 500, "text/plain", "error", "key: "+key+" "+ex.toString());
            }
        }
    }
    
    private String getKeyFromUri(URI uri) throws Exception{
        String[] parts = uri.getPath().split("/");
        String key = parts[2];
        if (parts.length > 3){
            String filename = parts[3];
            key+="/"+filename;
        }
        return key;
    }
}
