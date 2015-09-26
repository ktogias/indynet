
package plugins.Indynet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.json.simple.JSONObject;

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
    
    protected final IndynetCrypto crypto;
    
    public IndynetUserAuth(IndynetCrypto crypto) throws NoSuchAlgorithmException{

        this.crypto = crypto;
    }
    
    public String getUsernameHash(String username) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return crypto.SHA3(username, USERNAME_HASH_SIZE);
    }
    
    public JSONObject createAuthObject(String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException, IOException, DataLengthException, IllegalStateException, InvalidCipherTextException, InvalidKeySpecException{
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
    
    public User authenticate(JSONObject authObject, String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException, PasswordMismatchException, IOException, DataLengthException, IllegalStateException, InvalidCipherTextException, WrongPrameterException, InvalidKeySpecException, PublicKeyMismatchException{
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
        return user;
    }
    
    private String getBaseHash(String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return crypto.SHA3(username+password, BASEHASH_SIZE);
    }
    
    private String getCompareHash(String password){
        return crypto.bcryptHash(password, COMPAREHASH_ROUNDS);
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
