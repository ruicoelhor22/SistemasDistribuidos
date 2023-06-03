
import java.util.*;

public class ListaAtivos {
    private static Hashtable<String, IPInfo> listaUtilizadores = new Hashtable<String, IPInfo>();
    int out2;

    public Vector<String> getPresences(String IPAddress, String username, int out) {

        long actualTime = new Date().getTime();
        out2 = out;

        synchronized(this) {
            if (listaUtilizadores.containsKey(IPAddress)) {
                IPInfo newIp = listaUtilizadores.get(IPAddress);
                newIp.setLastSeen(actualTime);
            }
            else {
                IPInfo newIP = new IPInfo(IPAddress, actualTime, username);
                listaUtilizadores.put(IPAddress,newIP);
            }
        }
        return getIPList();
    }


    public Vector<String> getIPList(){
        Vector<String> result = new Vector<String>();
        for (Enumeration<IPInfo> e = listaUtilizadores.elements(); e.hasMoreElements(); ) {
            IPInfo element = e.nextElement();
            if (!element.timeOutPassed(this.out2*1000)) {
                result.add(element.getNomeUtilizador());
            }
        }
        return result;
    }
}

class IPInfo {

    private String ip;
    private long lastSeen;
    private String nomeUtilizador;

    public IPInfo(String ip, long lastSeen, String nomeUtilizador) {
        this.ip = ip;
        this.lastSeen = lastSeen;
        this.nomeUtilizador = nomeUtilizador;
    }

    public String getIP () {
        return this.ip;
    }

    public String getNomeUtilizador(){
        return this.nomeUtilizador;
    }

    public void setLastSeen(long time){
        this.lastSeen = time;
    }


    public boolean timeOutPassed(int timeout){
        boolean result = false;
        long timePassedSinceLastSeen = new Date().getTime() - this.lastSeen;
        if (timePassedSinceLastSeen >= timeout)
            result = true;
        return result;
    }
}