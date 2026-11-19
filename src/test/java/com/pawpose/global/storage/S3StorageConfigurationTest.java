package com.pawpose.global.storage;

import com.pawpose.global.error.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class S3StorageConfigurationTest {
    @Test void springEnvironmentCredentialsSupportSessionTokens() {
        var env=new MockEnvironment().withProperty("AWS_ACCESS_KEY_ID","test-access")
                .withProperty("AWS_SECRET_ACCESS_KEY","test-secret")
                .withProperty("AWS_SESSION_TOKEN","test-session");
        var credentials=new S3StorageConfiguration().photoCredentials(env).resolveCredentials();
        assertThat(credentials).isInstanceOf(AwsSessionCredentials.class);
        assertThat(credentials.accessKeyId()).isEqualTo("test-access");
        assertThat(((AwsSessionCredentials)credentials).sessionToken()).isEqualTo("test-session");
        assertThatThrownBy(()->new S3StorageConfiguration().photoCredentials(
                new MockEnvironment().withProperty("AWS_ACCESS_KEY_ID","only-one-field")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void missingLocalKeysUseDefaultProviderWithoutFetchingCredentials() throws Exception {
        var provider=new S3StorageConfiguration().photoCredentials(new MockEnvironment());
        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
        ((DefaultCredentialsProvider)provider).close();
    }

    @Test void storesPublicObjectUrlAndDeletesOnlyItsGeneratedKey() throws Exception {
        S3Client client=mock(S3Client.class);
        when(client.utilities()).thenReturn(S3Utilities.builder().region(Region.AP_SOUTHEAST_2).build());
        var storage=new S3StorageConfiguration.S3Photos(client,"test-bucket");
        String key=UUID.randomUUID()+".png";
        StoredPhoto photo=storage.put(key,new byte[]{1,2,3},"image/png");
        var request=ArgumentCaptor.forClass(PutObjectRequest.class);
        var body=ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(),body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("registrations/"+key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        try(var stream=body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).containsExactly(1,2,3);
        }
        assertThat(photo.url()).isEqualTo(
                "https://test-bucket.s3.ap-southeast-2.amazonaws.com/registrations/"+key);
        storage.delete(key);
        var delete=ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo("registrations/"+key);
        assertThatThrownBy(()->storage.delete("../outside.png")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void translatesSdkFailuresToPublicErrorCode() {
        S3Client client=mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());
        var storage=new S3StorageConfiguration.S3Photos(client,"test-bucket");
        assertThatThrownBy(()->storage.put(UUID.randomUUID()+".png",new byte[]{1},"image/png"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PHOTO_UPLOAD_FAILED));
    }
}
