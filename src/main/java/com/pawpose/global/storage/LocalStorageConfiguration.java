package com.pawpose.global.storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
@Conditional(LocalStorageCondition.class)
public class LocalStorageConfiguration implements WebMvcConfigurer {
    private final Path directory;
    private final String baseUrl;
    public LocalStorageConfiguration(Environment env,
        @Value("${pawpose.storage.local-directory:./data/uploads}") String directory,
        @Value("${pawpose.storage.local-base-url:http://localhost:8080/uploads}") String baseUrl) {
        if(!env.acceptsProfiles(Profiles.of("local","test"))||env.acceptsProfiles(Profiles.of("prod")))
            throw new IllegalStateException("Local storage requires local/test profile; configure S3 for production");
        this.directory=Path.of(directory).toAbsolutePath().normalize();
        this.baseUrl=baseUrl;
    }
    @Bean public PhotoStorage photoStorage() { return new LocalPhotoStorage(directory,baseUrl); }
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location=directory.toUri().toString();
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(location.endsWith("/")?location:location+"/");
    }
}
class LocalStorageCondition implements Condition {
    public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
        return StorageMode.local(context.getEnvironment());
    }
}
