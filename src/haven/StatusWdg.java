package haven;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatusWdg extends Widget {
    private static final ThreadGroup tg = new ThreadGroup("StatusUpdaterThreadGroup");
    private static final Tex hearthlingsplayingdef = Text.render(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Players: %s"), "?"), Color.WHITE).tex();
    private static final Tex pingtimedef = Text.render(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Ping: %s ms"), "?"), Color.WHITE).tex();
    private Tex players = hearthlingsplayingdef;
    private Tex pingtime = pingtimedef;
    private long lastPingUpdate = System.currentTimeMillis();
    private final static Pattern pattern = Config.iswindows ?
            Pattern.compile(".+?=32 .+?=(\\d+).*? TTL=.+") :    // Reply from 87.245.198.59: bytes=32 time=2ms TTL=53
            Pattern.compile(".+?time=(\\d+\\.?\\d*) ms");       // 64 bytes from ansgar.seatribe.se (213.239.201.139): icmp_seq=1 ttl=47 time=71.4 ms


    public StatusWdg() {
        tg.interrupt();
        startUpdater();
    }

    private void updatepingtime() {
        String ping = "?";

        java.util.List<String> command = new ArrayList<>();
        command.add("ping");
        command.add(Config.iswindows ? "-n" : "-c");
        command.add("1");
        command.add("game.havenandhearth.com");

        BufferedReader standardOutput = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            standardOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String output = "";
            String line;
            while ((line = standardOutput.readLine()) != null) {
                output += line;
            }

            Matcher matcher = pattern.matcher(output);
            while (matcher.find()) {
                ping = matcher.group(1);
            }
        } catch (IOException ex) {
            // NOP
        } finally {
            if (standardOutput != null)
                try {
                    standardOutput.close();
                } catch (IOException e) { // ignored
                }
        }

        if (ping.isEmpty())
            ping = "?";

        synchronized (this) {
            pingtime = Text.render(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Ping: %s ms"), ping), Color.WHITE).tex();
        }
    }

    private void startUpdater() {
        Thread statusupdaterthread = new Thread(tg, () -> {
            updatepingtime();
            while (true) {
                URL url_;
                BufferedReader br = null;
                HttpURLConnection conn = null;

                try {
                    url_ = new URL("http://www.havenandhearth.com/mt/srv-mon");
                    conn = (HttpURLConnection)url_.openConnection();
                    InputStream is = conn.getInputStream();
                    br = new BufferedReader(new InputStreamReader(is));

                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("users ")) {
                            String p = line.substring("users ".length());
                            players = Text.render(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Players: %s"), p), Color.WHITE).tex();
                        }

                        // Update ping at least every 5 seconds.
                        // This of course might take more than 5 seconds in case there were no new logins/logouts
                        // but it's not critical.
                        long now = System.currentTimeMillis();
                        if (now - lastPingUpdate > 5000) {
                            lastPingUpdate = now;
                            updatepingtime();
                        }

                        if (Thread.interrupted())
                            return;
                    }
                } catch (SocketException se) {
                    // don't print socket exceptions when network is unreachable to prevent console spamming on bad connections
                    if (!se.getMessage().equals("Network is unreachable"))
                        se.printStackTrace();
                } catch (MalformedURLException mue) {
                    mue.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } finally {
                    try {
                        if (br != null)
                            br.close();
                    } catch (IOException ioe) {
                    }
                    if (conn != null)
                        conn.disconnect();
                }

                if (Thread.interrupted())
                    return;

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "StatusUpdater");
        statusupdaterthread.start();
    }

    @Override
    public void draw(GOut g) {
        g.image(players, Coord.z);
        g.image(pingtime, new Coord(0, players.sz().y));

        int w = players.sz().x;
        if (pingtime.sz().x > w)
            w = pingtime.sz().x;
        this.sz = new Coord(w,  players.sz().y + pingtime.sz().y);
    }

    @Override
    public void reqdestroy() {
        tg.interrupt();
        super.reqdestroy();
    }
}