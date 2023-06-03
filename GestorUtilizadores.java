import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.Vector;

//Classe que gere os diferentes clientes conectados, do lado do servidor
public class GestorUtilizadores extends Thread{
    //ArrayList listaUtilizadores que guarda todos os clientes (objetos do tipo GestorUtilizadores)
    static ArrayList<GestorUtilizadores> listaUtilizadores = new ArrayList<>();

    //Objeto da classe ListaAtivos
    ListaAtivos listaAtivos;

    //ArrayList listaMensagens que guarda as mensagens enviadas
    static ArrayList<String> listaMensagens = new ArrayList<>();
    Socket socketLigacao;
    BufferedReader entrada;
    PrintWriter saida;
    String nomeUtilizador;
    //Variavel booleana para guardar se o utilizador quer ou não receber mensagens privadas
    Boolean receberMP;
    int out;
    String publicKey;

    //Construtor da classe GestorUtilizadores (executado sempre que um novo cliente se conecta)
    public GestorUtilizadores(Socket socketLigacao, ListaAtivos listaAtivos, int porta, int timeout){
        this.socketLigacao = socketLigacao;
        this.out = timeout;
        try{
            //BufferedReader "entrada" que armazena aquilo que é enviado do PrintWriter "saida" do cliente
            this.entrada = new BufferedReader(new InputStreamReader(socketLigacao.getInputStream()));

            //PrintWriter "saida" que armazena as mensagens no servidor, a enviar para o cliente
            this.saida = new PrintWriter(socketLigacao.getOutputStream());

            //Inicialização da listaAtivos
            this.listaAtivos = listaAtivos;

        } catch (IOException e){
            System.out.println("5Erro ao comunicar com o servidor: " + e);
            System.exit(1);
        }
    }


    public void sessionUpdate(GestorUtilizadores user) {
        user.saida.println("SESSION_UPDATE: Utilizadores Online: ");
        for (GestorUtilizadores u : listaUtilizadores) {
            user.saida.println("SESSION_UPDATE: /*cliente*/" + u.nomeUtilizador + "-" + u.socketLigacao.getRemoteSocketAddress().toString() + "-" + String.valueOf(u.receberMP) + "-" + u.publicKey);
        }

        String mensagens = "";
        for (String s : listaMensagens) {
            mensagens = mensagens.concat(s + "/paragrafo/");
        }
        user.saida.println("SESSION_UPDATE: Ultimas Mensagens:  ");
        user.saida.println("SESSION_UPDATE: " + mensagens);
        user.saida.flush();
    }

    public void atualizarPresenca() {
        listaAtivos.getPresences(socketLigacao.getRemoteSocketAddress().toString(), this.nomeUtilizador, this.out);
    }

    public void sessionTimeout() {
        Vector<String> ativos = listaAtivos.getIPList();
        ArrayList<GestorUtilizadores> inativos = new ArrayList<>();
        for (GestorUtilizadores u : listaUtilizadores) {
            if (!ativos.contains(u.nomeUtilizador)) {
                inativos.add(u);
                System.out.println(u.nomeUtilizador + " esta inativo");
            }
        }
        for (GestorUtilizadores u : inativos) {
            System.out.println("Este cliente vai se desconectar");
            u.saida.println("SESSION_TIMEOUT: A sua sessão terminou!");
            u.saida.flush();
            try {
                u.entrada.close();
                u.saida.flush();
                u.saida.close();
                u.socketLigacao.close();
                listaUtilizadores.remove(u);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    //Envia as mensagens recebidas para todos os utilizadores existentes na listaUtilizadores
    public void enviarMensagem(String msg){
        for(GestorUtilizadores utilizador: listaUtilizadores){
            if(!utilizador.nomeUtilizador.equals(this.nomeUtilizador)){
                utilizador.saida.println(msg);
                utilizador.saida.flush();
            }
        }
    }

    //Metodo que lê o cabeçalho incluido em cada mensagem e faz o seu tratamento adequadamente
    public void lerCabecalho(String msg){

        System.out.println(msg);
        String[] split = msg.split(":");
        String tipoMensagem = split[0];
        int pos = msg.indexOf(':');
        String mensagem = msg.substring(pos + 2);

        switch(tipoMensagem){
            case "AGENT_POST":
                if(listaMensagens.size() >= 10) {
                    listaMensagens.remove(0);
                }
                atualizarPresenca();
                sessionTimeout();
                listaMensagens.add(mensagem);
                enviarMensagem(mensagem);
                for (GestorUtilizadores u : listaUtilizadores) {
                    sessionUpdate(u);
                }
                break;

            case "SESSION_UPDATE_REQUEST":
                //Se mensagem for SESSION_UPDATE_REQUEST (com corpo), divide-a, nos ":" em nomeUtilizador, receberMP e publicKey
                if (mensagem.length() > 1) {
                    String[] s = mensagem.split(":");
                    String nomeUtilizador = s[0];
                    String receberMP = s[1];
                    this.publicKey = s[2];
                    this.nomeUtilizador = nomeUtilizador;
                    if(receberMP.equalsIgnoreCase("true")){
                        this.receberMP = true;
                    }else if (receberMP.equalsIgnoreCase("false")){
                        this.receberMP = false;
                    }
                    atualizarPresenca();
                    sessionTimeout();
                    System.out.println("Cliente " + nomeUtilizador + " conectou-se");
                    if (!listaUtilizadores.contains(this)) {
                        listaUtilizadores.add(this);
                    }
                    for (GestorUtilizadores u : listaUtilizadores) {
                        sessionUpdate(u);
                    }
                } else {
                    atualizarPresenca();
                    sessionTimeout();
                    sessionUpdate(this);
                }
                break;


            case "SESSION_TIMEOUT":
                try {

                    System.out.println("Utilizador " + nomeUtilizador + " desconectou-se.");
                    listaUtilizadores.remove(this);
                    this.saida.println("SESSION_TIMEOUT: A sua sessão vai terminar");
                    this.saida.println("SESSION_TIMEOUT: Sessão terminada!");
                    this.saida.flush();
                    this.entrada.close();
                    this.saida.flush();
                    this.saida.close();
                    this.socketLigacao.close();

                } catch (IOException e) {
                    System.out.println("6Erro ao comunicar com o servidor: " + e);
                }
        }
    }


    //Servidor aguarda entrada de mensagens, para as enviar para os utilizadores (através do metodo lerCabecalho())
    public void run(){
        new java.util.Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sessionTimeout();
            }
        }, 1000 * 5, 1000 * 5);


        while(socketLigacao.isConnected()){
            try {
                String msg = entrada.readLine();
                lerCabecalho(msg);
            } catch (IOException e) {
                //System.out.println("7Erro ao comunicar com o servidor: " + e);
            }
        }
    }

}