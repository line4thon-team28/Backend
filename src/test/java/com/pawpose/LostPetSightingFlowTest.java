package com.pawpose;

import com.fasterxml.jackson.databind.*;
import com.pawpose.domain.common.*;
import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.match.*;
import com.pawpose.domain.match.pipeline.*;
import com.pawpose.domain.notice.*;
import com.pawpose.domain.sighting.*;
import com.pawpose.domain.user.*;
import com.pawpose.domain.auth.kakao.KakaoOAuthClient;
import com.pawpose.domain.notice.collection.PublicAnimalApiClient;
import com.pawpose.global.error.*;
import com.pawpose.global.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"PHOTO_STORAGE_TYPE=local","pawpose.collection.service-key=",
    "pawpose.collection.enabled=false","pawpose.matching.enabled=false"}) @AutoConfigureMockMvc
class LostPetSightingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LostPetRepository pets;
    @Autowired SightingRepository sightings;
    @Autowired MatchingSourceRepository sources;
    @Autowired MatchRepository matches;
    @Autowired NoticeRepository notices;
    @Autowired UserRepository users;
    @Autowired MatchingWorker worker;
    @Autowired ComparisonDispatchService comparisons;
    @MockitoBean PhotoStorage storage;
    @MockitoBean PublicAnimalApiClient publicApi;
    @MockitoBean KakaoOAuthClient kakao;
    @MockitoBean EmbeddingClient embedding;
    @MockitoSpyBean MatchingInputService matching;

    @BeforeEach void storage() {
        when(storage.put(anyString(),any(byte[].class),anyString()))
                .thenAnswer(a->new StoredPhoto(a.getArgument(0),"https://storage.example/"+a.getArgument(0)));
    }

    @Test void swaggerMultipartFieldsMatchSpringFormBinding() throws Exception {
        JsonNode api=mapper.readTree(mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(String path:List.of("/api/v1/lost-pets","/api/v1/sightings")) {
            JsonNode schema=api.path("paths").path(path).path("post").path("requestBody")
                .path("content").path("multipart/form-data").path("schema");
            if(schema.has("$ref")) schema=api.at(schema.path("$ref").asText().substring(1));
            JsonNode properties=schema.path("properties");
            assertThat(properties.has("form")).as(path+" must have flat form fields").isFalse();
            for(String field:List.of("species","sido","sigungu","photos",
                    path.endsWith("lost-pets")?"lostDate":"seenAt"))
                assertThat(properties.has(field)).as(path+" field "+field).isTrue();
            assertThat(properties.path("photos").path("type").asText()).isEqualTo("array");
            assertThat(properties.path("photos").path("items").path("format").asText()).isEqualTo("binary");
        }
    }

    @Test void registrationPersistsFieldsPhotosAndMatchingInOneTransaction() throws Exception {
        String token=signup();
        long id=create(token);
        LostPet pet=pets.findById(id).orElseThrow();
        assertThat(pet.getLostDate()).isEqualTo(today());
        assertThat(pet.getSido()).isEqualTo("6110000");
        assertThat(pet.getSigungu()).isEqualTo("강남구");
        assertThat(pet.getSigunguCode()).isNull();
        MatchingSource source=sources.findByKindAndSourceId(SourceKind.LOST_PET,id).orElseThrow();
        assertThat(source.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(source.getImagesJson()).contains("https://storage.example/").doesNotContain("../");
        mvc.perform(get("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("보리"))
            .andExpect(jsonPath("$.data.photos.length()").value(1));
        verify(storage).put(matches("[a-f0-9-]+\\.png"),any(byte[].class),eq("image/png"));
    }

    @Test void mineAndDetailAreRestrictedToOwner() throws Exception {
        String token=signup(), other=signup();
        long id=create(token);
        mvc.perform(get("/api/v1/lost-pets/me").header("Authorization","Bearer "+other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+other))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+other)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FOUND\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(lostForm().file(photo())).andExpect(status().isUnauthorized());
    }

    @Test void foundCancelsMatchingAndCannotReopen() throws Exception {
        String token=signup(); long id=create(token);
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FOUND\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FOUND"));
        assertThat(sources.findByKindAndSourceId(SourceKind.LOST_PET,id).orElseThrow().getStatus())
            .isEqualTo(ProcessingStatus.CANCELLED);
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FOUND\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SEARCHING\"}")).andExpect(status().isBadRequest());
    }

    @Test void partialUpdateKeepsOmittedFieldsAndCanClearText() throws Exception {
        String token=signup(); long id=create(token);
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"features\":\"흰 발\",\"phone\":\"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("보리"))
            .andExpect(jsonPath("$.data.features").value("흰 발")).andExpect(jsonPath("$.data.phone").value(""));
        mvc.perform(patch("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test void invalidFilesAndDatesAreRejectedBeforeStorage() throws Exception {
        String token=signup();
        mvc.perform(lostForm().file(new MockMultipartFile("photos","cat.png","image/png","not an image".getBytes()))
            .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        var tooMany=lostForm();
        for(int i=0;i<6;i++) tooMany.file(photo());
        mvc.perform(tooMany.header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(lostForm().file(photo()).with(r->{ r.setParameter("lostDate",today().plusDays(1).toString()); return r; })
            .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(lostForm().file(photo()).with(r->{ r.setParameter("sido","unknown"); return r; })
            .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }

    @Test void missingPhotosAndInvalidEnumsReturn400() throws Exception {
        String token=signup();
        mvc.perform(lostForm().header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(lostForm().file(photo()).with(r->{ r.setParameter("species","BIRD"); return r; })
            .header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }

    @Test void failedMatchingRollsBackRegistrationAndCleansPhoto() throws Exception {
        String token=signup(); long before=pets.count();
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT)).when(matching)
            .registerLostPet(anyLong(),any(MatchingProfile.class));
        mvc.perform(lostForm().file(photo()).header("Authorization","Bearer "+token))
            .andExpect(status().isBadRequest());
        assertThat(pets.count()).isEqualTo(before);
        verify(storage).delete(anyString());
    }

    @Test void partialUploadFailureCleansEarlierPhotoWithoutCreatingRegistration() throws Exception {
        String token=signup(); long before=pets.count();
        AtomicInteger calls=new AtomicInteger();
        when(storage.put(anyString(),any(byte[].class),anyString())).thenAnswer(a->{
            if(calls.incrementAndGet()==2) throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED);
            return new StoredPhoto(a.getArgument(0),"https://storage.example/"+a.getArgument(0));
        });
        mvc.perform(lostForm().file(photo()).file(photo()).header("Authorization","Bearer "+token))
            .andExpect(status().isServiceUnavailable());
        assertThat(pets.count()).isEqualTo(before);
        verify(storage,times(2)).delete(anyString());
    }

    @Test void anonymousSightingCreatesMatchingInputAndPublicProgressOnly() throws Exception {
        long before=sightings.count();
        var response=mvc.perform(sightingForm().file(photo())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.processingStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.matchedCount").value(0)).andReturn();
        long id=mapper.readTree(response.getResponse().getContentAsString()).at("/data/sightingId").asLong();
        assertThat(sightings.count()).isEqualTo(before+1);
        assertThat(sources.findByKindAndSourceId(SourceKind.SIGHTING,id)).isPresent();
        mvc.perform(get("/api/v1/sightings/"+id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.retryAfterSeconds").value(10))
            .andExpect(jsonPath("$.data.phone").doesNotExist())
            .andExpect(jsonPath("$.data.userId").doesNotExist());
        mvc.perform(get("/api/v1/sightings/999999999")).andExpect(status().isNotFound());
    }

    @Test void sightingRequiresSpeciesAndRejectsTooManyPhotos() throws Exception {
        mvc.perform(multipart("/api/v1/sightings").file(photo()).param("sido","서울")
            .param("sigungu","강남구").param("seenAt",LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1).toString()))
            .andExpect(status().isBadRequest());
        var tooMany=sightingForm();
        for(int i=0;i<4;i++) tooMany.file(photo());
        mvc.perform(tooMany).andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }

    @Test void sightingProgressWaitsForComparisonThenCountsOnlySearchingPets() throws Exception {
        String token=signup();
        when(embedding.embed(anyString())).thenReturn(new double[]{1,0});
        var lostResult=mvc.perform(lostForm().file(photo()).header("Authorization","Bearer "+token)
            .with(r->{
                r.setParameter("species","DOG"); r.setParameter("sido","6500000");
                r.setParameter("sigungu","제주시"); r.setParameter("lostDate",today().minusDays(2).toString());
                return r;
            })).andExpect(status().isCreated()).andReturn();
        long petId=mapper.readTree(lostResult.getResponse().getContentAsString()).at("/data/lostPetId").asLong();
        long lostSource=sources.findSourceId(SourceKind.LOST_PET,petId).orElseThrow();
        worker.process(lostSource);
        String memo="빨간 목줄을 한 강아지를 공원 입구에서 봤습니다";
        var sightResult=mvc.perform(sightingForm().file(photo()).param("memo",memo).with(r->{
                r.setParameter("species","DOG"); r.setParameter("sido","6500000"); r.setParameter("sigungu","제주시");
                return r;
            })).andExpect(status().isCreated()).andReturn();
        long sightId=mapper.readTree(sightResult.getResponse().getContentAsString()).at("/data/sightingId").asLong();
        worker.process(sources.findSourceId(SourceKind.SIGHTING,sightId).orElseThrow());
        mvc.perform(get("/api/v1/sightings/"+sightId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processingStatus").value("PROCESSING"));
        comparisons.dispatchPending();
        worker.process(lostSource);
        mvc.perform(get("/api/v1/sightings/"+sightId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processingStatus").value("READY"))
            .andExpect(jsonPath("$.data.matchedCount").value(1));
        for(String sort:List.of("similarity","deadline")) {
            mvc.perform(get("/api/v1/lost-pets/"+petId+"/matches").param("sort",sort)
                    .header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.sightingId == "+sightId+")].memo")
                    .value(org.hamcrest.Matchers.contains(memo)));
        }
        mvc.perform(patch("/api/v1/lost-pets/"+petId).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FOUND\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sightings/"+sightId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.matchedCount").value(0));
    }

    @Test void oversizedPhotoReturnsSpecific400WithoutCallingStorage() throws Exception {
        String token=signup();
        var photo=new MockMultipartFile("photos","big.png","image/png",new byte[10*1024*1024+1]);
        mvc.perform(lostForm().file(photo).header("Authorization","Bearer "+token))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        verifyNoInteractions(storage);
    }

    @Test void countUsesSameVisibilityRulesAsCandidateList() throws Exception {
        String token=signup(); long id=create(token);
        LostPet pet=pets.findById(id).orElseThrow();
        Notice open=notices.save(Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0,20))
            .species(Species.CAT).status(NoticeStatus.NOTICE).noticeEndDate(today().plusDays(2)).build());
        Notice closed=notices.save(Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0,20))
            .species(Species.CAT).status(NoticeStatus.RETURNED).noticeEndDate(today().plusDays(2)).build());
        matches.save(Match.ofNotice(pet,open,90));
        matches.save(Match.ofNotice(pet,closed,95));
        mvc.perform(get("/api/v1/lost-pets/"+id).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchCount").value(1));
    }

    private long create(String token) throws Exception {
        var result=mvc.perform(lostForm().file(photo()).header("Authorization","Bearer "+token))
            .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/lostPetId").asLong();
    }
    private String signup() throws Exception {
        var result=mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\""+UUID.randomUUID()+"@test.com\",\"password\":\"password1234\",\"nickname\":\"테스트\"}"))
            .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }
    private MockMultipartHttpServletRequestBuilder lostForm() {
        var form=multipart("/api/v1/lost-pets");
        form.param("species","CAT").param("name","보리")
            .param("lostDate",today().toString()).param("sido","서울").param("sigungu","강남구")
            .param("features","빨간 목줄").param("phone","010-1234-5678");
        return form;
    }
    private MockMultipartHttpServletRequestBuilder sightingForm() {
        var form=multipart("/api/v1/sightings");
        form.param("species","CAT").param("sido","6110000")
            .param("sigungu","강남구").param("seenAt",LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1).toString());
        return form;
    }
    private MockMultipartFile photo() throws Exception {
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("photos","../cat.png","image/png",bytes.toByteArray());
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
}
