package uz.greenwhite.sb;
import okhttp3.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import scala.collection.JavaConverters$;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeResult;
import uz.greenwhite.biruni.old.service.runtimeservice.RuntimeService;
import uz.greenwhite.biruni.old.service.runtimeservice.SuccessRuntimeResult;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.Map;

@Service
@Scope("prototype")
public class CurrencyRateService implements RuntimeService {
    private final OkHttpClient client = getUnsafeOkHttpClient();
    private String url;

    @Override
    public RuntimeResult run(scala.collection.immutable.Map<String, Object> detail, String data) {
        Map<String, Object> jDetail = JavaConverters$.MODULE$.mapAsJavaMapConverter(detail).asJava();

        url = (String) jDetail.get("url");

        return SuccessRuntimeResult.apply(request());
    }

    private String request() {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            checkResponse(response);
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkResponse(Response response) throws IOException {
        if (response.isSuccessful()) return;

        switch (response.code()) {
            case 400:
                throw new RuntimeException("Bad request in method with error message " + response.body().string());
            case 401:
                throw new RuntimeException("Unauthorized in method with error message " + response.body().string());
            case 403:
                throw new RuntimeException("Forbidden in method with error message " + response.body().string());
            default:
                throw new RuntimeException("Unknown error in method with error code " + response.code() + " and error message " + response.body().string());
        }
    }

    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}