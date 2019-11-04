package util;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
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

@Slf4j
public abstract class HttpClientFactory {

  /** 请求超时时间 */
  private static final int connectionRequestTimeout = 500; // 500毫秒
  /** 建立连接超时时间 */
  private static final int connectionTimeoutTime = 500; // 500毫秒
  /** 读取超时 */
  private static final int soTimeoutTime = 500; // 500 毫秒

  private static final int maxTotal = 300;
  private static final int defaultMaxPerRoute = 300;

  private static HttpClientBuilder httpBuilder = createHttpClientBuilderWithLogEx();

  public static HttpClient getClient() {
    return httpBuilder.build();
  }

  private static HttpClientBuilder createHttpClientBuilderWithLogEx() {
    try {
      return createHttpClientBuilder();
    } catch (Exception e) {
      log.error("httpClient初始化异常了！", e);
      throw new RuntimeException("初始化HttpClient连接池失败了！", e);
    }
  }

  private static HttpClientBuilder createHttpClientBuilder() {
    return HttpClients.custom()
        .setConnectionManager(getPoolingConnectionManager())
        .setDefaultRequestConfig(getRequestConfig())
        .setRetryHandler(getHttpRequestRetryHandler());
  }

  private static HttpRequestRetryHandler getHttpRequestRetryHandler() {
    val retryClasses = new LinkedHashMap<Class<? extends IOException>, Boolean>();
    retryClasses.put(NoHttpResponseException.class, true); // 如果服务器丢掉了连接，那么就重试
    retryClasses.put(SSLHandshakeException.class, false); // 不要重试SSL握手异常
    retryClasses.put(SocketTimeoutException.class, false); // socket超时
    retryClasses.put(ConnectTimeoutException.class, false); // 连接超时
    retryClasses.put(InterruptedIOException.class, true); // 超时
    retryClasses.put(UnknownHostException.class, true); // 目标服务器不可达
    retryClasses.put(SSLException.class, false); // ssl握手异常
    retryClasses.put(ConnectException.class, true);
    retryClasses.put(SocketException.class, true);

    // 请求重试处理
    return (exception, executionCount, context) -> {
      log.warn("HttpRequestRetryHandler executionCount:{}", executionCount, exception);

      if (executionCount >= 5) { // 如果已经重试了5次，就放弃
        return false;
      }

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

  private static PoolingHttpClientConnectionManager getPoolingConnectionManager() {
    val registry =
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", SSLConnectionSocketFactory.getSocketFactory())
            .build();

    val poolConnManager = new PoolingHttpClientConnectionManager(registry);
    poolConnManager.setMaxTotal(maxTotal);
    // 将每个路由基础的连接最大 单个路由 跟总的一致
    poolConnManager.setDefaultMaxPerRoute(defaultMaxPerRoute);

    val socketConfig = SocketConfig.custom().setSoTimeout(soTimeoutTime).build();
    poolConnManager.setDefaultSocketConfig(socketConfig);
    return poolConnManager;
  }

  private static RequestConfig getRequestConfig() {
    return RequestConfig.custom()
        .setConnectionRequestTimeout(connectionRequestTimeout)
        .setConnectTimeout(connectionTimeoutTime)
        .setSocketTimeout(soTimeoutTime)
        .build();
  }

  private static boolean isIdempotent(HttpContext context) {
    val request = HttpClientContext.adapt(context).getRequest();
    return !(request instanceof HttpEntityEnclosingRequest);
  }
}
