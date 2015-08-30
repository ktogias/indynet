/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Indynet;

import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.clients.http.ToadletContainer;
import freenet.pluginmanager.*;
import freenet.pluginmanager.FredPluginFCPMessageHandler.ServerSideFCPMessageHandler;
import freenet.support.SimpleFieldSet;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.parser.ParseException;

/**
 * The plugin class
 *
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class Indynet implements FredPlugin, FredPluginThreadless, ServerSideFCPMessageHandler {

    PluginRespirator pr; //The PluginRespirator object provided when runPlugin method is called.
    final static String BASEPATH = "/indynet/"; //The base path under which the pugin is accessed. 
    static IndynetResolver resolver;
    
   public Indynet(){
        try {
            resolver = new IndynetResolver();
        } catch (IOException e){
            Logger.getLogger(Indynet.class.getName()).log(Level.SEVERE, null, e);
            resolver = null;
        } catch (ParseException e) {
            Logger.getLogger(Indynet.class.getName()).log(Level.SEVERE, null, e);
            resolver = null;
        }
       
   }

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
        IndynetToadlet rt = new IndynetToadlet(BASEPATH, pr.getHLSimpleClient(), pr.getNode(), resolver); //Create the Toadlet that handles the HTTP requests
        tc.register(rt, null, rt.path(), true, false); //Resgister the Toadlet to the container
    }

    @Override
    public FCPPluginMessage handlePluginFCPMessage(FCPPluginConnection fcppc, FCPPluginMessage fcppm) {
        String action = fcppm.params.get("action");
        if (action.equalsIgnoreCase("resolver.register")){
            String name = fcppm.params.get("name");
            String requestKey = fcppm.params.get("requestKey");
            SimpleFieldSet params = new SimpleFieldSet(false);
            params.putSingle("name", name);
            params.putSingle("requestKey", requestKey);
            return FCPPluginMessage.constructReplyMessage(fcppm, params, null, false, "NOT_IMPLEMENTED", "Indynet: Not implemented yet");
        }
        else {
            return FCPPluginMessage.constructErrorReply(fcppm, "NOT_SUPPORTED", "Indynet: Not supported yet.");
        }
    }
}
