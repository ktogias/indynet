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
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.MultiValueTable;
import freenet.support.SimpleFieldSet;
import freenet.support.api.HTTPRequest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.json.simple.parser.ParseException;

/**
 * The Toadlet class This class actually handles the incoming HTTP requests
 *
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class IndynetToadlet extends Toadlet implements LinkEnabledCallback {

    protected String path; //The url path under witch the Toadlet is accessed
    protected String resolv_file;
    protected HighLevelSimpleClient client;
    protected Node node;
    protected ToadletContainer container;

    /**
     * Class Constructor
     *
     * @param path String : The url path under witch the Toadlet is accessed
     * @param resolv_file
     * @param client HighLevelSimpleClient
     * @param node Node
     * @param container
     */
    public IndynetToadlet(String path, String resolv_file, HighLevelSimpleClient client, Node node, ToadletContainer container) {
        super(client);
        this.path = path;
        this.resolv_file = resolv_file;
        this.client = client;
        this.node = node;
        this.container = container;
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
    public void handleMethodGET(URI uri, HTTPRequest httpr, ToadletContext tc) throws IOException, UnsupportedEncodingException, ToadletContextClosedException, FileNotFoundException {
        try {
            IndynetResolver resolver = new IndynetResolver(client, container.getBucketFactory(), node, resolv_file, path);
            uri = resolver.normalizeUri(uri);
            SimpleFieldSet uriParts = resolver.decomposeUri(uri);
            String requestKey = uriParts.get("key");
            String requestPath = uriParts.get("path");
            String requestQuery = uriParts.get("query");
            if (!resolver.isFreenetKey(requestKey)){
                try {
                    requestKey = resolver.resolve(requestKey);
                } catch (FetchException ex) {
                    writeReply(tc, 404, "text/plain", "Resolve Error", "Data Not found! "+ex.getMessage());
                    return;
                } catch (Exception ex) {
                    writeReply(tc, 500, "text/plain", "Error while resolving name", "Error while resolving name: "+ex.getClass().toString()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
                    return;
                } 
            }
            String newUriStr = requestKey;
            if (requestPath != null){
                newUriStr += requestPath;
            }
            if (requestQuery != null){
                newUriStr += "?"+requestQuery;
            }
            FreenetURI furi = new FreenetURI(newUriStr);
            ftechFreenetURI(furi, requestKey, uriParts.get("key"), tc);
        } catch (URISyntaxException ex) {
            writeReply(tc, 400, "text/plain", "Bad Request", ex.getClass().toString()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (ParseException ex) {
            writeReply(tc, 500, "text/plain", "error", "Resolver JSON parse error!");
        }
    }

    private String getKeyFromUri(URI uri) throws Exception {
        return uri.getPath().replaceFirst(path, "");
    }

    private SimpleFieldSet decomposeNamedKey(String key) {
        SimpleFieldSet decomposition = new SimpleFieldSet(false);
        String[] parts = key.split("/");
        decomposition.putSingle("name", parts[0]);
        String keypath = "";
        for (int i = 1; i < parts.length; i++) {
            keypath += "/" + parts[i];
        }
        decomposition.putSingle("path", keypath);
        return decomposition;
    }

    private void ftechFreenetURI(FreenetURI furi, String name, String requestKey, ToadletContext tc) throws ToadletContextClosedException, IOException, URISyntaxException {
        FetchResult result;
        try {
            result = client.fetch(furi);
            writeReply(tc, 200, result.getMimeType(), "", result.asBucket());
        } catch (FetchException ex) {
            if (ex.isDNF()) {
                writeReply(tc, 404, "text/plain", "error", "Not found!");
            } else if (ex.getMode().equals(FetchException.FetchExceptionMode.PERMANENT_REDIRECT)) {
                MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
                headers.put("Location", path + "/" + ex.newURI.toString().replaceFirst(requestKey, name));
                tc.sendReplyHeaders(302, "Found", headers, null, 0);
            } else if (ex.newURI != null) {
                ftechFreenetURI(ex.newURI, name, requestKey, tc);
            } else {
                writeReply(tc, 500, "text/plain", "error", "key: " + furi.toString() + " " + ex.toString());
            }
        }
    }
}
