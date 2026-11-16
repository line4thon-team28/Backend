package com.pawpose.domain.notice.collection;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class PublicAnimalApiClient {
    static final String ENDPOINT = "https://apis.data.go.kr/1543061/abandonmentPublicService_v2/abandonmentPublic_v2";
    private final CollectionProperties properties;
    private final RestClient client;
    private final PublicAnimalParser parser = new PublicAnimalParser();

    @Autowired
    public PublicAnimalApiClient(CollectionProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(requestFactory()).build());
    }

    PublicAnimalApiClient(CollectionProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    public Page fetch(LocalDate from, LocalDate through, boolean initial, int page) {
        // Expand after encode so '+' and '/' in a decoding key are encoded exactly once.
        URI uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("serviceKey", "{key}").queryParam("_type", "json")
                .queryParam("pageNo", page).queryParam("numOfRows", properties.getPageSize())
                .queryParam(initial ? "bgnde" : "bgupd", from.format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam(initial ? "endde" : "enupd", through.format(DateTimeFormatter.BASIC_ISO_DATE))
                // No state filter: returned/adopted notices must also be updated.
                .encode().buildAndExpand(properties.getServiceKey()).toUri();
        return request(uri, response -> parser.parse(response, page, properties.getPageSize()));
    }

    public Map<String, String> fetchDistricts(String provinceCode) {
        URI uri = UriComponentsBuilder.fromUriString(ENDPOINT.replace("/abandonmentPublic_v2", "/sigungu_v2"))
                .queryParam("serviceKey", "{key}").queryParam("_type", "json")
                .queryParam("upr_cd", provinceCode).queryParam("pageNo", 1).queryParam("numOfRows", 1000)
                .encode().buildAndExpand(properties.getServiceKey()).toUri();
        return request(uri, parser::districts);
    }

    private <T> T request(URI uri, Function<JsonNode, T> parse) {
        try {
            JsonNode response = client.get().uri(uri).retrieve().body(JsonNode.class);
            return parse.apply(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) throw new CollectionFailure(CollectionFailure.Reason.QUOTA);
            if (e.getStatusCode().is5xxServerError()) throw new CollectionFailure(CollectionFailure.Reason.TRANSIENT);
            throw new CollectionFailure(CollectionFailure.Reason.REJECTED);
        } catch (ResourceAccessException e) {
            throw new CollectionFailure(CollectionFailure.Reason.TRANSIENT);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectionFailure(CollectionFailure.Reason.INVALID_RESPONSE);
        }
    }

    public record Page(List<CollectedNotice> notices, int totalCount) {
        public Page {
            notices = List.copyOf(notices);
        }
    }
}
