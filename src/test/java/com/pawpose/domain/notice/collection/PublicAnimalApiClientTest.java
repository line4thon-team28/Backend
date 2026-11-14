package com.pawpose.domain.notice.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.notice.NoticeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublicAnimalApiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsV2AndLegacyPhotosAndClosedState() throws Exception {
        String item = """
                {"desertionNo":"20260001","noticeNo":"notice-1","upKindCd":"417000",
                 "kindNm":"믹스견","happenDt":"20261101","noticeSdt":"20261102",
                 "noticeEdt":"20261112","orgNm":"서울특별시 강북구","orgCd":"3080000",
                 "popfile1":"https://example.test/large.jpg","filename":"small.jpg",
                 "processState":"종료(반환)","sexCd":"M"}
                """;
        var result = new PublicAnimalParser().parse(mapper.readTree(page(item)), 1, 100);
        CollectedNotice notice = result.notices().get(0);
        assertThat(notice.species()).isEqualTo(Species.DOG);
        assertThat(notice.status()).isEqualTo(NoticeStatus.RETURNED);
        assertThat(notice.imageUrl()).isEqualTo("https://example.test/large.jpg");
        assertThat(notice.sidoCode()).isEqualTo("6110000");
        assertThat(notice.sigunguCode()).isEqualTo("3080000");
        assertThat(notice.noticeStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));

        var legacy = new PublicAnimalParser().parse(mapper.readTree(page("""
                {"desertion_no":"legacy","kindCd":"[고양이] 한국 고양이",
                 "popfile":"https://example.test/cat.jpg","process_state":"보호중"}
                """)), 1, 100).notices().get(0);
        assertThat(legacy.species()).isEqualTo(Species.CAT);
        assertThat(legacy.breed()).isEqualTo("한국 고양이");
        assertThat(legacy.sigunguCode()).isNull();
    }

    @Test
    void missingRowsAndUnknownSpeciesCannotLookLikeSuccessfulEmptyPage() throws Exception {
        var parser = new PublicAnimalParser();
        var missing = mapper.readTree("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":2,"items":""}}}
                """);
        assertThatThrownBy(() -> parser.parse(missing, 1, 100)).isInstanceOf(CollectionFailure.class);
        var unknown = mapper.readTree(page("""
                {"desertionNo":"1","upKindCd":"unknown","processState":"보호중"}
                """));
        assertThatThrownBy(() -> parser.parse(unknown, 1, 100)).isInstanceOf(CollectionFailure.class);
    }

    @Test
    void incrementalQueryEncodesRawKeyOnceAndDoesNotFilterClosedNotices() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CollectionProperties properties = new CollectionProperties();
        properties.setServiceKey("fixture+key/=");
        PublicAnimalApiClient client = new PublicAnimalApiClient(properties, builder.build());
        server.expect(request -> {
            String query = request.getURI().getRawQuery();
            assertThat(query).contains("serviceKey=fixture%2Bkey%2F%3D", "bgupd=20261101", "enupd=20261102");
            assertThat(query).doesNotContain("state=", "bgnde=", "%252B");
        }).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":0,"items":""}}}
                """, MediaType.APPLICATION_JSON));
        assertThat(client.fetch(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), false, 1).notices()).isEmpty();
        server.verify();
    }

    @Test
    void rateLimitIsClassifiedWithoutLeakingRequestKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CollectionProperties properties = new CollectionProperties();
        properties.setServiceKey("fixture-private-value");
        PublicAnimalApiClient client = new PublicAnimalApiClient(properties, builder.build());
        server.expect(request -> {}).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.fetch(LocalDate.now(), LocalDate.now(), true, 1))
                .isInstanceOf(CollectionFailure.class).hasMessage("QUOTA").hasNoCause();
        server.verify();
    }

    @Test
    void districtLookupUsesOfficialOrgCodeAndName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicAnimalApiClient client = new PublicAnimalApiClient(new CollectionProperties(), builder.build());
        server.expect(request -> {
            assertThat(request.getURI().getPath()).endsWith("/sigungu_v2");
            assertThat(request.getURI().getRawQuery()).contains("upr_cd=6480000");
        }).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":1,
                "items":{"item":[{"uprCd":"6480000","orgCd":"5370000","orgdownNm":"거제시"}]}}}}
                """, MediaType.APPLICATION_JSON));
        assertThat(client.fetchDistricts("6480000")).containsEntry("거제시", "5370000");
        server.verify();
    }

    @Test
    void ambiguousDistrictNamesAreExcludedWithoutRejectingOtherDistricts() throws Exception {
        var response = mapper.readTree("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":5,
                "items":{"item":[
                  {"orgCd":"old-code","orgdownNm":"같은시"},
                  {"orgCd":"new-code","orgdownNm":"같은시"},
                  {"orgCd":"old-code","orgdownNm":"같은시"},
                  {"orgCd":"5370000","orgdownNm":"거제시"},
                  {"orgCd":"5370000","orgdownNm":"거제시"}
                ]}}}}
                """);
        assertThat(new PublicAnimalParser().districts(response))
                .containsExactlyEntriesOf(java.util.Map.of("거제시", "5370000"));
    }

    @Test
    void shrinkingFeedAllowsAnEmptyTailEvenWhenTotalIsNotZero() throws Exception {
        var response = mapper.readTree("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":2,"totalCount":1,"items":""}}}
                """);
        var page = new PublicAnimalParser().parse(response, 2, 1);
        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.notices()).isEmpty();
    }

    @Test
    void incompleteDirectoryRowsAreSkippedWithoutRejectingValidDistricts() throws Exception {
        var response = mapper.readTree("""
                {"response":{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":5,
                "items":{"item":[
                  {"orgCd":"1234567","orgdownNm":" "},
                  {"orgCd":"","orgdownNm":"빈코드시"},
                  {"orgCd":"7654321"},
                  {"orgdownNm":"누락시"},
                  {"orgCd":"5370000","orgdownNm":"거제시"}
                ]}}}}
                """);
        assertThat(new PublicAnimalParser().districts(response))
                .containsExactlyEntriesOf(java.util.Map.of("거제시", "5370000"));
    }

    private String page(String item) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"pageNo\":1,\"totalCount\":1,\"items\":{\"item\":["
                + item + "]}}}}";
    }
}
