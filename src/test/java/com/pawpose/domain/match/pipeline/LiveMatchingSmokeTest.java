package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.notice.collection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in; requires the public-data key and an already-running local embedding process. No keys/URLs logged. */
@EnabledIfEnvironmentVariable(named = "PAWPOSE_LIVE_TEST", matches = "true")
class LiveMatchingSmokeTest {
    @Test void officialApiPhotoThroughLocalCpuModel() {
        CollectionProperties collection = new CollectionProperties();
        collection.setServiceKey(System.getenv("PUBLIC_DATA_SERVICE_KEY")); collection.setPageSize(1);
        PublicAnimalApiClient api = new PublicAnimalApiClient(collection, RestClient.builder());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var page = api.fetch(today.minusDays(29), today, true, 1);
        assertThat(page.notices()).hasSize(1);
        var notice = page.notices().get(0);
        assertThat(api.fetchDistricts(notice.sidoCode())).isNotEmpty();
        MatchingProperties matching = new MatchingProperties();
        matching.setEmbeddingKey(System.getenv("EMBEDDING_API_KEY"));
        matching.setEmbeddingUrl(System.getenv().getOrDefault("EMBEDDING_URL", "http://127.0.0.1:8091/embed"));
        LocalEmbeddingClient client = new LocalEmbeddingClient(matching, new ObjectMapper());
        double[] vector = client.embed(notice.imageUrl());
        assertThat(vector).hasSize(768);
        assertThat(new SimilarityScorer().cosine(vector, vector)).isCloseTo(1, within(1e-8));
    }
}
