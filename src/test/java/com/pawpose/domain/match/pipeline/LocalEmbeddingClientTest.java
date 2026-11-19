package com.pawpose.domain.match.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LocalEmbeddingClientTest {
    @Test void encodesRawBracketsOnlyInPhotoPath() {
        var uri = LocalEmbeddingClient.imageUri("http://openapi.animal.go.kr/files/[notice]-[1].jpg");
        assertThat(uri.toString()).isEqualTo("http://openapi.animal.go.kr/files/%5Bnotice%5D-%5B1%5D.jpg");
    }

    @Test void preservesExistingEncodingSignedQueryAndFragment() {
        String suffix = "?X-Amz-Signature=abc%2F123&filter=[photo]#section[1]";
        var uri = LocalEmbeddingClient.imageUri("https://storage.example/a%20b%5B1%5D/[photo].jpg" + suffix);
        assertThat(uri.toString()).isEqualTo("https://storage.example/a%20b%5B1%5D/%5Bphoto%5D.jpg" + suffix);
        String encoded = "https://storage.example/a%5b1%5d.jpg" + suffix;
        assertThat(LocalEmbeddingClient.imageUri(encoded).toString()).isEqualTo(encoded);
    }

    @Test void leavesAuthorityAndPathlessQueryUnchanged() {
        var uri = LocalEmbeddingClient.imageUri("http://[2001:db8::1]/[photo].jpg");
        assertThat(uri.getRawAuthority()).isEqualTo("[2001:db8::1]");
        assertThat(uri.getRawPath()).isEqualTo("/%5Bphoto%5D.jpg");
        String queryOnly = "https://storage.example?next=/[photo]";
        assertThat(LocalEmbeddingClient.imageUri(queryOnly).toString()).isEqualTo(queryOnly);
        assertThatThrownBy(() -> LocalEmbeddingClient.imageUri("http://[invalid]/[photo].jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void bracketNormalizationDoesNotBypassDownloadRestrictions() {
        MatchingProperties properties = new MatchingProperties();
        properties.setImageHosts(List.of("127.0.0.1", "www.animal.go.kr"));
        var client = new LocalEmbeddingClient(properties, new ObjectMapper());
        for (String url : List.of("http://127.0.0.1/[photo].jpg", "https://evil.example/[photo].jpg",
                "https://user@www.animal.go.kr/[photo].jpg", "https://www.animal.go.kr:444/[photo].jpg",
                "https://www.animal.go.kr/[photo].jpg#fragment")) {
            assertThatThrownBy(() -> client.download(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
