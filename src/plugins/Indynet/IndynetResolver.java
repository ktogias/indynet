/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.keys.Key;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author ktogias
 */
public class IndynetResolver {
    final static String configFile = "indynet.resolv.json";
    static String insertKey;
    static String requestKey;
    
    public IndynetResolver() throws FileNotFoundException, IOException, ParseException{
        JSONObject keys = readKeys();
        insertKey = (String) keys.get("insertKey");
        requestKey = (String) keys.get("requestKey");
    }
    
    private JSONObject readKeys() throws IOException, ParseException{
        JSONParser parser = new JSONParser();
        JSONObject keys = (JSONObject) parser.parse(new FileReader(configFile));
        return keys;
    }
    
    public String resolve(String key){
        return requestKey;
    }
    
    public void register(String name, String insertKey) throws MalformedURLException{
        FreenetURI uri = new FreenetURI(insertKey);
        /*JSONObject response = new JSONObject();
        response.put("requestURI", callback.getInsertedURI().toString());*/
    }
}
