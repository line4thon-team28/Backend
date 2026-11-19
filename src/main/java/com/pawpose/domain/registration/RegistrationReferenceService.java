package com.pawpose.domain.registration;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.match.pipeline.RegionPolicy;
import com.pawpose.domain.notice.collection.*;
import com.pawpose.global.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service @RequiredArgsConstructor
public class RegistrationReferenceService {
    private final PublicAnimalApiClient publicApi;
    private final CollectionProperties collection;
    private final Map<String,DistrictCache> cache=new ConcurrentHashMap<>();
    private static final Map<String,String> PROVINCES=Map.ofEntries(
        Map.entry("6110000","서울특별시"),Map.entry("6260000","부산광역시"),
        Map.entry("6270000","대구광역시"),Map.entry("6280000","인천광역시"),
        Map.entry("6290000","광주광역시"),Map.entry("6300000","대전광역시"),
        Map.entry("6310000","울산광역시"),Map.entry("5690000","세종특별자치시"),
        Map.entry("6410000","경기도"),Map.entry("6530000","강원특별자치도"),
        Map.entry("6430000","충청북도"),Map.entry("6440000","충청남도"),
        Map.entry("6540000","전북특별자치도"),Map.entry("6460000","전라남도"),
        Map.entry("6470000","경상북도"),Map.entry("6480000","경상남도"),
        Map.entry("6500000","제주특별자치도"));

    public Region resolve(String sido,String district) {
        String code=RegionPolicy.canonical(sido);
        if(code==null||!PROVINCES.containsKey(code)||district==null||district.isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        String name=district.trim();
        Map<String,String> districts=districts(code);
        String districtCode=districts.get(name);
        if(districtCode==null&&!districts.isEmpty()) {
            var found=districts.entrySet().stream().filter(e->e.getValue().equals(district.trim()))
                .findFirst().orElseThrow(()->new BusinessException(ErrorCode.INVALID_INPUT));
            name=found.getKey(); districtCode=found.getValue();
        } else if(districts.isEmpty()&&name.matches("\\d+"))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        return new Region(code,PROVINCES.get(code),name,districtCode);
    }
    public List<Province> regions(String sido) {
        String selected=sido==null?null:RegionPolicy.canonical(sido);
        if(sido!=null&&selected==null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        return PROVINCES.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .filter(e->selected==null||selected.equals(e.getKey()))
            .map(e->new Province(e.getKey(),e.getValue(),selected==null?List.of():
                districts(e.getKey()).entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(d->new District(d.getValue(),d.getKey())).toList(),
                !configured()?"MANUAL":selected==null?"SELECT_PROVINCE":"OFFICIAL")).toList();
    }
    public List<Breed> breeds(Species species) {
        List<String> names=switch(species) {
            case DOG -> List.of("믹스견","말티즈","푸들","포메라니안","시츄","진도견","리트리버","기타");
            case CAT -> List.of("한국 고양이","코리안 숏헤어","러시안 블루","페르시안","샴","기타");
            case ETC -> List.of("토끼","햄스터","고슴도치","기타");
        };
        return names.stream().map(n->new Breed(n,n)).toList();
    }
    private boolean configured() { return collection.getServiceKey()!=null&&!collection.getServiceKey().isBlank(); }
    private Map<String,String> districts(String code) {
        if(!configured()) return Map.of();
        DistrictCache saved=cache.get(code);
        if(saved!=null&&saved.expiresAt().isAfter(Instant.now())) return saved.values();
        try {
            Map<String,String> values=Map.copyOf(publicApi.fetchDistricts(code));
            cache.put(code,new DistrictCache(values,Instant.now().plus(Duration.ofHours(24))));
            return values;
        } catch(CollectionFailure e) { throw new BusinessException(ErrorCode.REFERENCE_DATA_UNAVAILABLE); }
    }
    private record DistrictCache(Map<String,String> values,Instant expiresAt) {}
    public record Region(String sidoCode,String sidoName,String sigungu,String sigunguCode) {}
    public record District(String code,String name) {}
    public record Province(String sidoCode,String name,List<District> sigungu,String districtMode) {}
    public record Breed(String code,String name) {}
}
