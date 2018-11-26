package facesearch.ws.endpoint.schema;

import com.google.gson.Gson;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class WsCommandEncoder implements Encoder.Text<WsCommand> {

    private static Gson gson = new Gson();

    @Override
    public String encode(WsCommand message) throws EncodeException {
        return gson.toJson(message);
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