package com.pawpose.domain.match.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component @RequiredArgsConstructor
public class MatchingJson {
    private final ObjectMapper mapper;
    String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("INVALID_MATCHING_DATA"); } }
    List<String> images(String json) { try { return Arrays.asList(mapper.readValue(json, String[].class)); } catch (Exception e) { throw new IllegalArgumentException("INVALID_MATCHING_DATA"); } }
    double[][] vectors(String json) { try { return mapper.readValue(json, double[][].class); } catch (Exception e) { throw new IllegalArgumentException("INVALID_MATCHING_DATA"); } }
}
