/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugins.Indynet;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author ktogias
 */
public class User {
    private final static int HASH_SIZE = 256; 
    
    private IndynetCrypto crypto;
    private String PBKDF2Key;
    private RSAKeyPair RSAKeyPair;

    public User(String PBKDF2Key, RSAKeyPair RSAKeyPair, IndynetCrypto crypto){
        this.crypto = crypto;
        this.PBKDF2Key = PBKDF2Key;
        this.RSAKeyPair = RSAKeyPair;
    }

    public String getRSAPublicKey(){
        return this.RSAKeyPair.getPublicKey();
    }
    
    public String getHash() throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return crypto.SHA3(PBKDF2Key+RSAKeyPair.getPublicKey()+RSAKeyPair.getPrivateKey(), HASH_SIZE);
    }
    
}
