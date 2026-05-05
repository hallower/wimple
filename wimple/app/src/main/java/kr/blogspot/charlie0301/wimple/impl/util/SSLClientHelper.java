package kr.blogspot.charlie0301.wimple.impl.util;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class SSLClientHelper {

    public static ClientConfig configureClient() {
        return new DefaultClientConfig();
    }

    public static Client createClient() {
        return Client.create(SSLClientHelper.configureClient());
    }
}
