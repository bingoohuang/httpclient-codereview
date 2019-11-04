package util;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
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
import java.util.ArrayList;
import java.util.Map;

import static util.HttpClientFactory.getClient;

@Slf4j
public class HttpUtils {
  public static String get(String url, Map<String, String> paramMap, Map<String, String> headMap) {
    HttpGet httpGet = new HttpGet(url);

    if (paramMap != null && !paramMap.isEmpty()) {
      val params = new ArrayList<NameValuePair>();
      for (val entry : paramMap.entrySet()) {
        Object key = entry.getKey();
        Object value = entry.getValue();
        params.add(new BasicNameValuePair(key.toString(), value.toString()));
      }

      try {
        val str = EntityUtils.toString(new UrlEncodedFormEntity(params, "UTF-8"), "UTF-8");
        httpGet.setURI(new URI(httpGet.getURI().toString() + "?" + str));
      } catch (Exception e) {
        log.error("get请求 参数设置异常", e);
        throw new RuntimeException("get请求 参数设置异常", e);
      }
    } else {
      log.warn("paramMap is null");
    }

    // header处理
    setHeaders(headMap, httpGet);

    return execute(httpGet, "get请求异常");
  }

  public static String post(String url, String jsonContent) {
    HttpPost post = new HttpPost(url);
    val contentType = ContentType.create("application/json", Consts.UTF_8);
    post.setEntity(new StringEntity(jsonContent, contentType));
    post.setHeader("Content-Type", "application/Json");
    return execute(post, "post jsonbody请求异常");
  }

  public static String postForm(
      String url, Map<String, String> paramMap, Map<String, String> headMap) {
    HttpPost post = new HttpPost(url);

    // 参数处理
    if (paramMap != null && !paramMap.isEmpty()) {
      String paramStr = "";
      for (val entry : paramMap.entrySet()) {
        paramStr += "&" + entry.getKey() + "=" + entry.getValue();
      }
      // 构造最简单的字符串数据
      StringEntity reqEntity;
      try {
        reqEntity = new StringEntity(paramStr.substring(1), "UTF-8");
      } catch (Exception e) {
        log.warn("构造参数出错了！", e);
        return "";
      }
      // 设置类型
      reqEntity.setContentType("application/x-www-form-urlencoded");
      post.setEntity(reqEntity);
    } else {
      log.warn("paramMap is null");
      return "";
    }

    setHeaders(headMap, post);

    return execute(post, "post表单请求异常");
  }

  private static String execute(HttpUriRequest request, String exceptionMsg) {
    try {
      return getClient().execute(request, new BasicResponseHandler());
    } catch (Exception e) {
      log.error(exceptionMsg, e);
      throw new RuntimeException(
          request.getMethod() + " " + request.getURI() + " exception " + exceptionMsg, e);
    } finally {
      // To abort an ongoing request, the client can simply use:
      //
      // This will make sure that the client doesn't have to consume the entire body of the request
      // to release the connection:
      request.abort();
    }
  }

  private static void setHeaders(Map<String, String> headMap, AbstractHttpMessage httpMessage) {
    if (headMap == null) {
      return;
    }

    for (val entry : headMap.entrySet()) {
      httpMessage.setHeader(entry.getKey(), entry.getValue());
    }
  }
}
