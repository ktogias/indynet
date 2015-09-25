
package plugins.Indynet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.prng.DigestRandomGenerator;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.mindrot.jbcrypt.BCrypt;



/**
 *
 * @author ktogias
 */
public class IndynetCrypto {
    
    BouncyCastleProvider BCP;
    DigestRandomGenerator drgenerator;
    int blockSize = 4096;
            
    /**
     *
     * @throws NoSuchAlgorithmException
     */
    public IndynetCrypto() throws NoSuchAlgorithmException{
        BCP = new BouncyCastleProvider();
        Security.addProvider(BCP);
        drgenerator = new DigestRandomGenerator(new SHA3Digest(512));
        reseedDRGenerator();
    }
    
    /**
     *
     * @param message
     * @param salt
     * @return
     */
    public String bcryptHash(String message, String salt){
        return BCrypt.hashpw(message, salt);
    }
    
    /**
     *
     * @param message
     * @param rounds
     * @return
     */
    public String bcryptHash(String message, int rounds){
        return bcryptHash(message, BCrypt.gensalt(rounds));
    }
    
    /**
     *
     * @param message
     * @return
     */
    public String bcryptHash(String message){
        return bcryptHash(message, 10);
    }
    
    /**
     *
     * @param message
     * @param hash
     * @return
     */
    public boolean bcryptCompare(String message, String hash){
        return BCrypt.checkpw(message, hash);
    }
    
    /**
     *
     * @param rounds
     * @return
     */
    public String bcryptGenSalt(int rounds){
        return BCrypt.gensalt(rounds);
    }
    
    /**
     *
     * @return
     */
    public String bcryptGenSalt(){
        return BCrypt.gensalt(10);
    }
    
    /**
     *
     * @param message
     * @param bytes
     * @return
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public String SHA3(String message, int bytes) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return new String(
                Base64.getEncoder().encode(
                        MessageDigest.getInstance("SHA3-"+Integer.toString(bytes)).digest(message.getBytes("UTF-8"))
                )
        );
    }
    
    /**
     *
     * @param size
     * @return
     * @throws NoSuchAlgorithmException
     */
    public String generateRandomSalt(int size) throws NoSuchAlgorithmException{
        byte[] salt = new byte[size];
        reseedDRGenerator();
        drgenerator.nextBytes(salt);
        return new String(Base64.getEncoder().encode(salt));
    }
    
    /**
     *
     * @param min
     * @param max
     * @return
     * @throws NoSuchAlgorithmException
     */
    public int generateRandomInt(int min, int max) throws NoSuchAlgorithmException{
        return SecureRandom.getInstance("SHA1PRNG").ints(min,max).findAny().getAsInt();
    }
    
    /**
     *
     * @param fis
     * @param fos
     * @param key
     * @param iv
     * @throws IOException
     * @throws DataLengthException
     * @throws IllegalStateException
     * @throws InvalidCipherTextException
     */
    public void AESEncrypt(InputStream fis, OutputStream fos, String key, String iv) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
        AESProcess(true, fis, fos, key, iv);
    }
    
    /**
     *
     * @param fis
     * @param fos
     * @param key
     * @param iv
     * @throws IOException
     * @throws DataLengthException
     * @throws IllegalStateException
     * @throws InvalidCipherTextException
     */
    public void AESDecrypt(InputStream fis, OutputStream fos, String key, String iv) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
        AESProcess(false, fis, fos, key, iv);
    }
    
    /**
     *
     * @param bar
     * @return
     */
    public String base64Encode(byte [] bar){
        return new String((Base64.getEncoder().encode(bar)));
    }
    
    /**
     *
     * @param str
     * @return
     */
    public byte [] base64Decode(String str){
        return Base64.getDecoder().decode(str);
    }
    
    /**
     *
     * @param size
     * @param passPhrase
     * @return
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public Map<String,String> generateRSAKey(int size, String passPhrase) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(base64Decode(passPhrase));
        keyGen.initialize(size, sr);
        KeyPair key = keyGen.generateKeyPair();
        Map<String, String> pair = new HashMap<String, String>();
        pair.put("private", base64Encode(key.getPrivate().getEncoded()));
        pair.put("public", base64Encode(key.getPublic().getEncoded()));
        return pair;
    }
    
    /**
     *
     * @param fis
     * @param fos
     * @param publicKey
     * @throws IOException
     * @throws InvalidCipherTextException
     */
    public void RSAEncrypt(InputStream fis, OutputStream fos, String publicKey) throws IOException, InvalidCipherTextException{
        RSAProcess(true, fis, fos, publicKey);
    }
    
    /**
     *
     * @param fis
     * @param fos
     * @param privateKey
     * @throws IOException
     * @throws InvalidCipherTextException
     */
    public void RSADecrypt(InputStream fis, OutputStream fos, String privateKey) throws IOException, InvalidCipherTextException{
        RSAProcess(false, fis, fos, privateKey);
    }
    
    /**
     *
     * @param password
     * @param salt
     * @param iterations
     * @param size
     * @return
     * @throws InvalidKeySpecException
     * @throws NoSuchAlgorithmException
     */
    public String generatePBKDF2Key(String password, String salt, int iterations, int size) throws InvalidKeySpecException, NoSuchAlgorithmException{
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec keyspec = new PBEKeySpec(password.toCharArray(), base64Decode(salt), iterations, size);
        Key key = factory.generateSecret(keyspec);
        return base64Encode(key.getEncoded());
    }
    
    /**
     * 
     * @throws NoSuchAlgorithmException 
     */
    private void reseedDRGenerator() throws NoSuchAlgorithmException{
        drgenerator.addSeedMaterial(SecureRandom.getInstance("SHA1PRNG").longs().findAny().getAsLong());
    }
    
    /**
     * 
     * @param encrypt
     * @param fis
     * @param fos
     * @param key
     * @param iv
     * @throws IOException
     * @throws DataLengthException
     * @throws IllegalStateException
     * @throws InvalidCipherTextException 
     */
    private void AESProcess(boolean encrypt, InputStream fis, OutputStream fos, String key, String iv) throws IOException, DataLengthException, IllegalStateException, InvalidCipherTextException{
        PaddedBufferedBlockCipher cipher = initAESCipher(encrypt, key, iv);
        byte[] buffer = new byte[blockSize];
        int bytesNum;
        byte[] cipherBlock =
            new byte[cipher.getOutputSize(buffer.length)];
        int cipherBytes;
        while((bytesNum = fis.read(buffer))!=-1)
       {
           cipherBytes = cipher.processBytes(buffer, 0, bytesNum, cipherBlock, 0);
           fos.write(cipherBlock, 0, cipherBytes);
       }
       cipherBytes = cipher.doFinal(cipherBlock,0);
       fos.write(cipherBlock,0,cipherBytes);
    }
    
    /**
     * 
     * @param encrypt
     * @param key
     * @param iv
     * @return 
     */
    private PaddedBufferedBlockCipher initAESCipher(boolean encrypt, String key, String iv){
        KeyParameter keyParam = new KeyParameter(base64Decode(key));
        CipherParameters params = new ParametersWithIV(keyParam, base64Decode(iv));
        PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
        cipher.reset();
        cipher.init(encrypt, params);
        return cipher;
    }
    
    
    /**
     * 
     * @param encrypt
     * @param fis
     * @param fos
     * @param key
     * @throws IOException
     * @throws InvalidCipherTextException 
     */
    private void RSAProcess(boolean encrypt, InputStream fis, OutputStream fos, String key) throws IOException, InvalidCipherTextException{
        AsymmetricBlockCipher cipher = initRSACipher(encrypt, key);
        byte[] buffer = new byte[cipher.getInputBlockSize()];
        int bytesNum;
        while((bytesNum = fis.read(buffer))!=-1){
            byte[] cipherBlock = cipher.processBlock(buffer, 0, bytesNum);
            fos.write(cipherBlock, 0, cipherBlock.length);
        }
    }
    
    /**
     * 
     * @param encrypt
     * @param key
     * @return
     * @throws IOException 
     */
    private AsymmetricBlockCipher initRSACipher(boolean encrypt, String key) throws IOException{
        AsymmetricKeyParameter keyParam;
        if (encrypt){
            keyParam = (AsymmetricKeyParameter) PublicKeyFactory.createKey(base64Decode(key));
        }
        else {
             keyParam = (AsymmetricKeyParameter) PrivateKeyFactory.createKey(base64Decode(key));
        }
        AsymmetricBlockCipher cipher = new RSAEngine();
        cipher = new PKCS1Encoding(cipher);
        cipher.init(encrypt, keyParam);
        return cipher;
    }
}
