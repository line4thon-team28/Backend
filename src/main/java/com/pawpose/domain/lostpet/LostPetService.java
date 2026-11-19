package com.pawpose.domain.lostpet;

import com.pawpose.domain.lostpet.dto.*;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.match.pipeline.*;
import com.pawpose.domain.registration.RegistrationReferenceService;
import com.pawpose.domain.user.UserRepository;
import com.pawpose.global.error.*;
import com.pawpose.global.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LostPetService {
    private final LostPetRepository pets;
    private final UserRepository users;
    private final MatchRepository matches;
    private final RegistrationReferenceService references;
    private final PhotoUploadService uploads;
    private final MatchingInputService matching;
    private final TransactionTemplate transactions;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Created create(Long userId, LostPetCreateRequest request, List<MultipartFile> photos) {
        if (request.getLostDate() == null || request.getLostDate().isAfter(LocalDate.now(SEOUL)))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var owner = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var region = references.resolve(request.getSido(), request.getSigungu());
        List<StoredPhoto> stored = uploads.upload(photos, 5);
        try {
            return transactions.execute(tx -> {
                LostPet pet = pets.save(LostPet.register(owner, request, region,
                        stored.stream().map(StoredPhoto::url).toList()));
                matching.registerLostPet(pet.getId(), new MatchingProfile(pet.getPhotos(),
                        pet.getLostDate(), pet.getSido(), pet.getSigunguCode(), pet.getBreed(),
                        pet.getColor(), String.join(" ", region.sidoName(), region.sigungu(),
                                request.getPlace() == null ? "" : request.getPlace()).trim()));
                return new Created(pet.getId(), ProcessingStatus.PENDING);
            });
        } catch (RuntimeException e) {
            uploads.cleanup(stored);
            throw e;
        }
    }

    @Transactional(readOnly=true)
    public List<LostPetResponse> mine(Long userId) {
        return pets.findByUserIdOrderByIdDesc(userId).stream()
                .map(p -> LostPetResponse.from(p, matchCount(p.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public LostPetResponse detail(Long userId, Long id) {
        return LostPetResponse.from(owned(userId, id), matchCount(id));
    }

    @Transactional
    public LostPetResponse update(Long userId, Long id, LostPetUpdateRequest request) {
        if (request.status() == null && request.features() == null && request.phone() == null)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (request.status() != null && request.status() != LostPetStatus.FOUND)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        LostPet pet = pets.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.LOST_PET_NOT_FOUND));
        if (!pet.isOwnedBy(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (request.status() == LostPetStatus.FOUND) matching.markFound(userId, id);
        pet.updateContact(request.features(), request.phone());
        return LostPetResponse.from(pet, matchCount(id));
    }

    private LostPet owned(Long userId, Long id) {
        LostPet pet = pets.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.LOST_PET_NOT_FOUND));
        if (!pet.isOwnedBy(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return pet;
    }

    private long matchCount(Long id) {
        return Math.min(20, matches.countVisible(id, LocalDate.now(SEOUL)));
    }
    public record Created(Long lostPetId, ProcessingStatus processingStatus) {}
}
