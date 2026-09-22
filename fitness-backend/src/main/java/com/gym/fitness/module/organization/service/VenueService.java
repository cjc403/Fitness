package com.gym.fitness.module.organization.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.organization.dto.VenueRequest;
import com.gym.fitness.module.organization.dto.VenueResponse;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

    @Transactional(readOnly = true)
    public List<VenueResponse> list() {
        if (isSuperAdmin()) {
            return venueRepository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
        }
        Long venueId = requireCurrentVenueId();
        return List.of(toResponse(findAccessibleVenue(venueId)));
    }

    @Transactional(readOnly = true)
    public VenueResponse get(Long id) {
        return toResponse(findAccessibleVenue(id));
    }

    @Transactional
    public VenueResponse create(VenueRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (venueRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.VENUE_ALREADY_EXISTS, "场馆编码已存在");
        }
        validateParent(request.getParentVenueId(), null);

        VenueEntity venue = new VenueEntity();
        applyRequest(venue, request, code);
        return toResponse(venueRepository.save(venue));
    }

    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        VenueEntity venue = findAccessibleVenue(id);
        String code = request.getCode().trim().toUpperCase();
        if (venueRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException(ErrorCode.VENUE_ALREADY_EXISTS, "场馆编码已存在");
        }
        validateParent(request.getParentVenueId(), id);
        applyRequest(venue, request, code);
        return toResponse(venueRepository.save(venue));
    }

    private void validateParent(Long parentVenueId, Long currentVenueId) {
        if (parentVenueId == null) {
            return;
        }
        if (parentVenueId.equals(currentVenueId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "场馆不能把自己设为上级场馆");
        }
        if (!venueRepository.existsById(parentVenueId)) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND, "上级场馆不存在");
        }
    }

    private void applyRequest(VenueEntity venue, VenueRequest request, String normalizedCode) {
        venue.setName(request.getName().trim());
        venue.setCode(normalizedCode);
        venue.setParentVenueId(request.getParentVenueId());
        venue.setAddress(request.getAddress());
        venue.setContactPhone(request.getContactPhone());
        venue.setContactEmail(request.getContactEmail());
        venue.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().toUpperCase());
    }

    private VenueEntity findAccessibleVenue(Long id) {
        VenueEntity venue = venueRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
        if (!isSuperAdmin() && !id.equals(requireCurrentVenueId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS);
        }
        return venue;
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(SecurityUtil.getCurrentUserRole());
    }

    private Long requireCurrentVenueId() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return venueId;
    }

    private VenueResponse toResponse(VenueEntity venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .code(venue.getCode())
                .parentVenueId(venue.getParentVenueId())
                .address(venue.getAddress())
                .contactPhone(venue.getContactPhone())
                .contactEmail(venue.getContactEmail())
                .status(venue.getStatus())
                .createdAt(venue.getCreatedAt())
                .updatedAt(venue.getUpdatedAt())
                .build();
    }
}
