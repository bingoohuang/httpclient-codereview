package util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.Consts;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Http 操作辅助类。
 *
 * @author bjca
 */
@Slf4j
@UtilityClass
public class HttpUtils {
    private final HttpClient httpClient = new HttpClientBuilderBuilder().build().build();
    private final String UTF_8 = "UTF-8";
    private final ContentType CONTENT_TYPE_JSON = ContentType.create("application/json", Consts.UTF_8);

    /**
     * 执行GET请求。
     *
     * @param url     请求地址，例如 http://127.0.0.1:9001/say
     * @param params  查询参数。没有参数时，传null
     * @param headers 请求头。没有请求头时，传null
     * @return 响应报文体
     */
    public String get(String url, Map<String, String> params, Map<String, String> headers) {
        HttpGet httpGet = new HttpGet(url);

        if (params != null && !params.isEmpty()) {
            val paramList = params.entrySet().stream()
                    .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            try {
                val str = EntityUtils.toString(new UrlEncodedFormEntity(paramList, UTF_8), UTF_8);
                httpGet.setURI(new URI(httpGet.getURI().toString() + "?" + str));
            } catch (Exception e) {
                log.error("get请求 参数设置异常", e);
                throw new RuntimeException("get请求 参数设置异常", e);
            }
        }

        setHeaders(headers, httpGet);

        return execute(httpGet, "get请求异常");
    }

    /**
     * 执行POST请求。
     *
     * @param url         请求地址，例如 http://127.0.0.1:9001/say
     * @param jsonContent JSON请求体
     * @return 响应报文体
     */
    public String postJSON(String url, String jsonContent) {
        val post = new HttpPost(url);
        post.setEntity(new StringEntity(jsonContent, CONTENT_TYPE_JSON));
        post.setHeader("Content-Type", "application/Json");
        return execute(post, "post json body 请求异常");
    }

    /**
     * 执行表单POST请求。
     *
     * @param url     请求地址，例如 http://127.0.0.1:9001/say
     * @param params  表单参数，没有参数时，传null
     * @param headers 请求头，没有请求头时，传null
     * @return 响应报文体
     */
    public String postForm(String url, Map<String, String> params, Map<String, String> headers) {
        val post = new HttpPost(url);

        if (params != null && !params.isEmpty()) {
            val paramStr = new StringBuilder();
            for (val entry : params.entrySet()) {
                paramStr.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
            val reqEntity = new StringEntity(paramStr.substring(1), UTF_8);
            reqEntity.setContentType("application/x-www-form-urlencoded");
            post.setEntity(reqEntity);
        }

        setHeaders(headers, post);

        return execute(post, "post表单请求异常");
    }

    private String execute(HttpUriRequest request, String exceptionMsg) {
        try {
            return httpClient.execute(request, new BasicResponseHandler());
        } catch (Exception e) {
            log.error(exceptionMsg, e);
            throw new RuntimeException(
                    request.getMethod() + " " + request.getURI() + " exception " + exceptionMsg, e);
        } finally {
            // This will make sure that the client doesn't have to consume the entire body of the request
            // to release the connection:
            request.abort();
        }
    }

    private void setHeaders(Map<String, String> headMap, AbstractHttpMessage httpMessage) {
        if (headMap == null) {
            return;
        }

        for (val entry : headMap.entrySet()) {
            httpMessage.setHeader(entry.getKey(), entry.getValue());
        }
    }
}
