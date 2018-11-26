package facesearch.ws.endpoint.schema;

import com.google.gson.JsonElement;

public class WsCommand {
    public String type;
    public String cmd;
    public JsonElement data;
}
