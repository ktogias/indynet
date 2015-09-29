/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 *
 * @author ktogias
 */
public class RSAKeyPair {
    private String publicKey;
    private String privateKey;
    private final IndynetCrypto crypto;
    
    public RSAKeyPair(KeyPair key, IndynetCrypto crypto){
        this.crypto = crypto;
        this.privateKey = crypto.base64Encode(key.getPrivate().getEncoded());
        this.publicKey = crypto.base64Encode(key.getPublic().getEncoded());
    }

    public RSAKeyPair(int size, String passPhrase, IndynetCrypto crypto) throws NoSuchAlgorithmException{
        this.crypto = crypto;
        generate(size, passPhrase);
    }

    public String getPrivateKey(){
        return privateKey;
    }

    public String getPublicKey(){
        return publicKey;
    }

    private void generate(int size, String passPhrase) throws NoSuchAlgorithmException{
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(crypto.base64Decode(passPhrase));
        passPhrase = null;
        keyGen.initialize(size, sr);
        KeyPair key = keyGen.generateKeyPair();
        this.privateKey = crypto.base64Encode(key.getPrivate().getEncoded());
        this.publicKey = crypto.base64Encode(key.getPublic().getEncoded());
    }
}
