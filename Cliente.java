import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class Cliente {

    static final int DEFAULT_PORT=6000;
    static final String DEFAULT_HOST="localHost";

    private static String SERVICE_NAME = "/PrivateMessaging";
    private static HashMap<String, String> clientes = new HashMap<>();
    private static BufferedReader entrada;
    private static PrintWriter saida;
    private static String nomeUtilizador;
    static Socket socketCliente;

    private static PublicKey publicKey;
    private static PrivateKey privateKey;
    private static HashMap<String, PublicKey> chavesPublicas = new HashMap<>();

    //Método main
    public static void main(String[] args) {

        int porta = 0;
        String hostIP = null;
        boolean receberMP = true;
        Scanner scanner = new Scanner(System.in);
        Scanner scanner2 = new Scanner(System.in);
        System.out.println("Prefere:");
        System.out.println("1-Utilizar parametros default");
        System.out.println("2-Introduzir parametros");
        int escolha = scanner.nextInt();

        if (escolha == 1) {
            porta = DEFAULT_PORT;
            hostIP = DEFAULT_HOST;
        } else if (escolha == 2) {
            System.out.println("Introduzir porta: ");
            porta = scanner.nextInt();
            System.out.println("Introduzir IP: ");
            hostIP = scanner2.nextLine();

        }

        System.out.println("Pretende receber mensagens privadas? (sim/nao)");
        if(scanner.nextLine().equalsIgnoreCase ("sim")){
            receberMP = true;
        }else if (scanner.nextLine().equalsIgnoreCase("nao")){
            receberMP = false;
        }

        try {

            socketCliente = new Socket(hostIP, porta);
            System.out.println("Classe Cliente foi iniciada!");

            //BufferedReader "entrada" que armazena aquilo que é enviado do PrintWriter "saida" do servidor
            entrada = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));

            //PrintWriter "saida" que armazena as mensagens do cliente, a enviar para o servidor
            saida = new PrintWriter(socketCliente.getOutputStream(), true);

            Scanner input = new Scanner(System.in);
            System.out.print("Introduza o nome de Utilizador: ");
            nomeUtilizador = input.nextLine();


            //Criar registo e usar o username para o guardar
            //Criamos interface remota para o cliente
            //Acedemos ao registo e guardamos a interface associada ao service name
            try {
                LocateRegistry.createRegistry(1099);
                PrivateMessagingSecureInterface ref = new PrivateMessagingSecure(nomeUtilizador, receberMP, chavesPublicas);
                LocateRegistry.getRegistry("127.0.0.1", 1099).rebind(SERVICE_NAME, ref);

            } catch (RemoteException ex) {
                PrivateMessagingSecureInterface ref = new PrivateMessagingSecure(nomeUtilizador, receberMP, chavesPublicas);
                LocateRegistry.getRegistry("127.0.0.1", 1099).rebind(SERVICE_NAME, ref);

            } catch (Exception ex) {
                System.out.println("Erro na criação do registo");
            }

            //Gerar chaves publicas e chaves privadas
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(1024);
            KeyPair pair = keyPairGen.generateKeyPair();
            publicKey = pair.getPublic();
            privateKey = pair.getPrivate();

            //Converter a chave publica para Base64 de modo a ser possivel enviar para o servidor
            String encodedString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            saida.println("SESSION_UPDATE_REQUEST: " + nomeUtilizador + ":" + String.valueOf(receberMP) + ":" + encodedString);


            //Criação de Thread que enquanto permite a escrita, consegue receber mensagens do servidor
            new Thread(new Runnable() {
                public void run() {
                    while (socketCliente.isConnected()) {
                        try {

                            String msg = entrada.readLine();
                            String[] split = msg.split(":");
                            String cabecalho = split[0];
                            int pos = msg.indexOf(':');
                            String mensagem = msg.substring(pos + 2);

                            if (cabecalho.equalsIgnoreCase("SESSION_UPDATE")) {

                                String novamsg = mensagem.replace("/paragrafo/", "\n");

                                if(mensagem.startsWith("/*cliente*/")){

                                    String info = mensagem.substring(11);
                                    String[] split_info = info.split("-"); /*USERNAME-IP-RECEBERPM-PUBLIC_KEY */

                                    //Se o cliente não constar na lista de clientes, adicionamos para ter acesso ao IP
                                    if(!clientes.keySet().contains(split_info[0])){
                                        int p = split_info[1].indexOf(":");
                                        clientes.put(split_info[0], split_info[1].substring(1, p));
                                    }

                                    //Se o cliente nao constar na lista das chaves publicas, adicionamos
                                    if(!chavesPublicas.keySet().contains(split_info[0])){
                                        //Converter de Base64 para PublicKey
                                        byte[] decodedBytes = Base64.getDecoder().decode(split_info[3]);
                                        KeyFactory factory = KeyFactory.getInstance("RSA","SunRsaSign");
                                        PublicKey public_key = (PublicKey) factory.generatePublic(new X509EncodedKeySpec(decodedBytes));
                                        chavesPublicas.put(split_info[0], public_key);

                                    }
                                    System.out.println(split_info[0] + " - " + split_info[1] + " - "+ split_info[2]);
                                }else {
                                    System.out.println(novamsg);
                                }

                            } else if (cabecalho.equalsIgnoreCase("SESSION_TIMEOUT")) {

                                System.out.println(mensagem);
                                entrada.close();
                                saida.flush();
                                saida.close();
                                socketCliente.close();
                            }
                        } catch (IOException e) {
                            System.out.println("Erro ao comunicar com o servidor: " + e);
                            System.exit(-1);
                        }

                        catch (InvalidKeySpecException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (NoSuchProviderException e) {
                            e.printStackTrace();
                        }


                    }
                }
            }).start();

            //Método para enviar SESSION_UPDATE_REQUEST periodicos (definidos para 130 segundos, tem de ser um tempo superior ao timeout default)
            new Timer().schedule(new TimerTask(){
                @Override
                public void run() {
                    if(socketCliente.isConnected()){
                        saida.println("SESSION_UPDATE_REQUEST: ");
                        saida.flush();
                    }
                }
            },1000*130, 1000*130);


            //Ciclo que aguarda escrita de mensagem
            while (socketCliente.isConnected()) {

                System.out.print("");
                String msg = input.nextLine();
                String[] split = msg.split(" ");

                //Metodo para enviar mensagens privadas (/MP) ou mensagens privadas seguras (/MPSeguras)
                if(split[0].equalsIgnoreCase("/MP")){

                    String ip = clientes.get(split[1]);
                    System.out.println("IP: " + ip);
                    msg = msg.substring(msg.indexOf(" ") + 1);
                    String corpo = msg.substring(msg.indexOf(" ") + 1); //Obtem o corpo da mensagem (retira-se o IP)
                    System.out.println("Mensagem = " + corpo);
                    PrivateMessagingSecureInterface ref;

                    try {

                        ref = (PrivateMessagingSecureInterface) LocateRegistry.getRegistry(ip).lookup(SERVICE_NAME);

                        //Executa o metodo sendMessage no cliente correspondente e envia lhe como parametros o username de quem enviou e a mensagem
                        //Retorna o username do cliente que recebeu a mensagem
                        String recebeu = ref.sendMessage(nomeUtilizador, corpo);

                        if(recebeu == null){ //Se retornar username null signigfica que nao quer receber mensagens
                            System.out.println("Este utilizador não quer receber mensagens privadas");

                        } else {
                            System.out.println("Mensagem enviada a " + recebeu);
                        }

                    } catch (NotBoundException e) {
                        e.printStackTrace();
                    }

                }else if (msg.equals("off.") ) { //terminar a sessão antes do timeout com a palavra "off."
                    saida.println("SESSION_TIMEOUT: off.");
                    saida.flush();
                }

                else if(split[0].equalsIgnoreCase("/MPSegura")){
                    String ip = clientes.get(split[1]); //Buscar o ip do utilizador para quem pretendemos enviar
                    msg = msg.substring(msg.indexOf(" ") + 1);
                    String corpo = msg.substring(msg.indexOf(" ") + 1); //Obter corpo da mensagem (retirando o IP)

                    //CRIAR O SUMARIO
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    md.update(corpo.getBytes()); //passamos os bytes do corpo da mensagem para o messagedigest
                    byte[] digest = md.digest(); //Obtemos o sumario

                    //CRIAR O ALGORIMTO QUE VAI ENCRIPTAR E .ASSINAR O SUMARIO.
                    Cipher cipher = Cipher.getInstance("RSA");
                    cipher.init(Cipher.ENCRYPT_MODE, privateKey); //Encriptamos com a privateKey do cliente
                    cipher.update(digest); //Adicionamos a assinatura ao sumario

                    //
                    byte[] cipherText = cipher.doFinal();

                    //Converter os bytes para String (Base64) para poder enviar
                    String msgBase = Base64.getEncoder().encodeToString(cipherText);

                    //Obter referencia do cliente destino
                    PrivateMessagingSecureInterface ref = (PrivateMessagingSecureInterface) LocateRegistry.getRegistry(ip).lookup(SERVICE_NAME);

                    //Enviar a mensagem
                    String recebeu = ref.sendMessageSecure(nomeUtilizador, corpo, msgBase);
                    if(recebeu == null){
                        System.out.println("Este utilizador não quer receber mensagens");
                    }else{
                        System.out.println("Mensagem segura enviada a " + recebeu);
                    }
                }
                else {
                    saida.println("AGENT_POST: " + nomeUtilizador + ": " + msg);

                }
            }




        } catch (IOException e) {
            System.out.println("2Erro ao comunicar com o servidor: " + e);
        }
        catch (NoSuchAlgorithmException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (NoSuchPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NotBoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (BadPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}

