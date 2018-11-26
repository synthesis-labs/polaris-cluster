package facesearch.ws.endpoint.schema;

import com.google.gson.Gson;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class WsCommandDecoder implements Decoder.Text<WsCommand> {

    private static Gson gson = new Gson();

    @Override
    public WsCommand decode(String s) throws DecodeException {
        return gson.fromJson(s, WsCommand.class);
    }

    @Override
    public boolean willDecode(String s) {
        return (s != null);
    }

    @Override
    public void init(EndpointConfig endpointConfig) {
        // Custom initialization logic
    }

    @Override
    public void destroy() {
        // Close resources
    }
}