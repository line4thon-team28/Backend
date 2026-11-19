package com.pawpose.global.storage;
import org.springframework.core.env.Environment;
final class StorageMode {
    private StorageMode() {}
    static boolean local(Environment env) {
        String type=env.getProperty("PHOTO_STORAGE_TYPE","auto");
        if(!type.equals("auto")&&!type.equals("local")&&!type.equals("s3"))
            throw new IllegalStateException("PHOTO_STORAGE_TYPE must be auto, local or s3");
        return type.equals("local")||(type.equals("auto")&&env.getProperty("S3_BUCKET","").isBlank());
    }
}
