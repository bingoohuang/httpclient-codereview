package util;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory to create HttpClient instance.
 *
 * @author bjca
 */
@Slf4j
public class HttpClientBuilderBuilder {
    /**
     * 请求超时时间.
     */
    private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 500;
    /**
     * 建立连接超时时间.
     */
    private static final int CONNECTION_TIMEOUT_MILLIS = 500;
    /**
     * 读取超时.
     */
    private static final int SO_TIMEOUT_MILLIS = 500;
    /**
     * 连接池最大连接数.
     */
    private static final int MAX_TOTAL = 300;
    /**
     * 每个路由默认最大连接数.
     */
    private static final int DEFAULT_MAX_PER_ROUTE = 10;

    /**
     * 创建HttpClientBuilder.
     *
     * @return HttpClientBuilder
     */
    public HttpClientBuilder build() {
        return HttpClients.custom()
                .setConnectionManager(getPoolingConnectionManager())
                .setDefaultRequestConfig(getRequestConfig())
                .setRetryHandler(getHttpRequestRetryHandler());
    }

    private HttpRequestRetryHandler getHttpRequestRetryHandler() {
        return (exception, executionCount, context) -> {
            log.warn("HttpRequestRetryHandler executionCount:{}, context:{}", executionCount, context, exception);

            if (executionCount >= 3) { // 如果已经重试了3次，就放弃
                return false;
            }

            val retryClasses = createRetryClasses();

            if (retryClasses.containsKey(exception.getClass())) {
                return retryClasses.get(exception.getClass());
            }

            for (val entry : retryClasses.entrySet()) {
                if (entry.getKey().isInstance(exception)) {
                    return entry.getValue();
                }
            }

            return isIdempotent(context); // 如果请求是幂等的，就再次尝试
        };
    }

    private Map<Class<? extends IOException>, Boolean> createRetryClasses() {
        Map<Class<? extends IOException>, Boolean> retryClasses = new LinkedHashMap<>();

        // 如果服务器丢掉了连接，那么就重试
        retryClasses.put(NoHttpResponseException.class, true);
        // 不要重试SSL握手异常
        retryClasses.put(SSLHandshakeException.class, false);
        // socket超时
        retryClasses.put(SocketTimeoutException.class, false);
        // 连接超时
        retryClasses.put(ConnectTimeoutException.class, false);
        // 超时
        retryClasses.put(InterruptedIOException.class, true);
        retryClasses.put(UnknownHostException.class, true);
        // ssl握手异常
        retryClasses.put(SSLException.class, false);
        retryClasses.put(ConnectException.class, true);
        retryClasses.put(SocketException.class, true);

        return retryClasses;
    }

    private PoolingHttpClientConnectionManager getPoolingConnectionManager() {
        val registry =
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", SSLConnectionSocketFactory.getSocketFactory())
                        .build();

        val poolConnManager = new PoolingHttpClientConnectionManager(registry);
        poolConnManager.setMaxTotal(MAX_TOTAL);
        // 将每个路由基础的连接最大 单个路由 跟总的一致
        poolConnManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);

        val socketConfig = SocketConfig.custom().setSoTimeout(SO_TIMEOUT_MILLIS).build();
        poolConnManager.setDefaultSocketConfig(socketConfig);
        return poolConnManager;
    }

    private RequestConfig getRequestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MILLIS)
                .setConnectTimeout(CONNECTION_TIMEOUT_MILLIS)
                .setSocketTimeout(SO_TIMEOUT_MILLIS)
                .build();
    }

    private boolean isIdempotent(HttpContext context) {
        val request = HttpClientContext.adapt(context).getRequest();
        return !(request instanceof HttpEntityEnclosingRequest);
    }
}
