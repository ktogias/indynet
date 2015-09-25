/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Indynet;

import freenet.client.FetchException;
import freenet.client.InsertException;
import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.clients.http.ToadletContainer;
import freenet.node.FSParseException;
import freenet.pluginmanager.*;
import freenet.pluginmanager.FredPluginFCPMessageHandler.ServerSideFCPMessageHandler;
import freenet.support.SimpleFieldSet;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
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
    final static String AUTH_FILE = "indynet.auth.json";
    private Map<String, User> usersCache;
    
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
        this.usersCache = Util.createLRUMap(50);
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
        else if (action.equalsIgnoreCase("userauth.signup")){
            return handleUserAuthSignupFCPMessage(fcppc, fcppm);
        }
        else if (action.equalsIgnoreCase("userauth.signin")){
            return handleUserAuthSigninFCPMessage(fcppc, fcppm);
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
    
    private FCPPluginMessage handleUserAuthSignupFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm) {
        String username = fcppm.params.get("username");
        String password = fcppm.params.get("password");
        String authInsertKey = fcppm.params.get("authInsertKey");
        String authRequestKey = fcppm.params.get("authRequestKey");
        
        SimpleFieldSet params;
        IndynetUserAuth auth;
        try {
            if (authInsertKey == null && authRequestKey == null){
                auth = new IndynetUserAuth(usersCache, pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), AUTH_FILE);
            }
            else {
                auth = new IndynetUserAuth(usersCache, pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), authInsertKey, authRequestKey);
            }
            params = auth.signup(username, password);
            if (params.getInt("status") == InsertCallback.STATUS_SUCCESS){
                return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
            }
            else {
                return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", params.get("error"));
            }
        }
        catch(IOException ex){
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "IOException "+ex.getMessage());
        } catch (ParseException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "ParseException "+ex.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "NoSuchAlgorithmException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (DataLengthException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "DataLengthException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (IllegalStateException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "IllegalStateException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InvalidCipherTextException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "InvalidCipherTextException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InvalidKeySpecException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "InvalidKeySpecException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        }  catch (WrongPrameterException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "WrongPrameterException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InsertException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "InsertException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InterruptedException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "InterruptedException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (FSParseException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNUP_ERROR", "FSParseException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } 
    }
    
    private FCPPluginMessage handleUserAuthSigninFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm){
        String username = fcppm.params.get("username");
        String password = fcppm.params.get("password");
        String authInsertKey = fcppm.params.get("authInsertKey");
        String authRequestKey = fcppm.params.get("authRequestKey");
        
        SimpleFieldSet params;
        IndynetUserAuth auth;
        try {
            if (authInsertKey == null && authRequestKey == null){
                auth = new IndynetUserAuth(usersCache, pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), AUTH_FILE);
            }
            else {
                auth = new IndynetUserAuth(usersCache, pr.getHLSimpleClient(), pr.getToadletContainer().getBucketFactory(), pr.getNode(), authInsertKey, authRequestKey);
            }
            params = auth.signin(username, password);
            if (params.getInt("status") == InsertCallback.STATUS_SUCCESS){
                return FCPPluginMessage.constructReplyMessage(fcppm, params, null, true, "", "");
            }
            else {
                return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", params.get("error"));
            }
        }
        catch (PasswordMismatchException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "PasswordMismatch");
        } catch (PublicKeyMismatchException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "PublicKeyMismatch");
        } catch(IOException ex){
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "IOException "+ex.getMessage());
        } catch (ParseException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "ParseException "+ex.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "NoSuchAlgorithmException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (DataLengthException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "DataLengthException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (IllegalStateException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "IllegalStateException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InvalidCipherTextException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "InvalidCipherTextException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (InvalidKeySpecException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "InvalidKeySpecException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (FetchException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "FetchException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (WrongPrameterException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "WrongPrameterException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        } catch (FSParseException ex) {
            return FCPPluginMessage.constructErrorReply(fcppm, "SIGNIN_ERROR", "FSParseException "+ex.getMessage()+" "+Arrays.toString(ex.getStackTrace()));
        }
    }
}
