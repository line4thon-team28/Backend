package com.pawpose.domain.match.pipeline;

import java.util.*;

/** Province codes from the public-data API; old Gangwon/Jeonbuk codes remain aliases. */
public final class RegionPolicy {
    private RegionPolicy() {}
    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("서울", "6110000"), Map.entry("부산", "6260000"), Map.entry("대구", "6270000"),
        Map.entry("인천", "6280000"), Map.entry("광주", "6290000"), Map.entry("대전", "6300000"),
        Map.entry("울산", "6310000"), Map.entry("세종", "5690000"), Map.entry("경기", "6410000"),
        Map.entry("강원", "6530000"), Map.entry("충청북", "6430000"), Map.entry("충북", "6430000"),
        Map.entry("충청남", "6440000"), Map.entry("충남", "6440000"), Map.entry("전북", "6540000"),
        Map.entry("전라북", "6540000"), Map.entry("전라남", "6460000"), Map.entry("전남", "6460000"),
        Map.entry("경상북", "6470000"), Map.entry("경북", "6470000"), Map.entry("경상남", "6480000"),
        Map.entry("경남", "6480000"), Map.entry("제주", "6500000"));
    private static final List<String> EDGES = List.of(
        "6110000:6410000", "6110000:6280000", "6280000:6410000", "6410000:6530000", "6410000:6430000", "6410000:6440000",
        "6530000:6430000", "6530000:6470000", "6430000:6440000", "6430000:5690000", "6430000:6300000", "6430000:6540000", "6430000:6470000",
        "6440000:5690000", "6440000:6300000", "6440000:6540000", "6300000:5690000", "6300000:6540000",
        "6540000:6460000", "6540000:6470000", "6540000:6480000", "6460000:6290000", "6460000:6480000",
        "6470000:6270000", "6470000:6480000", "6470000:6310000", "6480000:6270000", "6480000:6310000", "6480000:6260000", "6310000:6260000");
    public static String canonical(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.equals("6420000")) return "6530000";
        if (v.equals("6450000")) return "6540000";
        if (NAMES.containsValue(v)) return v;
        return NAMES.entrySet().stream().filter(e -> v.startsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
    }
    public static boolean nearby(String a, String b) {
        a = canonical(a); b = canonical(b);
        return a != null && b != null && (a.equals(b) || EDGES.contains(a + ":" + b) || EDGES.contains(b + ":" + a));
    }
}
