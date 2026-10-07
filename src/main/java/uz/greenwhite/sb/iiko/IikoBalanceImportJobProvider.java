package uz.greenwhite.sb.iiko;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import uz.greenwhite.biruni.old.job.JobProvider;
import uz.greenwhite.biruni.old.job.JobResult;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IikoBalanceImportJobProvider implements JobProvider {

    private static final String AUTH_URL = "%s/resto/api/auth?login=%s&pass=%s";
    private static final String LOGOUT_URL = "%s/resto/api/logout?key=%s";
    private static final String BALANCE_URL = "%s/resto/api/v2/reports/balance/stores?key=%s&timestamp=%s%s%s";

    // =========================
    // ENTRY POINT
    // =========================
    @Override
    public JobResult run(String requestData) {

        if (requestData == null || requestData.isBlank()) {
            return new JobResult(400, "Request data is empty");
        }

        JSONArray finalResult = new JSONArray();

        try {
            JSONArray clients = new JSONArray(requestData);

            for (int i = 0; i < clients.length(); i++) {
                JSONObject clientResponse = processClientBalanceImport(clients.getJSONObject(i));
                finalResult.put(clientResponse);
            }

            return new JobResult(200, finalResult.toString());

        } catch (Exception e) {
            return new JobResult(500, "Server error: " + e.getMessage());
        }
    }

    // =========================
    // CLIENT PROCESSING
    // =========================
    private JSONObject processClientBalanceImport(JSONObject clientData) {

        String companyId = clientData.optString("company_id");
        String clientId = clientData.optString("client_id");
        String requestHost = clientData.getString("request_host");
        String login = clientData.getString("login");
        String password = clientData.getString("password");

        JSONObject requestParams = clientData.getJSONObject("request_params");
        String timestamp = requestParams.getString("timestamp");
        JSONArray productIds = requestParams.getJSONArray("product_ids");
        JSONArray storeIds = requestParams.getJSONArray("store_ids");

        String token = null;

        try {
            token = getIikoToken(requestHost, login, password);

            String productQuery = IntStream.range(0, productIds.length())
                    .mapToObj(i -> productIds.optString(i))
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> "&product=" + id)
                    .collect(Collectors.joining());

            String storeQuery = IntStream.range(0, storeIds.length())
                    .mapToObj(i -> storeIds.optString(i))
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> "&store=" + id)
                    .collect(Collectors.joining());

            String balanceData = getBalance(requestHost, token, timestamp, productQuery, storeQuery);

            JSONArray iikoResponse = new JSONArray(balanceData);

            JSONObject result = new JSONObject();
            result.put("company_id", companyId);
            result.put("client_id", clientId);
            result.put("data", iikoResponse);

            return result;

        } finally {
            releaseIikoToken(requestHost, token);
        }
    }

    // =========================
    // IIKO API
    // =========================
    private String getIikoToken(String baseUrl, String login, String password) {

        String url = String.format(AUTH_URL, baseUrl, login, password);

        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri(url)
                .retrieve()
                .toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful() ||
                response.getBody() == null ||
                response.getBody().isBlank()) {
            throw new RuntimeException("Failed to get IIKO token");
        }

        return response.getBody();
    }

    private void releaseIikoToken(String baseUrl, String token) {
        if (token == null || token.isBlank()) return;
        try {
            String url = String.format(LOGOUT_URL, baseUrl, token);
            RestClient.create().get().uri(url).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
        }
    }

    private String getBalance(String baseUrl, String token, String timestamp, String productQuery, String storeQuery) {

        String url = String.format(BALANCE_URL, baseUrl, token, timestamp, productQuery, storeQuery);

        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri(url)
                .retrieve()
                .toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("IIKO HTTP error: " + response.getStatusCode());
        }

        if (response.getBody() == null || response.getBody().isBlank()) {
            throw new RuntimeException("Empty response from IIKO");
        }

        return response.getBody();
    }
}