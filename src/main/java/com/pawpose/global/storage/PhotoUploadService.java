package com.pawpose.global.storage;
import com.pawpose.global.error.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class PhotoUploadService {
    private static final long MAX_BYTES=10L*1024*1024;
    private static final long MAX_PIXELS=20_000_000;
    private final PhotoStorage storage;

    public List<StoredPhoto> upload(List<MultipartFile> files,int maxCount) {
        if(files==null||files.isEmpty()||files.size()>maxCount)
            throw new BusinessException(ErrorCode.INVALID_PHOTO_COUNT);
        List<ValidatedPhoto> valid=files.stream().map(this::validate).toList();
        List<StoredPhoto> saved=new ArrayList<>();
        List<StoredPhoto> attempted=new ArrayList<>();
        try {
            for(ValidatedPhoto p:valid) {
                String key=UUID.randomUUID()+"."+p.extension();
                attempted.add(new StoredPhoto(key,null));
                saved.add(storage.put(key,p.bytes(),p.contentType()));
            }
            return List.copyOf(saved);
        } catch(RuntimeException e) { cleanup(attempted); throw e; }
    }
    public void cleanup(List<StoredPhoto> photos) {
        for(StoredPhoto p:photos) {
            try { storage.delete(p.key()); }
            catch(RuntimeException e) { log.warn("Photo cleanup failed key={}",p.key()); }
        }
    }
    private ValidatedPhoto validate(MultipartFile file) {
        if(file==null||file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_IMAGE);
        if(file.getSize()>MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes;
        try(InputStream in=file.getInputStream()) { bytes=in.readNBytes((int)MAX_BYTES+1); }
        catch(IOException e) { throw new BusinessException(ErrorCode.INVALID_IMAGE); }
        if(bytes.length>MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if(in==null) throw new BusinessException(ErrorCode.INVALID_IMAGE);
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new BusinessException(ErrorCode.INVALID_IMAGE);
            ImageReader reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!format.equals("png")&&!format.equals("jpeg"))
                    throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
                reader.setInput(in,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||(long)width*height>MAX_PIXELS)
                    throw new BusinessException(ErrorCode.INVALID_IMAGE);
                var image=reader.read(0);
                if(image==null) throw new BusinessException(ErrorCode.INVALID_IMAGE);
                image.flush();
                return new ValidatedPhoto(bytes,format.equals("jpeg")?"jpg":"png",
                    format.equals("jpeg")?"image/jpeg":"image/png");
            } finally { reader.dispose(); }
        } catch(IOException|IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_IMAGE); }
    }
    private record ValidatedPhoto(byte[] bytes,String extension,String contentType) {}
}
