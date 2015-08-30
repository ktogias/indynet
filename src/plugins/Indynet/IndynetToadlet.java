/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Indynet;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import java.io.IOException;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Toadlet class
 * This class actually handles the incoming HTTP requests 
 * 
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class IndynetToadlet extends Toadlet implements LinkEnabledCallback{
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
    public IndynetToadlet(String path, HighLevelSimpleClient client, Node node) {
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
        FreenetURI furi = null;
        try {
            key = getKeyFromUri(uri);
            furi = new FreenetURI(key); 
        }
        catch (MalformedURLException e){
            writeReply(tc, 400, "text/plain", "error", "Malformed key: "+key);
            return;
        }
        catch (Exception e){
            writeReply(tc, 400, "text/plain", "error", "Bad request");
            return;
        }
        try {
            ftechFreenetURI(furi, tc);
        } catch (URISyntaxException ex) {
            writeReply(tc, 500, "text/plain", "error", "key: "+furi.toString()+" "+ex.toString());
        }
    }
    
    private String getKeyFromUri(URI uri) throws Exception{
        return uri.getPath().replaceFirst(path, "");
    }
    
    private void ftechFreenetURI(FreenetURI furi, ToadletContext tc) throws ToadletContextClosedException, IOException, URISyntaxException{
        FetchResult result;
        try {
            result = client.fetch(furi);
            writeReply(tc, 200, result.getMimeType(), "", result.asBucket());
        } catch (FetchException ex) {
            if (ex.isDNF()){
                writeReply(tc, 404, "text/plain", "error", "Not found!");
            }
            else if (ex.getMode().equals(FetchException.FetchExceptionMode.PERMANENT_REDIRECT)){
                MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
                headers.put("Location", path+ex.newURI.toString());
		tc.sendReplyHeaders(302, "Found", headers, null, 0);
            }
            else if (ex.newURI != null){
                ftechFreenetURI(ex.newURI, tc);
            }
            else {
                writeReply(tc, 500, "text/plain", "error", "key: "+furi.toString()+" "+ex.toString());
            }
        }
    }
}
