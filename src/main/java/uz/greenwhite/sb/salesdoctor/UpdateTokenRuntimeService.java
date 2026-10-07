package uz.greenwhite.sb.salesdoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import scala.collection.immutable.Map;
import uz.greenwhite.biruni.old.service.runtimeservice.ErrorRuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeService;
import uz.greenwhite.biruni.old.service.runtimeservice.SuccessRuntimeResult;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class UpdateTokenRuntimeService implements RuntimeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------------------------------------

    private HttpURLConnection openConnection(URL url) throws Exception {
        String protocol = url.getProtocol().toLowerCase();

        HttpURLConnection conn = switch (protocol) {
            case "https" -> (HttpsURLConnection) url.openConnection();
            case "http"  -> (HttpURLConnection)  url.openConnection();
            default      -> throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        };

        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        return conn;
    }

    // ------------------------------------------------------------------------------------------------

    private String readResponse(HttpURLConnection conn) throws Exception {
        int responseCode = conn.getResponseCode();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode == HttpURLConnection.HTTP_OK
                                ? conn.getInputStream()
                                : conn.getErrorStream(),
                        StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------------------------------------

    @Override
    public RuntimeResult run(Map<String, Object> detail, String data) {
        String url        = detail.getOrElse("url",         () -> "");
        String method     = detail.getOrElse("method",      () -> "");
        String login      = detail.getOrElse("login",       () -> "");
        String password   = detail.getOrElse("password",    () -> "");
        String supplierId = detail.getOrElse("supplier_id", () -> "");

        if (url.isEmpty()) {
            return new ErrorRuntimeResult("", "sales doctor url is required");
        }

        HttpURLConnection conn = null;
        try {
            conn = openConnection(URI.create(url).toURL());

            // Request body
            ObjectNode authNode = MAPPER.createObjectNode();
            authNode.put("login",    login);
            authNode.put("password", password);

            ObjectNode requestBody = MAPPER.createObjectNode();
            requestBody.put("method", method);
            requestBody.set("auth",   authNode);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(MAPPER.writeValueAsBytes(requestBody));
            }

            // Response
            int    responseCode = conn.getResponseCode();
            String responseStr  = readResponse(conn);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return new ErrorRuntimeResult("",
                        "sales doctor http error | code: " + responseCode + " | response: " + responseStr);
            }

            // Outer JSON parse
            ObjectNode outerJson = (ObjectNode) MAPPER.readTree(responseStr);

            String bodyStr;
            if (outerJson.has("body")) {
                int outerStatus = outerJson.path("status").asInt();
                if (outerStatus != 200) {
                    return new ErrorRuntimeResult("",
                            "sales doctor outer status error | status: " + outerStatus + " | response: " + responseStr);
                }
                bodyStr = outerJson.path("body").asText(null);
            } else {
                bodyStr = responseStr;
            }

            if (bodyStr == null || bodyStr.isEmpty()) {
                return new ErrorRuntimeResult("", "sales doctor response body is empty");
            }

            // Inner body parse: {"status":true,"result":{"userId":"...","token":"..."}}
            ObjectNode bodyJson = (ObjectNode) MAPPER.readTree(bodyStr);
            boolean innerStatus = bodyJson.path("status").asBoolean(false);
            if (!innerStatus) {
                String errorMessage = bodyJson.path("error").path("message").asText("unknown error");
                int    errorCode    = bodyJson.path("error").path("code").asInt(0);
                return new ErrorRuntimeResult("",
                        "sales doctor login failed | code: " + errorCode + " | message: " + errorMessage);
            }

            // Result: {"userId":"...","token":"..."}
            ObjectNode resultNode = (ObjectNode) bodyJson.path("result");
            if (resultNode.isMissingNode()) {
                return new ErrorRuntimeResult("", "sales doctor response missing 'result' field");
            }

            resultNode.put("supplier_id", supplierId);

            return new SuccessRuntimeResult("", "[" + resultNode.toString() + "]");
        } catch (Exception e) {
            return new ErrorRuntimeResult("",
                    "sales doctor connection failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}