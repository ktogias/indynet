/**
 * @version 0.1
 * @author Konstantinos Togias <info@ktogias.gr>
 */
package plugins.Librenet;

import freenet.clients.http.ToadletContainer;
import freenet.pluginmanager.*;

/**
 * The plugin class
 *
 * @author Konstantinos Togias <info@ktogias.gr>
 */
public class Librenet implements FredPlugin, FredPluginThreadless {

    PluginRespirator pr; //The PluginRespirator object provided when runPlugin method is called.
    final static String basePath = "/librenet/"; //The base path under which the pugin is accessed. 

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
        LibrenetToadlet rt = new LibrenetToadlet(basePath, pr.getHLSimpleClient(), pr.getNode()); //Create the Toadlet that handles the HTTP requests
        tc.register(rt, null, rt.path(), true, false); //Resgister the Toadlet to the container
    }
}
