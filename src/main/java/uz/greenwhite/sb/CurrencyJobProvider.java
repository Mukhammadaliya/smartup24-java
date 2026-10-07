package uz.greenwhite.sb;

import uz.greenwhite.biruni.old.http.TrustAllCerts;
import uz.greenwhite.biruni.old.job.JobProvider;
import uz.greenwhite.biruni.old.job.JobResult;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class CurrencyJobProvider implements JobProvider {

    private HttpURLConnection getHttpConnection(String path) throws Exception {
        var url = URI.create(path).toURL();
        String protocol = url.getProtocol().toLowerCase();
        HttpURLConnection conn = null;

        if ("http".equals(protocol)) {
            conn = (HttpURLConnection) url.openConnection();
        } else if ("https".equals(protocol)) {
            conn = (HttpsURLConnection) url.openConnection();
        }

        if (conn != null) {
            conn.setRequestMethod("GET");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("User-Agent", "smartup");
        }
        return conn;
    }

    @Override
    public JobResult run(String requestData){
        TrustAllCerts trust = new TrustAllCerts();
        try {
            trust.trust();
        } catch (Exception ignored){}

        HttpURLConnection conn = null;
        try {
            conn = getHttpConnection("https://cbu.uz/oz/arkhiv-kursov-valyut/json/");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                return new JobResult(HttpURLConnection.HTTP_OK, response.toString());
            } else {
                return new JobResult(400, "return bad request");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return new JobResult(500, "server error Currency job is not working");
    }
}

