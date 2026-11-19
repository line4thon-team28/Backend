package com.pawpose.domain.registration;
import com.pawpose.domain.notice.collection.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

class RegistrationReferenceServiceTest {
    @Test void officialNameAndCodeResolveToSameDistrictAndCacheIsReused() {
        PublicAnimalApiClient api=mock(PublicAnimalApiClient.class);
        CollectionProperties properties=new CollectionProperties();
        properties.setServiceKey("test-key");
        when(api.fetchDistricts("6110000")).thenReturn(Map.of("테스트구","TEST_CODE"));
        var service=new RegistrationReferenceService(api,properties);
        var byName=service.resolve("서울특별시","테스트구");
        var byCode=service.resolve("6110000","TEST_CODE");
        assertThat(byName).isEqualTo(byCode);
        assertThat(byName.sigunguCode()).isEqualTo("TEST_CODE");
        verify(api,times(1)).fetchDistricts("6110000");
    }
    @Test void offlineNamesDoNotBecomeFakeOfficialCodes() {
        var service=new RegistrationReferenceService(mock(PublicAnimalApiClient.class),new CollectionProperties());
        assertThat(service.resolve("서울","강남구").sigunguCode()).isNull();
        assertThat(service.regions(null)).hasSize(17);
        assertThat(service.regions("6110000").get(0).districtMode()).isEqualTo("MANUAL");
    }
}
