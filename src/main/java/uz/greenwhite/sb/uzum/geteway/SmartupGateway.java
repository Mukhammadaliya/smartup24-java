package uz.greenwhite.sb.uzum.geteway;

import lombok.RequiredArgsConstructor;
import okhttp3.*;
import uz.greenwhite.sb.uzum.exception.GatewayException;

import java.io.IOException;

@RequiredArgsConstructor
public abstract class SmartupGateway {
    protected final OkHttpClient client;
    private final MediaType JSON;

    protected RequestBody createRequestBody(String body) {
        return RequestBody.create(body, JSON);
    }

    protected String makeRequestAndGetResult(String requestUrl, String authHeader, String body) throws IOException {
        Request request = new Request.Builder()
                .url(requestUrl)
                .post(createRequestBody(body))
                .addHeader("Authorization", authHeader)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (response.isSuccessful()) {
                return responseBody;
            } else {
                throw new GatewayException(response.code(), responseBody);
            }
        }
    }
}