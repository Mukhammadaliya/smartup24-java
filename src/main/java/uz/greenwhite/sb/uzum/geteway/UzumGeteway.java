package uz.greenwhite.sb.uzum.geteway;

import jakarta.servlet.http.HttpServletRequest;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("uzumGeteway")
public class UzumGeteway extends SmartupGateway {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String SERVICE_PATH = "/b/sb/sbe/uzum";

    public UzumGeteway(OkHttpClient client) {
        super(client, JSON);
    }

    public String forward(HttpServletRequest request, String authHeader, String body) throws IOException {
        String targetUrl = buildTargetUrl(request);

        return makeRequestAndGetResult(targetUrl, authHeader, body);
    }

    private String buildTargetUrl(HttpServletRequest request) {
        String baseHost = getBaseHost(request);
        String servletPath = request.getServletPath();

        int lastSlashIndex = servletPath.lastIndexOf('/');
        String transformedPath = servletPath.substring(0, lastSlashIndex) +
                ":" +
                servletPath.substring(lastSlashIndex + 1);

        return baseHost + SERVICE_PATH + transformedPath;
    }

    private String getBaseHost(HttpServletRequest request) {

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("Host");
        if (host == null || host.isEmpty()) {
            host = request.getServerName();
        }

        return scheme + "://" + host;
    }
}