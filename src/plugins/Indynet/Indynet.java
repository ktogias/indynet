/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Indynet;

import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.clients.http.ToadletContainer;
import freenet.node.RequestStarter;
import freenet.pluginmanager.*;
import freenet.pluginmanager.FredPluginFCPMessageHandler.ServerSideFCPMessageHandler;
import freenet.support.SimpleFieldSet;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * The plugin class
 *
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class Indynet implements FredPlugin, FredPluginThreadless, ServerSideFCPMessageHandler {

    private PluginRespirator pr; //The PluginRespirator object provided when runPlugin method is called.
    private final static String BASEPATH = "/indy:/"; //The base path under which the pugin is accessed. 
    private final static String RESOLV_FILE = "indynet.resolv.json";
    private final static int USERS_CACHE_SIZE = 50;
    private Map<String, User> usersCache;
    private IndynetCrypto crypto;
    
    /**
     * Dummy implementation of terminate method. 
     */
    @Override
    public void terminate() {
        usersCache.clear();
    }

    /**
     * Implementation of runPlugin method. This method runs when the plugin is
     * enabled.
     *
     * @param pr PluginRespirator : The PluginRespirator object
     */
    @Override
    public void runPlugin(PluginRespirator pr) {
        try {
            this.pr = pr;
            this.usersCache = Util.createLRUMap(USERS_CACHE_SIZE);
            this.crypto = new IndynetCrypto();
            ToadletContainer tc = pr.getToadletContainer(); //Get the container
            IndynetToadlet rt = new IndynetToadlet(BASEPATH, RESOLV_FILE, pr.getHLSimpleClient(), pr.getNode(), pr.getToadletContainer()); //Create the Toadlet that handles the HTTP requests
            tc.register(rt, null, rt.path(), true, false); //Resgister the Toadlet to the container
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Indynet.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        else if (action.equalsIgnoreCase("userauth.getUsernameHash")){
            return handleUserAuthGetUsernameHashFCPMessage(fcppc, fcppm);
        }
        else if (action.equalsIgnoreCase("userauth.createAuthObject")){
            return handleUserAuthCreateAuthObjectFCPMessage(fcppc, fcppm);
        }
        else if (action.equalsIgnoreCase("userauth.authenticate")){
            return handleUserAuthAuthenticateFCPMessage(fcppc, fcppm);
        }
        else {
            return FCPPluginMessage.constructErrorReply(fcppm, "NOT_SUPPORTED", "Indynet: Action not supported.");
        }
    }
    
    private FCPPluginMessage handleResolverRegisterFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        String name = fcppm.params.get("name");
        String requestKey = fcppm.params.get("requestKey");
        boolean persistent = fcppm.params.getBoolean("persistent", false);
        boolean realtime = fcppm.params.getBoolean("realtime", false);
        short priorityClass = fcppm.params.getShort("priorityClass", RequestStarter.INTERACTIVE_PRIORITY_CLASS);
        SimpleFieldSet params;
        try {
            IndynetResolver resolver = new IndynetResolver(pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), RESOLV_FILE, BASEPATH);
            params = resolver.register(requestKey, name, fcppc, fcppm, priorityClass, persistent, realtime);
            if (params.getInt("status") == InsertCallback.STATUS_SUCCESS){
                return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
            }
            else {
                return FCPPluginMessage.constructErrorReply(fcppm, "REGISTER_ERROR", params.get("error"));
            }
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "REGISTER_ERROR", ex.getClass().getName()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } 
    }
    
    private FCPPluginMessage handleResolverResolveFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        String name = fcppm.params.get("name");
        boolean persistent = fcppm.params.getBoolean("persistent", false);
        boolean realtime = fcppm.params.getBoolean("realtime", false);
        short priorityClass = fcppm.params.getShort("priorityClass", RequestStarter.INTERACTIVE_PRIORITY_CLASS);
        SimpleFieldSet params = new SimpleFieldSet(false);
        try {
            IndynetResolver resolver = new IndynetResolver(pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), RESOLV_FILE, BASEPATH);
            String requestKey = resolver.resolveName(name, fcppc, fcppm, priorityClass, persistent, realtime);
            params.putSingle("requestKey", requestKey);
            params.putSingle("status", "final");
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "RESOLVE_ERROR", Util.exceptionToJson(ex).toJSONString());
        }
    }
    
    private FCPPluginMessage handleUserAuthGetUsernameHashFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        try {
            IndynetUserAuth auth = new IndynetUserAuth(crypto);
            String hash = auth.getUsernameHash(fcppm.params.get("username"));
            fcppm.params.removeValue("username");
            SimpleFieldSet params = new SimpleFieldSet(false);
            params.putSingle("hash", hash);
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "GET_USERNAME_HASH", ex.getClass().getName()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        }
        
    }
    
    private FCPPluginMessage handleUserAuthCreateAuthObjectFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        try {
            IndynetUserAuth auth = new IndynetUserAuth(crypto);
            JSONObject authObject = auth.createAuthObject(fcppm.params.get("username"), fcppm.params.get("password"));
            fcppm.params.removeValue("username");
            fcppm.params.removeValue("password");
            SimpleFieldSet params = new SimpleFieldSet(false);
            params.putSingle("authObject", authObject.toJSONString());
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "CREATE_AUTH_OBJECT", ex.getClass().getName()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } 
    }
    
    private FCPPluginMessage handleUserAuthAuthenticateFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        try {
            JSONParser parser = new JSONParser();
            IndynetUserAuth auth = new IndynetUserAuth(crypto);
            User user = auth.authenticate(
                    (JSONObject) parser.parse(fcppm.params.get("authObject")), 
                    fcppm.params.get("username"), 
                    fcppm.params.get("password"));
            fcppm.params.removeValue("authObject");
            fcppm.params.removeValue("username");
            fcppm.params.removeValue("password");
            if (!usersCache.containsKey(user.getHash())){
                usersCache.put(user.getHash(), user);
            }
            SimpleFieldSet params = new SimpleFieldSet(false);
            params.putSingle("status", "success");
            params.putSingle("userHash", user.getHash());
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
        } catch (Exception ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "AUTHENTICATE", ex.getClass().getName()+" "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } 
    }
}
