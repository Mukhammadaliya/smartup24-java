package uz.greenwhite.sb;

import org.springframework.stereotype.Service;
import uz.greenwhite.biruni.old.service.runtimeservice.ErrorRuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeService;
import uz.greenwhite.biruni.old.service.runtimeservice.SuccessRuntimeResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import scala.collection.immutable.Map;

/**
 * Reverse geocoding against the Yandex geocoder: the URL, the api key and the coordinates are built
 * in PL/SQL (Sbe_Util.Yandex_Geocode_Url) and this service only fetches it, so that the address rules
 * stay in one place. The body is handed back untouched and parsed by Sbe_Util.Yandex_Geocode_Address.
 * <p>
 * The two error codes worth telling apart are reported as such: 403 is a key that is dead, wrong or
 * out of its daily quota, and 429 is too many requests. Both are fixed by a person -- a new key in
 * the moderator settings, or a wider tariff -- and neither is helped by the caller retrying, so they
 * must not reach the moderator as a generic "service unavailable".
 */
@Service
public class YandexGeocoderRuntimeService implements RuntimeService {

  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 15000;

  private HttpURLConnection open(URL url) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");
    conn.setDoInput(true);
    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
    conn.setReadTimeout(READ_TIMEOUT_MS);

    return conn;
  }

  private String read(InputStream stream) throws Exception {
    if (stream == null) {
      return "";
    }

    StringBuilder body = new StringBuilder();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        body.append(line);
      }
    }

    return body.toString();
  }

  @Override
  public RuntimeResult run(Map<String, Object> detail, String data) {
    HttpURLConnection conn = null;

    String url = detail.getOrElse("url", () -> "");

    if (url.isEmpty()) {
      return new ErrorRuntimeResult("", "geocoder url is required");
    }

    try {
      conn = open(URI.create(url).toURL());

      int responseCode = conn.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_OK) {
        return new SuccessRuntimeResult("", read(conn.getInputStream()));
      }

      String body = read(conn.getErrorStream());

      if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
        return new ErrorRuntimeResult("", "yandex geocoder rejected the api key: it is invalid, "
            + "expired, belongs to another product or its daily quota is spent " + body);
      }

      if (responseCode == 429) {
        return new ErrorRuntimeResult("", "yandex geocoder is being called too often " + body);
      }

      return new ErrorRuntimeResult("", "yandex geocoder answered " + responseCode + " " + body);

    } catch (Exception e) {
      return new ErrorRuntimeResult("", "connection fail with yandex geocoder " + e.getMessage());
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }
}
