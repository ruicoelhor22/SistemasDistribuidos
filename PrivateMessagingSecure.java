import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PrivateMessagingSecure extends UnicastRemoteObject implements PrivateMessagingSecureInterface {

    private String nomeUtilizador;
    private boolean receberMP;
    private HashMap<String, PublicKey> chavesPublicas;

    //Construtor da classe PrivateMessagingSecure
    public PrivateMessagingSecure(String nomeUtilizador, boolean receberMP, HashMap<String, PublicKey> chavesPublicas) throws RemoteException {
        super();
        this.nomeUtilizador = nomeUtilizador;
        this.receberMP = receberMP;
        this.chavesPublicas = chavesPublicas;
    }

    public String sendMessage(String enviou, String msg) throws RemoteException {
        if(this.receberMP == true){
            System.out.println("Mensagem privada de " + enviou + ": " + msg);
            return this.nomeUtilizador;
        }else{
            return null;
        }
    }

    public String sendMessageSecure(String enviou, String msg, String assinatura){
        if(this.receberMP == true){
            try{
                //Converter sumario de Base 64 para bytes
                byte[] decodedBytes = Base64.getDecoder().decode(assinatura);
                Cipher cipher = Cipher.getInstance("RSA");

                //Obter a chave publica do cliente atraves do seu nome
                PublicKey pk = chavesPublicas.get(enviou);

                //Decifrar o sumario original com a chave publica do cliente
                cipher.init(Cipher.DECRYPT_MODE, pk);
                byte[] decipheredDigest = cipher.doFinal(decodedBytes);

                //Criar um novo sumario da mensagem recebida
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(msg.getBytes());
                byte[] digest = md.digest();

                //Comparar o sumario original com o sumario gerado e verificar autenticidade
                if(Arrays.equals(decipheredDigest, digest)){
                    System.out.println("Mensagem privada de " + enviou + ": " + msg);
                    System.out.println("Esta mensagem e segura. ");
                }else{
                    System.out.println(enviou + " A mensagem que recebeu pode ter sido alterada durante o envio!");
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
            return this.nomeUtilizador;
        }else{
            return null;
        }
    }

}
