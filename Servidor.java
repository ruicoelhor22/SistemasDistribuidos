import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Servidor {
    static final int DEFAULT_PORT=6000;
    static final int DEFAULT_TIMEOUT=120;

    public static void main(String[] args) {

        int porta = 0;
        int timeout = 0;
        ServerSocket servidor = null;
        ListaAtivos listaUtilizadores = new ListaAtivos();
        Scanner scanner = new Scanner(System.in);

        //Escolha dos parametros
        System.out.println("Prefere:");
        System.out.println("1-Utilizar porta e timeout Default");
        System.out.println("2-Introduzir porta e timeout");
        int escolha = scanner.nextInt();

        if (escolha == 1) {
            porta = DEFAULT_PORT;
            timeout = DEFAULT_TIMEOUT;
        } else if (escolha == 2) {
            System.out.println("Introduzir porta: ");
            porta = scanner.nextInt();
            System.out.println("Introduzir tempo timeout: ");
            timeout = scanner.nextInt();
        }

        try {
            servidor = new ServerSocket(porta);
            System.out.println("Servidor iniciado com sucesso no endereço " + servidor.getInetAddress().getLocalHost() + ":" + porta);
        } catch (IOException e) {
            System.out.println("3Erro na execucao do servidor: " + e);
            System.exit(1);
        }

        while(true){
            try {
                //Socket ligacao aguarda que novos clientes se liguem
                Socket ligacao = servidor.accept();

                //Criação de thread que gere um novo cliente quando este se liga
                GestorUtilizadores mThread = new GestorUtilizadores(ligacao, listaUtilizadores, porta, timeout);
                mThread.start();

            } catch (IOException e) {
                System.out.println("4Erro na execução do servidor: " + e);
            }
        }

    }
}
