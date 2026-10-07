package uz.greenwhite.sb.iiko;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import uz.greenwhite.biruni.old.job.JobProvider;
import uz.greenwhite.biruni.old.job.JobResult;

public class IikoOrderSyncJobProvider implements JobProvider {

    private static final String AUTH_URL = "%s/resto/api/auth?login=%s&pass=%s";
    private static final String LOGOUT_URL = "%s/resto/api/logout?key=%s";
    private static final String INVOICE_URL = "%s/resto/api/documents/import/incomingInvoice?key=%s";

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
                JSONArray clientResponses = processClientSync(clients.getJSONObject(i));

                for (int j = 0; j < clientResponses.length(); j++) {
                    finalResult.put(clientResponses.getJSONObject(j));
                }
            }

            return new JobResult(200, finalResult.toString());

        } catch (Exception e) {
            return new JobResult(500, "Server error: " + e.getMessage());
        }
    }

    // =========================
    // CLIENT PROCESSING
    // =========================
    private JSONArray processClientSync(JSONObject clientData) {

        JSONArray responses = new JSONArray();

        String companyId = clientData.optString("company_id");
        String clientId = clientData.optString("client_id");
        String requestHost = clientData.getString("request_host");
        String login = clientData.getString("login");
        String password = clientData.getString("password");

        JSONArray invoices = clientData.getJSONObject("data").getJSONArray("invoices");

        if (invoices.isEmpty()) {
            return responses;
        }

        String token = getIikoToken(requestHost, login, password);

        try {
            for (int i = 0; i < invoices.length(); i++) {

                JSONObject invoice = invoices.getJSONObject(i);
                JSONObject result = new JSONObject();

                result.put("company_id", companyId);
                result.put("client_id", clientId);
                result.put("deal_id", invoice.optString("deal_id"));
                result.put("document_number", invoice.optString("documentNumber"));

                try {
                    String xml = convertInvoiceToXml(invoice);
                    String response = sendInvoice(requestHost, token, xml);

                    if (isValidResponse(response)) {
                        JSONObject output = new JSONObject();
                        output.put("valid", true);
                        output.put("document_number", extractTag(response, "documentNumber"));

                        result.put("status", "SUCCESS");
                        result.put("output", output);
                    } else {
                        result.put("status", "ERROR");
                        result.put("error_message", extractErrorMessage(response));
                    }

                } catch (Exception ex) {
                    result.put("status", "ERROR");
                    result.put("error_message", ex.getMessage());
                }

                responses.put(result);

                // Required by iiko API limitations
                Thread.sleep(2000);
            }

        } catch (Exception e) {
            responses.put(new JSONObject().put("status", "ERROR").put("error_message", e.getMessage()));
        } finally {
            releaseIikoToken(requestHost, token);
        }

        return responses;
    }

    // =========================
    // IIKO API
    // =========================
    private String getIikoToken(String baseUrl, String login, String password) {

        String url = String.format(AUTH_URL, baseUrl, login, password);

        ResponseEntity<String> response = RestClient.create().get().uri(url).retrieve().toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
            throw new RuntimeException("Failed to get IIKO token");
        }

        return response.getBody();
    }

    private void releaseIikoToken(String baseUrl, String token) {
        if (token == null) return;
        try {
            String url = String.format(LOGOUT_URL, baseUrl, token);
            RestClient.create().get().uri(url).retrieve();
        } catch (Exception ignored) {
        }
    }

    private String sendInvoice(String baseUrl, String token, String xml) {

        String url = String.format(INVOICE_URL, baseUrl, token);

        ResponseEntity<String> response = RestClient.create().post().uri(url).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE).body(xml).retrieve().toEntity(String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("IIKO HTTP error: " + response.getStatusCode());
        }

        return response.getBody();
    }

    // =========================
    // XML BUILD
    // =========================
    private String convertInvoiceToXml(JSONObject invoice) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<document>\n");

        append(xml, "documentNumber", invoice.optString("documentNumber"), 4);
        append(xml, "dateIncoming", invoice.optString("dateIncoming"), 4);
        append(xml, "dueDate", invoice.optString("dueDate"), 4);
        append(xml, "supplier", invoice.optString("supplier"), 4);
        append(xml, "defaultStore", invoice.optString("defaultStore"), 4);
        append(xml, "employeePassToAccount", invoice.optString("employeePassToAccount", ""), 4);
        append(xml, "comment", invoice.optString("comment"), 4);
        append(xml, "totalSum", invoice.optDouble("totalSum", 0), 4);
        append(xml, "discountSum", String.valueOf(invoice.optDouble("discountSum", 0.0)), 4);
        append(xml, "status", invoice.optString("status", "NEW"), 4);

        JSONArray items = invoice.optJSONArray("items");
        if (items != null && !items.isEmpty()) {
            xml.append("    <items>\n");

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);

                xml.append("        <item>\n");
                append(xml, "store", invoice.optString("defaultStore"), 12);
                append(xml, "num", item.optInt("num", i + 1), 12);
                append(xml, "product", item.optString("product"), 12);
                append(xml, "amount", item.optDouble("amount", 0), 12);
                append(xml, "price", item.optDouble("price", 0), 12);
                append(xml, "sum", item.optDouble("sum", 0), 12);
                append(xml, "discountSum", item.optDouble("sum", 0), 12);
                xml.append("        </item>\n");
            }

            xml.append("    </items>\n");
        }

        xml.append("</document>");
        return xml.toString();
    }

    private void append(StringBuilder xml, String tag, Object value, int indent) {
        if (value == null || value.toString().isBlank()) return;
        xml.append(" ".repeat(indent)).append("<").append(tag).append(">").append(escape(value)).append("</").append(tag).append(">\n");
    }

    private String escape(Object v) {
        return v.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // =========================
    // RESPONSE PARSING
    // =========================
    private boolean isValidResponse(String xml) {
        return xml != null && xml.contains("<valid>true</valid>");
    }

    private String extractErrorMessage(String xml) {
        if (xml == null) return "Empty response";
        return extractTag(xml, "errorMessage");
    }

    private String extractTag(String xml, String tag) {
        int s = xml.indexOf("<" + tag + ">");
        int e = xml.indexOf("</" + tag + ">");
        if (s == -1 || e == -1) return null;
        return xml.substring(s + tag.length() + 2, e).trim();
    }
}
