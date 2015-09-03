/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Indynet;

import freenet.client.FetchException;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.clients.http.ToadletContainer;
import freenet.pluginmanager.*;
import freenet.pluginmanager.FredPluginFCPMessageHandler.ServerSideFCPMessageHandler;
import freenet.support.SimpleFieldSet;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/**
 * The plugin class
 *
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class Indynet implements FredPlugin, FredPluginThreadless, ServerSideFCPMessageHandler {

    PluginRespirator pr; //The PluginRespirator object provided when runPlugin method is called.
    final static String BASEPATH = "/indy:/"; //The base path under which the pugin is accessed. 
    final static String RESOLV_FILE = "indynet.resolv.json";
    
    /**
     * Dummy implementation of terminate method. 
     */
    @Override
    public void terminate() {

    }

    /**
     * Implementation of runPlugin method. This method runs when the plugin is
     * enabled.
     *
     * @param pr PluginRespirator : The PluginRespirator object
     */
    @Override
    public void runPlugin(PluginRespirator pr) {
        this.pr = pr;
        ToadletContainer tc = pr.getToadletContainer(); //Get the container
        IndynetToadlet rt = new IndynetToadlet(BASEPATH, RESOLV_FILE, pr.getHLSimpleClient(), pr.getNode(), pr.getToadletContainer()); //Create the Toadlet that handles the HTTP requests
        tc.register(rt, null, rt.path(), true, false); //Resgister the Toadlet to the container
    }

    @Override
    public FCPPluginMessage handlePluginFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm) {
        String action = fcppm.params.get("action");
        if (action.equalsIgnoreCase("resolver.register")){
            return handleResolverRegisterFCPMessage(fcppc, fcppm);
        }
        else if (action.equalsIgnoreCase("resolver.resolve")){
            return handleResolverResolveFCPMessage(fcppc, fcppm);
        }
        else {
            return FCPPluginMessage.constructErrorReply(fcppm, "NOT_SUPPORTED", "Indynet: Action not supported.");
        }
    }
    
    private FCPPluginMessage handleResolverRegisterFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        String name = fcppm.params.get("name");
        String requestKey = fcppm.params.get("requestKey");
        SimpleFieldSet params;
        try {
            IndynetResolver resolver = new IndynetResolver(pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), RESOLV_FILE);
            params = resolver.register(requestKey, name);
            if (params.getInt("status") == InsertCallback.STATUS_SUCCESS){
                return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
            }
            else {
                return FCPPluginMessage.constructErrorReply(fcppm, "REGISTER_ERROR", params.get("error"));
            }
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "REGISTER_ERROR", ex.getMessage());
        } 
    }
    
    private FCPPluginMessage handleResolverResolveFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        String name = fcppm.params.get("name");
        SimpleFieldSet params = new SimpleFieldSet(false);
        try {
            IndynetResolver resolver = new IndynetResolver(pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), RESOLV_FILE);
            JSONObject requestObject = resolver.resolve(name);
            params.putSingle("json", requestObject.toJSONString());
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
        }
        catch (FetchException ex){
            return FCPPluginMessage.constructErrorReply(fcppm, "RESOLVE_ERROR", "FetchException "+ex.getMessage());
        }
        catch(IOException ex){
            return FCPPluginMessage.constructErrorReply(fcppm, "RESOLVE_ERROR", "IOException "+ex.getMessage());
        }
        catch (ParseException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "RESOLVE_ERROR", "ParseException "+ex.getMessage());
        } 
    }
}
