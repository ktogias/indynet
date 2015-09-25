
package plugins.Indynet;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;
import freenet.support.api.BucketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author ktogias
 */
public class IndynetUserAuth {
    
    
    protected static final int BASEHASH_SIZE = 256;
    protected static final int COMPAREHASH_ROUNDS = 11;
    protected static final int PBKDF2_SALT_LENGTH_MIN = 32;
    protected static final int PBKDF2_SALT_LENGTH_MAX = 64;
    protected static final int PBKDF2_ITERATIONS_MIN = 100000;
    protected static final int PBKDF2_ITERATIONS_MAX = 200000;
    protected static final int PBKDF2_KEY_SIZE = 1024;
    protected static final int RSA_KEY_SIZE = 4096;
    protected static final int AES_IV_SIZE = 16;
    protected static final int USERNAME_HASH_SIZE = 192;
    protected final Map<String, User> usersCache;
    
    protected String AUTH_FILE;
    
    protected final HighLevelSimpleClient client;
    protected final BucketFactory bf;
    protected final Node node;
    
    protected final IndynetCrypto crypto;
    protected String authInsertKey;
    protected String authRequestKey;
    
    public IndynetUserAuth(Map<String, User> usersCache, HighLevelSimpleClient client, BucketFactory bf, Node node, String authFile) throws NoSuchAlgorithmException, IOException, ParseException{
        this(usersCache, client, bf, node);
        this.AUTH_FILE = authFile;
        JSONObject authKeys = readAuthKeys();
        this.authInsertKey = (String) authKeys.get("insertKey");
        this.authRequestKey = (String) authKeys.get("requestKey");
    }
    
    public IndynetUserAuth(Map<String, User> usersCache, HighLevelSimpleClient client, BucketFactory bf, Node node, String authInsertKey, String authRequestKey) throws NoSuchAlgorithmException{
        this(usersCache, client, bf, node);
        this.authInsertKey = authInsertKey;
        this.authRequestKey = authRequestKey;
    }
    
    private IndynetUserAuth(Map<String, User> usersCache, HighLevelSimpleClient client, BucketFactory bf, Node node) throws NoSuchAlgorithmException{
        this.usersCache = usersCache;
        this.client = client;
        this.bf = bf;
        this.node = node;
        this.crypto = new IndynetCrypto();
    }
    
    public SimpleFieldSet signup(String username, String password) throws NoSuchAlgorithmException, IOException, UnsupportedEncodingException, DataLengthException, IllegalStateException, InvalidCipherTextException, InvalidKeySpecException, WrongPrameterException, InsertException, InterruptedException{
        JSONObject authObject = createAuthObject(username, password);
        FreenetURI authInsertURI = buildAuthInsertUri(username);
        username = null;
        password = null;
        InsertCallback callback = Util.insertJSONObject(authObject, authInsertURI, client, bf, node);
        int status = callback.getStatus();
        SimpleFieldSet result = new SimpleFieldSet(false);
        result.put("status", status);
        if (status == InsertCallback.STATUS_SUCCESS){
            result.putSingle("resolveURI", callback.getInsertedURI().toString());
        }
        else {
            result.putSingle("error", callback.getInsertException().getMessage());
        }
        return result;
    }
    
    public SimpleFieldSet signin(String username, String password) throws FetchException, ParseException, UnsupportedEncodingException, IOException, WrongPrameterException, NoSuchAlgorithmException, PasswordMismatchException, DataLengthException, IllegalStateException, InvalidCipherTextException, InvalidKeySpecException, PublicKeyMismatchException {
        FetchResult fetched = client.fetch(buildAuthRequestUri(username));
        JSONParser parser = new JSONParser();
        JSONObject authObject = (JSONObject) parser.parse(new String(fetched.asByteArray(), "UTF-8"));
        String userHash = authenticate(authObject, username, password);
        SimpleFieldSet result = new SimpleFieldSet(false);
        result.putSingle("status", "success");
        result.putSingle("userHash", userHash);
        return result;
    }
    
    private JSONObject readAuthKeys() throws IOException, ParseException{
        JSONParser parser = new JSONParser();
        JSONObject keys = (JSONObject) parser.parse(new FileReader(AUTH_FILE));
        return keys;
    }
    
    private JSONObject createAuthObject(String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException, IOException, DataLengthException, IllegalStateException, InvalidCipherTextException, InvalidKeySpecException{
        String baseHash = getBaseHash(username, password);
        String compareHash = getCompareHash(password);
        username = null;
        password = null; 
        int saltLength = crypto.generateRandomInt(PBKDF2_SALT_LENGTH_MIN, PBKDF2_SALT_LENGTH_MAX);
        String salt = crypto.generateRandomSalt(saltLength);
        int iterations = crypto.generateRandomInt(PBKDF2_ITERATIONS_MIN, PBKDF2_ITERATIONS_MAX);
        String pbkey = crypto.generatePBKDF2Key(baseHash, salt, iterations, PBKDF2_KEY_SIZE);
        RSAKeyPair rsaKey = new RSAKeyPair(RSA_KEY_SIZE, pbkey, crypto);
        Secret secret = new Secret(baseHash, salt, iterations);
        JSONObject authObject = new JSONObject();
        authObject.put("hash", compareHash);
        authObject.put("sec", secret.getEncrypted());
        authObject.put("pubkey", rsaKey.getPublicKey());
        return authObject;
    }
    
    private String authenticate(JSONObject authObject, String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException, PasswordMismatchException, IOException, DataLengthException, IllegalStateException, InvalidCipherTextException, WrongPrameterException, InvalidKeySpecException, PublicKeyMismatchException{
        String compareHash = (String)authObject.get("hash");
        if (!crypto.bcryptCompare(password, compareHash)){
            throw new PasswordMismatchException("Wrong password");
        }
        String baseHash = getBaseHash(username, password);
        username = null;
        password = null;
        Secret secret = new Secret(baseHash, (String)authObject.get("sec"));
        String pbkey = crypto.generatePBKDF2Key(baseHash, secret.getSalt(), secret.getIterations(), PBKDF2_KEY_SIZE);
        secret = null;
        RSAKeyPair rsaKey = new RSAKeyPair(RSA_KEY_SIZE, pbkey, crypto);
        if (!rsaKey.getPublicKey().equals((String)authObject.get("pubkey"))){
            throw new PublicKeyMismatchException("Public key Mismatch");
        }
        User user = new User(pbkey, rsaKey, crypto);
        return user.getHash();
    }
    
    private String getBaseHash(String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return crypto.SHA3(username+password, BASEHASH_SIZE);
    }
    
    private String getCompareHash(String password){
        return crypto.bcryptHash(password, COMPAREHASH_ROUNDS);
    }
    
    private FreenetURI buildAuthInsertUri(String username) throws WrongPrameterException, NoSuchAlgorithmException, UnsupportedEncodingException, MalformedURLException{
        try {
            if (username.isEmpty()){
                throw new WrongPrameterException("Username is empty");
            }
        }
        catch (NullPointerException e){
            throw new WrongPrameterException("Username is missing");
        }
        String usernameHash = crypto.SHA3(username, USERNAME_HASH_SIZE);
        return new FreenetURI(authInsertKey+"/"+usernameHash);
    } 
    
    private FreenetURI buildAuthRequestUri(String username) throws WrongPrameterException, NoSuchAlgorithmException, UnsupportedEncodingException, MalformedURLException{
        try {
            if (username.isEmpty()){
                throw new WrongPrameterException("Username is empty");
            }
        }
        catch (NullPointerException e){
            throw new WrongPrameterException("Username is missing");
        }
        String usernameHash = crypto.SHA3(username, USERNAME_HASH_SIZE);
        return new FreenetURI(authRequestKey+"/"+usernameHash);
    } 
    
    private class Secret {
        private String baseHash;
        private String encrypted;
        private String iv;
        private String salt;
        private int iterations;
        public Secret(String baseHash, String encrypted) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException, WrongPrameterException{
            this.baseHash = baseHash;
            this.encrypted = encrypted;
            try {
                String [] parts = encrypted.split("[|]");
                this.iv = parts[0];
                String secret = decrypt(parts[1]);
                try {
                    String [] secretParts = secret.split("[|]");
                    this.salt = secretParts[0];
                    this.iterations = Integer.parseInt(secretParts[1]);
                }
                catch (NullPointerException ex){
                    throw new WrongPrameterException("Wrong secret string format");
                }
            }
            catch (NullPointerException ex){
                throw new WrongPrameterException("Wrong sec string format");
            }
        }
        
        public Secret(String baseHash, String salt, int iterations) throws NoSuchAlgorithmException, IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
            this.baseHash = baseHash;
            this.salt = salt;
            this.iterations = iterations;
            String plainStr = salt+"|"+Integer.toString(iterations);
            this.iv = crypto.generateRandomSalt(AES_IV_SIZE);
            this.encrypted = this.iv+"|"+encrypt(plainStr);
        }
        
        private String decrypt(String encryptedStr) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
            InputStream istream = new ByteArrayInputStream(crypto.base64Decode(encryptedStr));
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            crypto.AESDecrypt(istream, ostream, baseHash, iv);
            String secret = new String(ostream.toByteArray(), StandardCharsets.UTF_8);
            return secret;
        }
        
        private String encrypt(String plainStr) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
            InputStream istream = new ByteArrayInputStream(plainStr.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            crypto.AESEncrypt(istream, ostream, baseHash, iv);
            return crypto.base64Encode(ostream.toByteArray());
        }
        
        public String getSalt(){
            return this.salt;
        }
        
        public int getIterations(){
            return this.iterations;
        }
        
        public String getEncrypted(){
            return this.encrypted;
        }
    }
    
}
