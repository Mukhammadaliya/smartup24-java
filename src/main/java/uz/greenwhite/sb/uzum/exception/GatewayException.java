package uz.greenwhite.sb.uzum.exception;

import lombok.Getter;

@Getter
public class GatewayException extends RuntimeException {
    private final int code;

    public GatewayException(int code, String description) {
        super(description);
        this.code = code;
    }
}