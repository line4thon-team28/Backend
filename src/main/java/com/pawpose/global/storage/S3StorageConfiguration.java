package com.pawpose.global.storage;

import com.pawpose.global.error.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import java.time.Duration;

@Configuration
@Conditional(S3StorageCondition.class)
public class S3StorageConfiguration {
    @Bean
    public AwsCredentialsProvider photoCredentials(Environment env) {
        String id=env.getProperty("AWS_ACCESS_KEY_ID","");
        String secret=env.getProperty("AWS_SECRET_ACCESS_KEY","");
        String token=env.getProperty("AWS_SESSION_TOKEN","");
        if(id.isBlank()&&secret.isBlank()) return DefaultCredentialsProvider.create();
        if(id.isBlank()||secret.isBlank()) throw new IllegalStateException("Both local AWS credential fields are required");
        return StaticCredentialsProvider.create(token.isBlank()
                ?AwsBasicCredentials.create(id,secret):AwsSessionCredentials.create(id,secret,token));
    }

    @Bean(destroyMethod="close")
    public S3Client photoS3Client(Environment env,AwsCredentialsProvider photoCredentials) {
        return S3Client.builder().region(Region.of(env.getProperty("AWS_REGION","ap-southeast-2")))
                .credentialsProvider(photoCredentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(20))).build();
    }

    @Bean
    public PhotoStorage photoStorage(S3Client photoS3Client,Environment env) {
        String bucket=env.getProperty("S3_BUCKET","");
        if(!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalStateException("Configure a valid S3_BUCKET");
        return new S3Photos(photoS3Client,bucket);
    }

    static class S3Photos implements PhotoStorage {
        private final S3Client s3;
        private final String bucket;
        S3Photos(S3Client s3,String bucket) { this.s3=s3; this.bucket=bucket; }
        public StoredPhoto put(String key,byte[] content,String contentType) {
            validateKey(key);
            try {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key("registrations/"+key)
                        .contentType(contentType).cacheControl("max-age=86400").build(),RequestBody.fromBytes(content));
                String url=s3.utilities().getUrl(GetUrlRequest.builder().bucket(bucket).key("registrations/"+key).build()).toString();
                return new StoredPhoto(key,url);
            } catch(RuntimeException e) { throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED); }
        }
        public void delete(String key) {
            validateKey(key);
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key("registrations/"+key).build());
        }
        private void validateKey(String key) {
            if(!key.matches("[a-f0-9-]+\\.(jpg|png)")) throw new IllegalArgumentException("Invalid storage key");
        }
    }
}
class S3StorageCondition implements Condition {
    public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
        return !StorageMode.local(context.getEnvironment());
    }
}
