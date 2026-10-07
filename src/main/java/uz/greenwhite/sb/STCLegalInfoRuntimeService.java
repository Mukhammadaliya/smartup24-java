package uz.greenwhite.sb;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.greenwhite.biruni.old.service.runtimeservice.ErrorRuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeService;
import uz.greenwhite.biruni.old.service.runtimeservice.SuccessRuntimeResult;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import scala.collection.immutable.Map;

@Service
public class STCLegalInfoRuntimeService implements RuntimeService {

  private String getAuth(String auth) {
    try {
      return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      return "";
    }
  }

  private HttpURLConnection getHttpConnection(URL url, String login, String password) throws Exception {
    String protocol = url.getProtocol().toLowerCase();
    HttpURLConnection conn;

    if ("http".equals(protocol)) {
      conn = (HttpURLConnection) url.openConnection();
    } else if ("https".equals(protocol)) {
      conn = (HttpsURLConnection) url.openConnection();
    } else {
      throw new IllegalArgumentException("Unsupported protocol: " + protocol);
    }

    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", getAuth(login + ":" + password));
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setConnectTimeout(5000);

    return conn;
  }

  @Override
  public RuntimeResult run(Map<String, Object> detail, String data) {
    HttpURLConnection conn = null;

    String url = detail.getOrElse("url", () -> "");
    String login = detail.getOrElse("login", () -> "");
    String password = detail.getOrElse("password", () -> "");

    if (url.isEmpty()) {
      return new ErrorRuntimeResult("", "legal person info url is required");
    }

    try {
      conn = getHttpConnection(URI.create(url).toURL(), login, password);

      int responseCode = conn.getResponseCode();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(
              responseCode == HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));

      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }

      reader.close();

      if (responseCode == HttpURLConnection.HTTP_OK) {
        return new SuccessRuntimeResult("",response.toString());
      } else {
        return new ErrorRuntimeResult("",response.toString());
      }

    } catch (Exception e) {
      return new ErrorRuntimeResult("","connection fail with legal person info server " + e.getMessage());
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }
}