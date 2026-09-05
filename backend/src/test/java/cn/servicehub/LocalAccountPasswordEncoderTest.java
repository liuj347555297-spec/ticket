package cn.servicehub;

import cn.servicehub.security.LocalAccountPasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class LocalAccountPasswordEncoderTest {
    @Test void acceptsMultibytePasswordBeyondBcryptRawByteBoundaryWithoutTruncation() {
        String password="多字节密码安全验证".repeat(4); // well over 72 UTF-8 bytes, within 128 Java chars
        String changed=password.substring(0,password.length()-1)+"错";
        var encoder=new LocalAccountPasswordEncoder();String hash=encoder.encode(password);
        assertTrue(hash.startsWith("{bcrypt}"));assertTrue(encoder.matches(password,hash));
        assertNotEquals(encoder.matches(changed,hash),true);
    }
}
