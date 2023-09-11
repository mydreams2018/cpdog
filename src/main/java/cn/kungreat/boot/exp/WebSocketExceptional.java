package cn.kungreat.boot.exp;

public class WebSocketExceptional extends Exception{

    public WebSocketExceptional(String message) {
        super(message);
    }

    public WebSocketExceptional(String message, Throwable cause) {
        super(message, cause);
    }
}
