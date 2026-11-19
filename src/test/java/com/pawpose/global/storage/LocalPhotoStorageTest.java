package com.pawpose.global.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LocalPhotoStorageTest {
    @TempDir Path temp;
    @Test void writesActualBytesAndDeletesOnlyGeneratedKeys() throws Exception {
        LocalPhotoStorage storage=new LocalPhotoStorage(temp,"http://localhost:8080/uploads");
        String key=UUID.randomUUID()+".png";
        StoredPhoto photo=storage.put(key,new byte[]{1,2,3},"image/png");
        assertThat(Files.readAllBytes(temp.resolve(key))).containsExactly(1,2,3);
        assertThat(photo.url()).isEqualTo("http://localhost:8080/uploads/"+key);
        assertThatThrownBy(()->storage.delete("../outside.png")).isInstanceOf(IllegalArgumentException.class);
        storage.delete(key);
        assertThat(Files.exists(temp.resolve(key))).isFalse();
    }
    @Test void productionCannotSilentlyUseLocalStorage() {
        MockEnvironment env=new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThatThrownBy(()->new LocalStorageConfiguration(env,temp.toString(),"http://localhost/uploads"))
            .isInstanceOf(IllegalStateException.class);
    }
}
