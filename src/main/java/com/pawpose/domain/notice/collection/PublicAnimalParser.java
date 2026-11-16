package com.pawpose.domain.notice.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.pawpose.domain.common.Sex;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.notice.NoticeStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

final class PublicAnimalParser {
    private static final Map<String, String> PROVINCES = Map.ofEntries(
            Map.entry("서울", "6110000"), Map.entry("부산", "6260000"), Map.entry("대구", "6270000"),
            Map.entry("인천", "6280000"), Map.entry("광주", "6290000"), Map.entry("대전", "6300000"),
            Map.entry("울산", "6310000"), Map.entry("세종", "5690000"), Map.entry("경기", "6410000"),
            Map.entry("강원", "6530000"), Map.entry("충청북", "6430000"), Map.entry("충청남", "6440000"),
            Map.entry("전북", "6540000"), Map.entry("전라북", "6540000"), Map.entry("전라남", "6460000"),
            Map.entry("경상북", "6470000"), Map.entry("경상남", "6480000"), Map.entry("제주", "6500000"));

    PublicAnimalApiClient.Page parse(JsonNode root, int expectedPage, int pageSize) {
        JsonNode body = body(root);
        int total = integer(body.get("totalCount"));
        int page = integer(body.get("pageNo"));
        if (total < 0 || total > 1_000_000 || page != expectedPage) throw invalid();
        int expected = Math.min(pageSize, Math.max(0, total - (expectedPage - 1) * pageSize));
        List<JsonNode> items = items(body, expected);
        if (items.size() != expected) throw invalid();
        return new PublicAnimalApiClient.Page(items.stream().map(this::notice).toList(), total);
    }

    Map<String, String> districts(JsonNode root) {
        JsonNode body = body(root);
        int total = integer(body.get("totalCount"));
        if (total < 0 || total > 1000 || integer(body.get("pageNo")) != 1) throw invalid();
        var items = items(body, total);
        if (items.size() != total) throw invalid();
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (JsonNode item : items) {
            String name = text(item, 100, "orgdownNm");
            String code = text(item, 20, "orgCd");
            // Directory enrichment is optional; incomplete provider rows cannot identify a district.
            if (name == null || code == null) continue;
            if (ambiguous.contains(name)) continue;
            String previous = result.putIfAbsent(name, code);
            if (previous != null && !previous.equals(code)) {
                // The provider can return historical/current organizations under the same display name.
                // A name alone cannot identify the correct code; omit its bonus instead of guessing.
                result.remove(name);
                ambiguous.add(name);
            }
        }
        return Map.copyOf(result);
    }

    private JsonNode body(JsonNode root) {
        if (root == null) throw invalid();
        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asText();
        if (List.of("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR").contains(code)) {
            throw new CollectionFailure(CollectionFailure.Reason.QUOTA);
        }
        if (List.of("01", "02", "04", "05").contains(code)) {
            throw new CollectionFailure(CollectionFailure.Reason.TRANSIENT);
        }
        if (!"00".equals(code) && !"0".equals(code)) throw invalid();
        return response.path("body");
    }

    private List<JsonNode> items(JsonNode body, int expectedCount) {
        JsonNode items = body.path("items").path("item");
        List<JsonNode> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(result::add);
        } else if (items.isObject()) {
            result.add(items);
        } else if (expectedCount > 0) {
            throw invalid();
        }
        return result;
    }

    private CollectedNotice notice(JsonNode n) {
        String id = text(n, 30, "desertionNo", "desertion_no");
        if (id == null) throw invalid();
        String kind = text(n, 100, "kindFullNm", "kindCd", "kind_cd");
        String upperKind = text(n, 30, "upKindCd", "up_kind_cd", "upKindNm");
        Species species = species(upperKind, kind);
        String breed = text(n, 50, "kindNm", "kind_nm");
        if (breed == null && kind != null && kind.startsWith("[")) {
            breed = kind.substring(kind.indexOf(']') + 1).trim();
            if (breed.length() > 50) throw invalid();
        }
        String organization = text(n, 100, "orgNm", "org_nm");
        String province = text(n, 20, "uprCd", "upr_cd", "sidoCode");
        if (province == null && organization != null) {
            province = PROVINCES.entrySet().stream().filter(e -> organization.startsWith(e.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        return new CollectedNotice(id, text(n, 50, "noticeNo", "notice_no"),
                date(n, "noticeSdt", "notice_sdt"), date(n, "noticeEdt", "notice_edt"),
                date(n, "happenDt", "happen_dt"), text(n, 200, "happenPlace", "happen_place"),
                species, breed, text(n, 50, "colorCd", "color_cd"),
                sex(text(n, 10, "sexCd", "sex_cd")), text(n, 30, "age"), text(n, 30, "weight"),
                text(n, 500, "specialMark", "special_mark"), text(n, 100, "careNm", "care_nm"),
                text(n, 30, "careTel", "care_tel"), text(n, 200, "careAddr", "care_addr"),
                organization, text(n, 500, "popfile1", "popfile", "popFile", "filename"),
                status(text(n, 50, "processState", "process_state")), province,
                text(n, 20, "orgCd", "org_cd", "sigunguCode"));
    }

    private Species species(String upper, String kind) {
        if ("417000".equals(upper) || "개".equals(upper) || (kind != null && kind.startsWith("[개]"))) return Species.DOG;
        if ("422400".equals(upper) || "고양이".equals(upper) || (kind != null && kind.startsWith("[고양이]"))) return Species.CAT;
        if ("429900".equals(upper) || "기타".equals(upper) || (kind != null && kind.startsWith("[기타]"))) return Species.ETC;
        throw invalid();
    }

    private NoticeStatus status(String value) {
        if (value == null) throw invalid();
        return switch (value) {
            case "보호중", "공고중" -> NoticeStatus.NOTICE;
            case "종료(반환)" -> NoticeStatus.RETURNED;
            case "종료(입양)" -> NoticeStatus.ADOPTED;
            case "종료(안락사)" -> NoticeStatus.EUTHANIZED;
            case "종료(자연사)" -> NoticeStatus.NATURAL_DEATH;
            case "종료(기증)" -> NoticeStatus.DONATED;
            case "종료(방사)" -> NoticeStatus.RELEASED;
            default -> {
                if (value.startsWith("종료")) yield NoticeStatus.ENDED;
                throw invalid();
            }
        };
    }

    private Sex sex(String value) {
        if (value == null) return Sex.UNKNOWN;
        return switch (value) { case "M" -> Sex.MALE; case "F" -> Sex.FEMALE; default -> Sex.UNKNOWN; };
    }

    private LocalDate date(JsonNode n, String... names) {
        String value = text(n, 8, names);
        if (value == null) return null;
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private String text(JsonNode n, int max, String... names) {
        for (String name : names) {
            JsonNode value = n.get(name);
            if (value == null || value.isNull()) continue;
            if (!value.isValueNode()) throw invalid();
            String result = value.asText().trim();
            if (result.isEmpty()) continue;
            if (result.length() > max) throw invalid();
            return result;
        }
        return null;
    }

    private int integer(JsonNode node) {
        if (node == null || !node.isValueNode()) throw invalid();
        try { return Integer.parseInt(node.asText()); } catch (NumberFormatException e) { throw invalid(); }
    }

    private CollectionFailure invalid() {
        return new CollectionFailure(CollectionFailure.Reason.INVALID_RESPONSE);
    }
}
