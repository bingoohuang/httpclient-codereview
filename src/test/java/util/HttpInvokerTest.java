package util;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class HttpInvokerTest {

    @Test
    public void get() {
        for (int i = 0; i < 10; i++) {
            String say = new HttpInvoker().get("http://127.0.0.1:9002/say", null, null);
            assertThat(say).contains("Hi there, say I love you!");
        }
    }
}
