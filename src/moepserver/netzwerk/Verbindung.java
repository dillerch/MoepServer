
package moepserver.netzwerk;

import java.util.logging.Level;
import moepserver.Spieler;
import moepserver.Karte;
import moepserver.MoepLogger;

/**
 * Beschreibt eine Verbindung zu einem Client
 * Jede Verbindung besitzt einen Reader und einen Writer, die die Lese- bzw. Schreibvorgänge in seperaten Threads ausführen
 * @author Christian Diller
 * @version BETA 1.1
 */
public class Verbindung extends Thread
{    
    protected boolean istAktiv;
    protected String loginName;
    
    protected VerbindungReaderThread reader;
    private VerbindungWriterThread writer;
    
    public Spieler spieler;
    
    private int farbeWuenschenInt = -1;
    
    private static final MoepLogger log = new MoepLogger();

    public Verbindung(VerbindungReaderThread _reader, VerbindungWriterThread _writer) {
        
        setName("VerbindungThread");
        reader = _reader;
        writer = _writer;
    }

    @Override
    public void run()
    {
        reader.verbindung = this;
        reader.start();
        writer.start();
        while(true)
        {
            synchronized(this)
            {
                try
                {
                    this.wait();
                }
                catch(InterruptedException ex)
                {
                    log.log(Level.WARNING, "Verbindung wurde beim Warten unterbrochen");
                }
            }
            
            while(!reader.istLeer())
            {
                String data = reader.pop();
                if(!packetBearbeiten(data))
                {
                    log.log(Level.WARNING, "Fehler im Protokoll (Falscher Client?) Data: " + data);
                }
            }
        }
    }

    protected boolean packetBearbeiten(String str)
    {
        Packet packet = Packet.erstellePacket(str);
        if(packet == null)
            return false;
        if(packet instanceof Packet01Login)
        {
            loginName = ((Packet01Login)packet).gibName();
            this.setName("VerbindungThread: " + loginName);     
            reader.setName("ReaderThread: " + loginName);
            writer.setName("WriterThread: " + loginName);
            istAktiv = true;
        }
        else
        {
            packet.eventAufrufen(this);
        }
        return true;
    }

    private boolean packetSenden(Packet packet)
    {
        try
        {
            writer.push(packet.gibData());
            synchronized(writer)
            {
                writer.notify();
            }
            return true;
        }
        catch(Exception ex){return false;}
    }

    /**
     * Sendet einen Kick an den Client und schließt anschließend die Verbindung
     * @param grund Der Grund, weshalb der Client gekickt wurde (Wird in der GUI des Benutzers angezeigt)
     * @return Erfolgreich abgeschlossen ja/nein
     */
    public boolean verbindungSchliessen(String grund)
    {
        packetSenden(new Packet02Kick(grund));
        try
        {
            reader.interrupt();
            writer.interrupt();
            istAktiv = false;
            return true;
        }
        catch(Exception ex){return false;}
    }

    /**
     * Teilt dem Client mit, ob er am Zug ist
     * @param wert Am Zug ja/nein
     * @param text Wenn nicht am Zug: Anzuzeigender Text, z.B. "Spieler xy ist gerade am Zug" oder "Warten, bis vier Spieler online sind..."
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeAmZug(boolean wert) {
        return packetSenden(new Packet03AmZug(wert));
    }

    /**
     * Teilt dem Client mit, dass sein letzter Zug ungültig war
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeUngueltigerZug(int art) {
        return packetSenden(new Packet04ZugLegal(false, art));
    }
    
    public boolean sendeGueltigerZug() {
        return packetSenden(new Packet04ZugLegal(true, -1));
    }

    /**
     * Sendet dem Client eine Handkarte (Nur nach Aufforderung durch diesen!)
     * @param karte Die zu sendende Karte
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeHandkarte(Karte karte) {
        return packetSenden(new Packet11Handkarte(karte));
    }

    /**
     * Sendet dem Client die jeweils oberste Ablagestapelkarte nach jedem Spielerzug (ohne Aufforderung durch den Client!)
     * @param karte Die zu sendende Karte
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeAblagestapelkarte(Karte karte) {
        return packetSenden(new Packet12Ablagestapelkarte(karte));
    }
    
    /**
     * Sendet dem Client, ob der MoepButton rechtzeitig gedrückt wurde
     * @param rechtzeitig Rechtzeitig gedrückt ja/nein
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeMoepButtonAntwort(boolean rechtzeitig)
    {
        return packetSenden(new Packet05MoepButton(rechtzeitig));
    }
    
    /**
     * Sendet eine Antwort auf eine Loginanfrage; wird von MoepServer ausgelöst
     * @param wert Login akzeptiert ja/nein
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeLoginAntwort(boolean wert)
    {
        return packetSenden(new Packet01Login(spieler.spielername, wert));
    }

    /**
     * Sendet eine Anfrage auf die gewünschte Farbe zum Client und wartet dann solange,
     * bis eine Antwort zurückkommt
     * @return Die vom Spieler gewünschte Farbe
     */
    public int farbeWuenschen()
    {
        sendeFarbeWuenschen();
        while(farbeWuenschenInt<0){try {
                Thread.currentThread().sleep(200);
            } catch (InterruptedException ex) {}
}
        int ausgabe = farbeWuenschenInt;
        farbeWuenschenInt = -1;
        return ausgabe;
    }
    
    /**
     * Sendet beliebigen Text an den Client, der mit dieser Verbindung verknüpgt ist
     * @param text Der zu sendende Text
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeText(String text)
    {
        return packetSenden(new Packet07Text(text));
    }
    
    /**
     * Sendet eine Nachticht an den Client, wenn sich ein Spieler ein- bzw ausloggt
     * @param art Login(art=0) oder Logout(art=1)?
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeSpielerServerAktion(String name, int art)
    {
        return packetSenden(new Packet08SpielerServerAktion(name, art));
    }
    
    /**
     * Sendet eine Anfrage an den Client, sich eine Farbe zu wünschen
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeFarbeWuenschen()
    {
        return packetSenden(new Packet06FarbeWuenschen(-1));
    }
    
    /**
     * Sendet den Befehl an den Client, das Spielfeld aufzuräumen
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeSpielende(boolean wert) {
        return packetSenden(new Packet09Spielende(wert));
    }
    
    /**
     * Kickt einen Client (Verwendet z.B. vom IP-Schutz)
     * @param grund Der im Client anzuzeigende Grund
     * @return Erfolgreich gesendet ja/nein
     */
    public boolean sendeKick(String grund)
    {
        return packetSenden(new Packet02Kick(grund));
    }
    
    //Event - Methoden
    protected void moepButtonEvent() {
        spieler.moepButtonEvent();
    }

    protected void farbeWuenschenEvent(int farbe) {
        farbeWuenschenInt = farbe;
    }

    protected void karteLegenEvent(Karte karte) {
        spieler.karteLegenEvent(karte);
    }

    protected void karteZiehenEvent() {
        spieler.karteZiehenEvent();
    }

    protected void verbindungVerlorenEvent() {
        spieler.verbindungVerlorenEvent();
    }
    //Ende Eventmethoden
    
    public String gibIP()
    {
        return reader.gibIP();
    }
    
    public int gibFarbeWuenschenInt() 
    {
        int ausgabe = farbeWuenschenInt;
        farbeWuenschenInt = -1;
        return ausgabe;
    }

    public boolean farbeWuenschenAntwortErhalten() {
        return farbeWuenschenInt >= 0;
    }
}
