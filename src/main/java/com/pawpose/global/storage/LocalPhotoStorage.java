package com.pawpose.global.storage;
import com.pawpose.global.error.*;
import java.io.IOException;
import java.nio.file.*;
public class LocalPhotoStorage implements PhotoStorage {
    private final Path root;
    private final String baseUrl;
    public LocalPhotoStorage(Path root,String baseUrl) {
        this.root=root.toAbsolutePath().normalize();
        this.baseUrl=baseUrl.replaceAll("/+$","");
    }
    public StoredPhoto put(String key,byte[] content,String contentType) {
        try {
            Files.createDirectories(root);
            Files.write(path(key),content,StandardOpenOption.CREATE_NEW);
            return new StoredPhoto(key,baseUrl+"/"+key);
        } catch(IOException e) { throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED); }
    }
    public void delete(String key) {
        try { Files.deleteIfExists(path(key)); }
        catch(IOException e) { throw new IllegalStateException("Local photo cleanup failed"); }
    }
    private Path path(String key) {
        Path target=root.resolve(key).normalize();
        if(!target.startsWith(root)||!key.matches("[a-f0-9-]+\\.(jpg|png)"))
            throw new IllegalArgumentException("Invalid storage key");
        return target;
    }
}
