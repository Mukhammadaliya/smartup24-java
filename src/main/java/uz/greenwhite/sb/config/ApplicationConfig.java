package uz.greenwhite.sb.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sb")
@Component("sbApplicationConfig")
@Data
public class ApplicationConfig {

    private String messageBrokerUrl;
    private String integrationUri;
}
