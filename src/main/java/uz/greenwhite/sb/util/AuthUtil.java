package uz.greenwhite.sb.util;

import org.apache.cxf.transport.http.auth.HttpAuthHeader;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class AuthUtil {

    public static String getBasicAuth(String username, String password) {
        String value = String.format("%s:%s", username, password);

        String code = Base64.getEncoder()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));

        return String.format("%s %s", HttpAuthHeader.AUTH_TYPE_BASIC, code);
    }
}
