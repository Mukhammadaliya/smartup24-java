package uz.greenwhite.sb.nurfin;

import org.springframework.stereotype.Service;
import uz.greenwhite.biruni.old.service.runtimeservice.ErrorRuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeService;
import uz.greenwhite.biruni.old.service.runtimeservice.SuccessRuntimeResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import scala.collection.immutable.Map;

/**
 * Creates a Nurfin webview session. The address and the body are built in Sbe_Nurfin; this service
 * signs the body and performs the call.
 * <p>
 * The signature is computed here, over the very bytes written to the connection, so what is signed
 * and what is sent cannot differ. The body must never be parsed and re-serialized in between.
 */
@Service
public class NurfinLaunchRuntimeService implements RuntimeService {
  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 30000;

  // "sha256=" + lowercase hex of HMAC-SHA256(secret, timestamp + "." + body bytes)
  private String sign(String secret, String timestamp, byte[] payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
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
    String url = detail.getOrElse("url", () -> "");
    String body = detail.getOrElse("body", () -> "");
    String secret = detail.getOrElse("request_secret", () -> "");

    if (url.isEmpty() || body.isEmpty() || secret.isEmpty()) {
      return new ErrorRuntimeResult("", "nurfin url, body and request secret are required");
    }

    HttpURLConnection conn = null;

    try {
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      String timestamp = String.valueOf(Instant.now().getEpochSecond());

      conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Accept", "application/json");
      // Nurfin sits behind Cloudflare, which bans the default "Java/<version>" agent (error 1010)
      conn.setRequestProperty("User-Agent", "Smartup24/1.0");
      conn.setRequestProperty("Authorization", "Bearer " + detail.getOrElse("token", () -> ""));
      conn.setRequestProperty("Idempotency-Key", detail.getOrElse("idempotency_key", () -> ""));
      conn.setRequestProperty("X-Timestamp", timestamp);
      conn.setRequestProperty("X-Signature", sign(secret, timestamp, payload));

      try (OutputStream out = conn.getOutputStream()) {
        out.write(payload);
      }

      int code = conn.getResponseCode();

      if (code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_OK) {
        return new SuccessRuntimeResult("", read(conn.getInputStream()));
      }

      String error = read(conn.getErrorStream());

      if (code == 401 || code == 403) {
        return new ErrorRuntimeResult("", "nurfin rejected the credentials of this supplier: "
            + "check the token, the request secret and the server clock " + error);
      }
      if (code == 409) {
        return new ErrorRuntimeResult("", "nurfin has this order bound to other data " + error);
      }
      if (code == 422) {
        return new ErrorRuntimeResult("", "nurfin refused the order data " + error);
      }
      if (code == 429) {
        return new ErrorRuntimeResult("", "nurfin is being called too often " + error);
      }
      if (code == 503) {
        return new ErrorRuntimeResult("", "nurfin channel is unavailable or not configured " + error);
      }

      return new ErrorRuntimeResult("", "nurfin answered " + code + " " + error);

    } catch (Exception e) {
      return new ErrorRuntimeResult("", "connection fail with nurfin " + e.getMessage());
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }
}
