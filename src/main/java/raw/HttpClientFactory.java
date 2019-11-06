package raw;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientFactory {

  private static Logger log = LoggerFactory.getLogger(HttpClientFactory.class);

  /**
   * 请求超时时间
   */
  private static final Integer connectionRequestTimeout = 500;// 500毫秒
  /**
   * 建立连接超时时间
   */
  private static final Integer connectionTimeoutTime = 500;// 500毫秒
  /**
   * 读取超时
   */
  private static final Integer soTimeoutTime = 500;// 500 毫秒

  private static final int MaxTotal = 300;
  private static final int DefaultMaxPerRoute = 300;
  private static HttpClientBuilder httpBulder = null;
  static final String CONTENT_TYPE = "Content-Type";
  static final String BASP_CONTENT_TYPE = "application/Json";

  static {
    init();
  }

  public static HttpClient getClient() {
    CloseableHttpClient httpClient = httpBulder.build();
    return httpClient;
  }

  private static void init() {
    try {
      ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
      LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();

      Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
          .register("http", plainsf)
          .register("https", sslsf)
          .build();

      PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager(
          registry);
      poolConnManager.setMaxTotal(MaxTotal);
      // 将每个路由基础的连接最大  单个路由 跟总的一致
      poolConnManager.setDefaultMaxPerRoute(DefaultMaxPerRoute);

      SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(soTimeoutTime).build();
      poolConnManager.setDefaultSocketConfig(socketConfig);

      RequestConfig requestConfig = RequestConfig.custom()
          .setConnectionRequestTimeout(connectionRequestTimeout)
          .setConnectTimeout(connectionTimeoutTime)
          .setSocketTimeout(soTimeoutTime).build();

      //请求重试处理
      HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
        public boolean retryRequest(IOException exception, int executionCount,
            HttpContext context) {
          if (executionCount >= 5) {// 如果已经重试了5次，就放弃
            return false;
          }
          if (exception instanceof NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
            return true;
          }
          if (exception instanceof SSLHandshakeException) {// 不要重试SSL握手异常
            return false;
          }
          if (exception instanceof InterruptedIOException) {// 超时
            return true;
          }
          if (exception instanceof UnknownHostException) {// 目标服务器不可达
            return true;
          }
          if (exception instanceof ConnectTimeoutException) {// 连接被拒绝
            return false;
          }
          if (exception instanceof SSLException) {// ssl握手异常
            return false;
          }
          if (exception instanceof ConnectException){
            return true;
          }
          if (exception instanceof SocketException){
            return true;
          }

          HttpClientContext clientContext = HttpClientContext.adapt(context);
          HttpRequest request = clientContext.getRequest();
          // 如果请求是幂等的，就再次尝试
          if (!(request instanceof HttpEntityEnclosingRequest)) {
            return true;
          }
          return false;
        }
      };

      httpBulder = HttpClients.custom()
          .setConnectionManager(poolConnManager)
          .setDefaultRequestConfig(requestConfig)
          .setRetryHandler(httpRequestRetryHandler);

    } catch (Exception e) {
      log.error("httpClient初始化异常了！");
      throw new RuntimeException("初始化HttpClient连接池失败了！", e);
    }
  }

}
